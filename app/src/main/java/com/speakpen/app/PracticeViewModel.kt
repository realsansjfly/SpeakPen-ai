package com.speakpen.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speakpen.app.data.*
import com.speakpen.app.network.*
import com.speakpen.app.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class PracticeState(
    val page: Int = 0, val settings: AppSettings = AppSettings(),
    val sessions: List<ChatSession> = emptyList(), val currentSessionId: String = "", val library: List<ReviewItem> = emptyList(),
    val draft: String = "", val inputKind: InputKind = InputKind.ENGLISH,
    val serviceStatus: String = "正在检查服务连接…", val serviceOnline: Boolean = false,
    val busy: String? = null, val error: String? = null, val notice: String? = null,
    val speechPhase: SpeechPhase = SpeechPhase.IDLE, val recordingLevel: Float = 0f, val playingId: String? = null, val preparingVoiceId: String? = null,
    val streamingText: String = "", val candidates: List<ReviewCandidate>? = null,
    val accumulationEditor: ReviewItem? = null,
    val historyVisible: Boolean = false, val targetPickerVisible: Boolean = false,
) {
    val recording: Boolean get() = speechPhase == SpeechPhase.LISTENING
    val voiceActive: Boolean get() = speechPhase != SpeechPhase.IDLE
    val session: ChatSession get() = sessions.firstOrNull { it.id == currentSessionId } ?: ChatSession(id = currentSessionId)
    val pendingVoice: ChatMessage? get() = session.messages.lastOrNull { it.isConversation }?.takeIf { it.role == "user" && it.audioFileName.isNotBlank() && it.text.isBlank() }
    val needsReply: Boolean get() = session.messages.lastOrNull { it.isConversation }?.let { it.role == "user" && it.text.isNotBlank() } == true
    val serviceConfigured: Boolean get() = ManagedService.configured(settings)
}

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val recordings = RecordingStore(application)
    private val ai = AiClient()
    private val stored = store.readData()
    private val saved = ConversationSessions.normalize(stored.copy(sessions = stored.sessions.map { session ->
        session.copy(messages = session.messages.map(VoiceMessages::restore))
    }))
    private val initialSession = saved.sessions.firstOrNull { it.id == saved.currentSessionId } ?: ChatSession()
    private val preferences = store.readSettings()
    private val mutable = MutableStateFlow(PracticeState(
        settings = preferences, sessions = saved.sessions.ifEmpty { listOf(initialSession) },
        currentSessionId = initialSession.id, library = saved.library, error = store.loadWarning,
    ))
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var operationGeneration = 0
    private var persistJob: Job? = null
    private var foreground = true
    private var speechJob: Job? = null
    private var speechGeneration = 0
    private var statusJob: Job? = null
    private val speech = SpeechController(application,
        onFile = { file, kind, duration -> acceptRecording(file, kind, duration) },
        onPhase = { phase -> mutable.update { it.copy(speechPhase = phase) } },
        onPlayback = { id -> mutable.update { it.copy(playingId = id) } },
        onLevel = { level -> mutable.update { it.copy(recordingLevel = level) } },
        onNotice = { notice -> mutable.update { it.copy(notice = notice) } },
        onError = { error -> mutable.update { it.copy(error = error) } },
    )
    init {
        application.cacheDir.listFiles()?.filter { it.name.startsWith("utterance_") }?.forEach { it.delete() }
        if (saved != stored) persist()
        runCatching { store.saveSettings(preferences) }
        checkService()
    }
    fun page(page: Int) { if (!state.value.voiceActive) mutable.update { it.copy(page = page, error = null, notice = null) } }
    fun draft(text: String) { mutable.update { it.copy(draft = text) } }
    fun inputKind(kind: InputKind) { if (!state.value.voiceActive && state.value.busy == null) mutable.update { it.copy(inputKind = kind) } }
    fun clearFeedback() { mutable.update { it.copy(error = null, notice = null) } }
    fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun preferences(value: AppSettings) {
        try { store.saveSettings(value); mutable.update { it.copy(settings = value) } }
        catch (_: Exception) { error("偏好保存失败，请重试。") }
    }
    fun connect(raw: String) {
        if (state.value.busy != null || state.value.voiceActive) { error("请先结束当前操作，再打开配对链接。"); return }
        try {
            val connection = ServicePairing.parse(raw)
            preferences(state.value.settings.copy(serviceUrl = connection.baseUrl, serviceAccessToken = connection.accessToken, demoMode = false))
            mutable.update { it.copy(page = 0, notice = "已连接电脑的口语服务。") }
            checkService()
        } catch (_: Exception) { error("服务连接链接无效，请复制电脑新生成的完整服务入口。"); }
    }
    fun checkService() {
        statusJob?.cancel()
        if (!state.value.serviceConfigured) { mutable.update { it.copy(serviceStatus = "尚未连接服务", serviceOnline = false) }; return }
        val settings = state.value.settings
        mutable.update { it.copy(serviceStatus = "正在检查服务连接…", serviceOnline = false) }
        statusJob = viewModelScope.launch {
            try {
                val result = ai.status(ManagedService.config(settings))
                if (state.value.settings.serviceUrl == settings.serviceUrl) mutable.update { it.copy(serviceStatus = result, serviceOnline = true) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutable.update { it.copy(serviceStatus = "连接已失效或网络不可达。请重新启动电脑服务，扫描新入口；也可点“重新连接”粘贴入口。", serviceOnline = false) }
            }
        }
    }
    private fun canUseService(): Boolean {
        if (state.value.settings.demoMode || ManagedService.configured(state.value.settings)) return true
        error("AI 服务尚未开通，可以先到偏好中开启示例体验。")
        return false
    }
    fun showHistory(visible: Boolean) { mutable.update { it.copy(historyVisible = visible) } }
    fun showTargets(visible: Boolean) { mutable.update { it.copy(targetPickerVisible = visible) } }
    fun selectSession(id: String) {
        if (state.value.busy != null || state.value.voiceActive) return
        stopSpeaking()
        mutable.update { it.copy(currentSessionId = id, draft = "", candidates = null, historyVisible = false,
            inputKind = InputKind.ENGLISH, error = null, streamingText = "") }
        persist()
    }
    fun newSession(mode: ConversationMode, targets: List<String> = emptyList()) {
        if (state.value.busy != null || state.value.voiceActive) return
        if (mode == ConversationMode.REVIEW && targets.isEmpty()) { showTargets(true); return }
        stopSpeaking()
        val current = state.value
        val started = ConversationSessions.start(SavedData(current.sessions, current.library, current.currentSessionId), mode, targets)
        mutable.update { it.copy(sessions = started.sessions, currentSessionId = started.currentSessionId, page = 0,
            draft = "", candidates = null, targetPickerVisible = false, error = null, notice = null, inputKind = InputKind.ENGLISH, streamingText = "") }
        persist()
    }
    fun send() {
        val current = state.value
        if (current.draft.isBlank() || current.voiceActive || current.busy != null) return
        if (current.needsReply || current.pendingVoice != null) { error("请先重试上一条消息或重说。草稿已保留。"); return }
        if (!canUseService()) return
        appendUser(ChatMessage(role = "user", text = current.draft.trim(), inputKind = current.inputKind))
        mutable.update { it.copy(draft = "", notice = null) }
        reply()
    }
    private fun appendUser(message: ChatMessage) {
        val session = state.value.session
        updateSession(session.copy(messages = session.messages + message,
            title = if (session.messages.isEmpty()) message.text.take(26).ifBlank { "语音练习" } else session.title,
            updatedAt = System.currentTimeMillis()))
    }
    fun startConversation(topic: String = "") {
        if (state.value.session.messages.isNotEmpty()) return
        mutable.update { it.copy(draft = when {
            topic.isNotBlank() -> "Let's talk about $topic."
            it.session.mode == ConversationMode.REVIEW -> "Let's practice my review targets."
            else -> "Hi! Let's have a conversation."
        }, inputKind = InputKind.ENGLISH) }
        send()
    }
    fun reply() {
        if (!canUseService() || !state.value.needsReply) return
        runOperation("AI 正在回复…") { generateReply() }
    }
    private suspend fun generateReply() {
        val current = state.value; val session = current.session
        val requestGeneration = operationGeneration
        val targets = current.library.filter { it.id in session.targetIds }
        mutable.update { it.copy(busy = "AI 正在回复…", streamingText = "") }
        val text = if (current.settings.demoMode) {
            val sample = demoReply(session, targets)
            for (end in (1..sample.length).filter { it % 9 == 0 || it == sample.length }) { delay(35); mutable.update { it.copy(streamingText = sample.take(end)) } }
            sample
        } else ai.streamChat(ManagedService.config(state.value.settings), listOf("system" to CoachPrompts.system(session.mode, targets)) + CoachPrompts.history(session)) { text ->
            if (requestGeneration == operationGeneration) mutable.update { it.copy(streamingText = text) }
        }
        val message = ChatMessage(role = "assistant", text = text)
        val latest = state.value.sessions.first { it.id == session.id }
        updateSession(latest.copy(messages = latest.messages + message, updatedAt = System.currentTimeMillis()))
        mutable.update { it.copy(streamingText = "") }
        if (state.value.settings.autoSpeak && foreground) playMessage(message)
    }
    fun markStuck(id: String) {
        val session = state.value.session
        updateSession(session.copy(messages = session.messages.map { if (it.id == id) it.copy(markedStuck = !it.markedStuck) else it }))
    }
    fun redo() {
        if (state.value.busy != null || state.value.voiceActive) return
        val session = state.value.session
        val index = session.messages.indexOfLast { it.role == "user" }; if (index < 0) return
        val message = session.messages[index]
        stopSpeaking()
        updateSession(session.copy(messages = session.messages.take(index), updatedAt = System.currentTimeMillis()))
        session.messages.drop(index).forEach { recordings.remove(it.audioFileName) }
        mutable.update { it.copy(draft = if (message.audioFileName.isBlank()) message.text else "", inputKind = message.inputKind,
            streamingText = "", notice = "上一轮已回退，可以重新说一遍。") }
    }
    fun summarize() { // The toolbar is intentionally a local, blank editor; no model request.
        mutable.update { it.copy(accumulationEditor = ReviewItem(content = "", sourceSessionId = it.currentSessionId)) }
    }
    fun dismissAccumulation() { mutable.update { it.copy(accumulationEditor = null) } }
    fun saveAccumulation(item: ReviewItem) { upsert(item); if (item.content.isNotBlank()) dismissAccumulation() }
    fun identifyExpressions(id: String) {
        if (!canUseService()) return
        runOperation("识别这条消息的好表达…") {
            val session = state.value.session
            val selected = requireNotNull(session.messages.firstOrNull { it.id == id }) { "消息不存在。" }
            require(selected.text.isNotBlank()) { "请先完成语音转写或补充文字。" }
            val raw = if (state.value.settings.demoMode) LearningPrompts.demoExtraction(selected)
                else ai.chat(ManagedService.config(state.value.settings), LearningPrompts.extract(selected), "summary")
            val scoped = session.copy(messages = listOf(selected))
            val candidates = ApiProtocol.summaryCandidates(raw, scoped).map { candidate ->
                candidate.copy(item = candidate.item.copy(sourceNote = "单条消息 · " + if (selected.isHint) "提示建议" else if (selected.role == "user") "自己的表达" else "AI 回复"))
            }
            mutable.update { it.copy(candidates = candidates, notice = if (candidates.isEmpty()) "这条消息没有提取到合适表达，可以手动积累。" else null) }
        }
    }
    fun optimizeExpression(id: String) {
        val selected = state.value.session.messages.firstOrNull { it.id == id } ?: return
        if (selected.role != "user" || !canUseService()) return
        runOperation("优化你的英文表达…") {
            val session = state.value.session
            require(selected.text.isNotBlank()) { "请先完成语音转写或补充文字。" }
            val text = if (state.value.settings.demoMode) "A more natural way to say it:\nI really enjoyed catching up with a friend over coffee.\n\n说明：这是示例优化；保留原意，自然表达。"
                else ai.learning(ManagedService.config(state.value.settings), LearningPrompts.optimize(session, selected))
            appendHint(session.id, ChatMessage(role = "hint", text = text, hintKind = HintKind.OPTIMIZE,
                sourceMessageId = id, hintContextIds = LearningPrompts.contextBefore(session, id).map { it.id }))
        }
    }
    fun topicExpressions() {
        if (!canUseService()) return
        runOperation("准备当前话题的地道表达…") {
            val session = state.value.session
            val context = session.messages.filter { it.isConversation && it.text.isNotBlank() }.takeLast(12)
            require(context.isNotEmpty()) { "先开启一个话题，再获取相关语料。" }
            val text = if (state.value.settings.demoMode) "Talking about your day\n\n• catch up over coffee — 喝咖啡叙叙旧\nI caught up with a friend over coffee.\n\n• look forward to — 期待\nI'm looking forward to the weekend.\n\n（示例语料）"
                else ai.learning(ManagedService.config(state.value.settings), LearningPrompts.topic(context))
            appendHint(session.id, ChatMessage(role = "hint", text = text, hintKind = HintKind.TOPIC, hintContextIds = context.map { it.id }))
        }
    }
    private fun appendHint(sessionId: String, hint: ChatMessage) {
        val session = state.value.sessions.first { it.id == sessionId }
        updateSession(session.copy(messages = session.messages + hint, updatedAt = System.currentTimeMillis()))
    }
    fun regenerate(id: String) {
        val selected = state.value.session.messages.firstOrNull { it.id == id } ?: return
        if (selected.role == "user" || !canUseService()) return
        runOperation("重新生成这条${if (selected.isHint) "提示" else "回复"}…") {
            val session = state.value.session
            val text = if (state.value.settings.demoMode) "${selected.text}\n（重新生成示例）"
            else if (selected.isHint) {
                val context = session.messages.filter { it.id in selected.hintContextIds && it.isConversation }
                val prompts = if (selected.hintKind == HintKind.OPTIMIZE) {
                    val original = requireNotNull(session.messages.firstOrNull { it.id == selected.sourceMessageId }) { "原消息已不存在。" }
                    LearningPrompts.optimize(session.copy(messages = context + original), original)
                } else LearningPrompts.topic(context)
                ai.learning(ManagedService.config(state.value.settings), prompts)
            } else {
                val before = session.copy(messages = session.messages.takeWhile { it.id != id })
                val targets = state.value.library.filter { it.id in session.targetIds }
                ai.chat(ManagedService.config(state.value.settings), listOf("system" to CoachPrompts.system(session.mode, targets)) + CoachPrompts.history(before))
            }
            stopSpeaking()
            val latest = state.value.sessions.first { it.id == session.id }
            updateSession(LearningMessages.replace(latest, id, text))
            mutable.update { it.copy(notice = "已替换此块，其他消息保留。") }
        }
    }
    fun candidateSelected(id: String, selected: Boolean) { mutable.update { it.copy(candidates = it.candidates?.map { candidate -> if (candidate.item.id == id) candidate.copy(selected = selected) else candidate }) } }
    fun editCandidate(item: ReviewItem) { mutable.update { it.copy(candidates = it.candidates?.map { candidate -> if (candidate.item.id == item.id) candidate.copy(item = item) else candidate }) } }
    fun dismissCandidates() { mutable.update { it.copy(candidates = null) } }
    fun saveCandidates() {
        val candidates = state.value.candidates.orEmpty().filter { it.selected }.map { it.item }
        val existing = state.value.library.map { it.kind to it.content.trim().lowercase() }.toSet()
        val fresh = candidates.filter { (it.kind to it.content.trim().lowercase()) !in existing }.distinctBy { it.kind to it.content.trim().lowercase() }
        mutable.update { it.copy(library = fresh + it.library, candidates = null, notice = "已加入 ${fresh.size} 项复习内容。") }; persist()
    }
    fun upsert(item: ReviewItem) {
        if (item.content.isBlank()) { error("请输入词汇、表达或话题。"); return }
        val cleaned = item.copy(content = item.content.trim(), meaning = item.meaning.trim(), example = item.example.trim())
        mutable.update { it.copy(library = if (it.library.any { value -> value.id == item.id }) it.library.map { value -> if (value.id == item.id) cleaned else value } else listOf(cleaned) + it.library, notice = "复习内容已保存。") }; persist()
    }
    fun deleteItem(id: String) { mutable.update { it.copy(library = it.library.filterNot { item -> item.id == id }) }; persist() }
    fun reviewResult(id: String, remembered: Boolean) {
        mutable.update { it.copy(library = it.library.map { item -> if (item.id == id) ReviewSchedule.update(item, remembered) else item }, notice = if (remembered) "已安排下次复习。" else "已标为需要再练。") }; persist()
    }
    fun playMessage(message: ChatMessage) {
        if (state.value.voiceActive) return
        val wasPlaying = state.value.playingId == message.id || state.value.preparingVoiceId == message.id
        stopSpeaking()
        if (wasPlaying) return
        if (message.audioFileName.isNotBlank()) {
            runCatching { speech.play(recordings.file(message.audioFileName), message.id) }.onFailure { error("录音不可用，请重试。") }
            return
        }
        if (state.value.settings.demoMode || !state.value.serviceConfigured) { speech.speak(message.text, message.id); return }
        val cache = File(getApplication<Application>().cacheDir, "reply_${message.id}_${message.revision}.wav")
        if (cache.exists()) { speech.play(cache, message.id); return }
        val token = speechGeneration
        mutable.update { it.copy(preparingVoiceId = message.id) }
        speechJob = viewModelScope.launch {
            try {
                val bytes = ai.speech(ManagedService.config(state.value.settings), message.text)
                withContext(Dispatchers.IO) { cache.writeBytes(bytes) }
                if (token == speechGeneration && !state.value.voiceActive && foreground) speech.play(cache, message.id)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (token == speechGeneration && !state.value.voiceActive && foreground) speech.speak(message.text, message.id) }
            finally { if (token == speechGeneration) mutable.update { it.copy(preparingVoiceId = null) } }
        }
    }
    fun speak(text: String) { if (!state.value.voiceActive) speech.speak(text) }
    fun stopSpeaking() { speechGeneration++; speechJob?.cancel(); speech.stopPlayback(); mutable.update { it.copy(preparingVoiceId = null) } }
    fun startRecording() {
        val current = state.value
        if (current.busy != null || current.voiceActive) return
        if (current.needsReply || current.pendingVoice != null) { error("请先处理上一条消息，或点击重说。"); return }
        if (!canUseService()) return
        mutable.update { it.copy(error = null, notice = null) }
        stopSpeaking()
        speech.start(SpeechMode.CLOUD, current.inputKind)
    }
    fun stopRecording() = speech.stop()
    fun cancelRecording() { speech.cancel(); mutable.update { it.copy(notice = "已取消，这段录音没有发送。") } }
    private fun acceptRecording(file: File, kind: InputKind, duration: Long) {
        try {
            val filename = recordings.save(file)
            val message = ChatMessage(role = "user", text = "", inputKind = kind, fromVoice = true,
                audioFileName = filename, audioDurationMs = duration, transcriptState = TranscriptState.PENDING)
            appendUser(message)
            transcribe(message.id)
        } catch (_: Exception) { file.delete(); error("保存录音失败，请重新录制。") }
    }
    fun transcribe(id: String) {
        if (!canUseService()) return
        runOperation("正在听你的语音…") {
            val sourceSessionId = state.value.currentSessionId
            val message = state.value.session.messages.first { it.id == id }
            updateMessage(message.copy(transcriptState = TranscriptState.PENDING))
            try {
                val current = state.value
                val text = if (current.settings.demoMode) {
                    delay(250)
                    if (message.inputKind == InputKind.HELP) "[示例体验：原录音已保存，未进行真实转写]" else "[Sample mode: recording saved, no real transcription]"
                } else ai.transcribe(ManagedService.config(state.value.settings), recordings.file(message.audioFileName), message.inputKind,
                    current.library.filter { it.id in current.session.targetIds }.map { it.content })
                updateMessage(VoiceMessages.ready(message, text))
            } catch (error: Exception) {
                val originalSession = state.value.sessions.firstOrNull { it.id == sourceSessionId }
                val originalMessage = originalSession?.messages?.firstOrNull { it.id == id }
                if (originalSession != null && originalMessage != null) updateSession(originalSession.copy(messages = originalSession.messages.map {
                    if (it.id == id) originalMessage.copy(transcriptState = TranscriptState.FAILED) else it
                }))
                throw error
            }
            if (state.value.session.messages.lastOrNull { it.isConversation }?.id == id) generateReply()
        }
    }
    fun editTranscript(id: String, text: String) {
        if (state.value.busy != null || state.value.voiceActive || text.isBlank()) return
        val message = state.value.session.messages.firstOrNull { it.id == id } ?: return
        updateMessage(VoiceMessages.ready(message, text))
        if (state.value.session.messages.lastOrNull { it.isConversation }?.id == id) reply()
    }
    private fun updateMessage(message: ChatMessage) {
        val session = state.value.session
        updateSession(session.copy(messages = session.messages.map { if (it.id == message.id) message else it },
            title = if (session.title == "语音练习" && message.text.isNotBlank()) message.text.take(26) else session.title))
    }
    fun foreground() { foreground = true }
    fun background() { foreground = false; if (state.value.voiceActive) speech.cancel(); stopSpeaking() }
    fun cancelOperation() { operationGeneration++; operation?.cancel(); mutable.update { it.copy(busy = null, notice = "请求已取消，消息和原录音已保留。") } }
    private fun runOperation(label: String, action: suspend () -> Unit) {
        if (state.value.busy != null || state.value.voiceActive) return
        mutable.update { it.copy(busy = label, error = null, notice = null) }
        val token = ++operationGeneration
        operation = viewModelScope.launch {
            try { action() } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (token == operationGeneration) mutable.update { it.copy(error = error.message?.take(180) ?: "操作失败，请重试。") } }
            finally { if (token == operationGeneration) mutable.update { it.copy(busy = null) } }
        }
    }
    private fun updateSession(session: ChatSession) { mutable.update { it.copy(sessions = it.sessions.map { saved -> if (saved.id == session.id) session else saved }) }; persist() }
    private fun persist() {
        val current = state.value; val data = SavedData(current.sessions, current.library, current.currentSessionId); val previous = persistJob
        persistJob = viewModelScope.launch(Dispatchers.IO) { previous?.join(); try { store.saveData(data) } catch (_: Exception) { error("学习记录保存失败，请重试。") } }
    }
    private fun demoReply(session: ChatSession, targets: List<ReviewItem>): String {
        if (session.messages.lastOrNull { it.isConversation }?.inputKind == InputKind.HELP) return "试着说：\"Could you give me a moment to think?\"\nTry saying it, then let's continue.（示例）"
        if (session.mode == ConversationMode.REVIEW) return "Let's practice \"${targets.firstOrNull()?.content ?: "Let me think"}\". Imagine we're catching up over coffee. What would you say?"
        return when (session.messages.count { it.role == "user" } % 3) {
            1 -> "Hey! How's your day going? Tell me one little thing that made you smile."
            2 -> "That sounds lovely. What did you enjoy most about it?"
            else -> "I'd love to hear more. What are you looking forward to this weekend?"
        }
    }
    private fun demoCandidates(session: ChatSession) = listOf(
        ReviewCandidate(ReviewItem(content = "Could you give me a moment to think?", meaning = "能给我一点时间想一想吗？", example = "That's a good question. Could you give me a moment to think?", sourceSessionId = session.id, sourceNote = "示例内容 · 请确认")),
        ReviewCandidate(ReviewItem(kind = ReviewKind.WORD, content = "look forward to", meaning = "期待", example = "I'm looking forward to the weekend.", sourceSessionId = session.id, sourceNote = "示例内容 · 请确认")),
    )
    override fun onCleared() { speech.release(); super.onCleared() }
}







