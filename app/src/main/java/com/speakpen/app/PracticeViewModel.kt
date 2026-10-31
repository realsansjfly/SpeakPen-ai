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
    val page: Int = 0, val appForeground: Boolean = true, val settings: AppSettings = AppSettings(),
    val sessions: List<ChatSession> = emptyList(), val currentSessionId: String = "", val library: List<ReviewItem> = emptyList(),
    val draft: String = "", val inputKind: InputKind = InputKind.ENGLISH,
    val serviceStatus: String = "正在检查服务连接…", val serviceOnline: Boolean = false,
    val conversationControlsSupported: Boolean = false,
    val busy: String? = null, val error: String? = null, val notice: String? = null,
    val speechPhase: SpeechPhase = SpeechPhase.IDLE, val recordingLevel: Float = 0f, val playingId: String? = null, val preparingVoiceId: String? = null,
    val streamingText: String = "", val candidates: List<ReviewCandidate>? = null,
    val accumulationEditor: ReviewItem? = null,
    val historyVisible: Boolean = false, val targetPickerVisible: Boolean = false,
    val settingsDraft: AppSettings? = null, val settingsSaving: Boolean = false,
    val conversationDraft: ConversationConfig? = null, val settingsSessionId: String = "",
    val settingsFeedback: String? = null, val settingsSaveError: Boolean = false,
    val pendingSettingsPage: Int? = null, val scrollToMessageId: String? = null,
    val operationAnchorId: String? = null,
    val scenarioTemplates: List<ScenarioTemplate> = emptyList(), val scenarioSuccesses: List<ScenarioSuccess> = emptyList(),
    val scenarioPickerVisible: Boolean = false, val scenarioEditor: ScenarioTemplate? = null,
    val vocabularyBooks: List<VocabularyBook> = emptyList(), val vocabularySaving: Boolean = false,
    val vocabularySuggestion: VocabularySuggestion? = null,
    val speakingPracticeId: String? = null, val recapVisible: Boolean = false, val recapKind: RecapKind = RecapKind.LATEST,
    val scenarioReviewPromptId: String? = null, val rewardPracticeId: String? = null,
    val correctionPendingId: String? = null, val correctionFailedId: String? = null,
) {
    val settingsDirty: Boolean get() = settingsDraft?.let { SettingsUpdates.merge(settings, it) != settings } == true ||
        conversationDraft?.let { draft -> sessions.firstOrNull { it.id == settingsSessionId }?.config?.let { it != draft } } == true
    val recording: Boolean get() = speechPhase == SpeechPhase.LISTENING
    val voiceActive: Boolean get() = speechPhase != SpeechPhase.IDLE
    val session: ChatSession get() = sessions.firstOrNull { it.id == currentSessionId } ?: ChatSession(id = currentSessionId)
    val pendingVoice: ChatMessage? get() = session.messages.lastOrNull { it.isConversation }?.takeIf { it.role == "user" && it.audioFileName.isNotBlank() && it.text.isBlank() }
    val needsReply: Boolean get() = session.messages.lastOrNull { it.isConversation }?.let { it.role == "user" && it.text.isNotBlank() } == true
    val serviceConfigured: Boolean get() = ManagedService.configured(settings)
    val scenarioReadOnly: Boolean get() = session.scenario?.phase == ScenarioPhase.ENDED
    val scenarioOpeningNeeded: Boolean get() = session.scenario?.phase == ScenarioPhase.ACTIVE && session.messages.none { it.scenarioOpening }
    val scenarioInputBlocked: Boolean get() = scenarioReadOnly || scenarioOpeningNeeded || session.scenario?.needsCheck == true
}

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    // Large unconfirmed import content survives Activity recreation without using its Bundle.
    var vocabularyImportRaw: String = ""
    var vocabularyImportPreview: VocabularyPreview? = null
    var vocabularyImportName: String = ""
    var vocabularyImportSource: String = ""
    var vocabularyImportFilename: String = ""
    var vocabularyImportFormat: VocabularyFormat = VocabularyFormat.TXT
    private val store = LocalStore(application)
    private val vocabularyStore = VocabularyStore(application)
    private val vocabularyBooks = vocabularyStore.load()
    private val recordings = RecordingStore(application)
    private val ai = AiClient()
    private val stored = store.readData()
    private val saved = ConversationSessions.normalize(stored.copy(sessions = stored.sessions.map { session ->
        val restored = session.copy(messages = session.messages.map(VoiceMessages::restore), speakingPractices = session.speakingPractices.map {
            if (it.transcriptState == TranscriptState.PENDING) it.copy(transcriptState = TranscriptState.FAILED) else it
        })
        if (restored.scenario?.checkStatus == ScenarioCheckStatus.PENDING) Scenarios.checkFailed(restored) else restored
    }))
    private val preferences = store.readSettings()
    private val recoveredEditor = store.readEditorDraft(preferences)
    private val initialSession = saved.sessions.firstOrNull { it.id == saved.currentSessionId } ?: ChatSession(config = preferences.conversationDefaults)
    private val mutable = MutableStateFlow(SettingsUpdates.apply(PracticeState(
        settings = preferences, vocabularyBooks = vocabularyBooks, sessions = saved.sessions.ifEmpty { listOf(initialSession) },
        currentSessionId = initialSession.id, draft = initialSession.draft, library = saved.library, error = store.loadWarning,
        scenarioTemplates = saved.scenarioTemplates, scenarioSuccesses = saved.sessions.fold(saved) { data, session -> Scenarios.settle(data, session) }.scenarioSuccesses,
    ), preferences).let { initial ->
        recoveredEditor?.takeIf { draft -> initial.sessions.any { it.id == draft.sessionId } }?.let { draft ->
            initial.copy(page = 2, settingsDraft = draft.settings, conversationDraft = draft.conversation, settingsSessionId = draft.sessionId)
        } ?: initial
    })
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    @Volatile private var operationGeneration = 0
    private var persistJob: Job? = null
    private var draftPersistJob: Job? = null
    private var foreground = true
    private var speechJob: Job? = null
    private var speechGeneration = 0
    private var statusJob: Job? = null
    private var correctionJob: Job? = null
    private var correctionGeneration = 0
    private var practiceRecordingId: String? = null
    private val speech = SpeechController(application,
        onFile = { file, kind, duration -> acceptRecording(file, kind, duration) },
        onPhase = { phase -> mutable.update { it.copy(speechPhase = phase) } },
        onPlayback = { id -> mutable.update { it.copy(playingId = id) } },
        onLevel = { level -> mutable.update { it.copy(recordingLevel = level) } },
        onNotice = { notice -> mutable.update { it.copy(notice = notice) } },
        onError = { error -> mutable.update { it.copy(error = error) } },
    )
    init {
        vocabularyStore.warning?.let { error(it) }
        application.cacheDir.listFiles()?.filter { it.name.startsWith("utterance_") }?.forEach { it.delete() }
        if (saved != stored) persist()
        runCatching { store.saveSettings(preferences) }
        checkService()
    }
    fun page(page: Int) {
        val previousPage = state.value.page
        mutable.update { SettingsNavigation.request(it, page) }
        if (state.value.page == 2 && previousPage != 2) persist()
        if (state.value.page == 2 && state.value.pendingSettingsPage != null) saveSettings()
    }
    private fun protectSettingsDraft() {
        val current = state.value
        val draft = current.settingsDraft ?: return
        val config = current.conversationDraft ?: return
        runCatching { store.saveEditorDraft(SettingsEditorDraft(draft, current.settingsSessionId, config)) }
            .onFailure { mutable.update { it.copy(settingsSaveError = true, settingsFeedback = "设置草稿暂未写入本机，请返回时重试保存。") } }
    }
    fun discardSettingsAndLeave() {
        mutable.update { it.copy(page = it.pendingSettingsPage ?: it.page, settingsDraft = it.settings,
            conversationDraft = it.sessions.firstOrNull { s -> s.id == it.settingsSessionId }?.config,
            pendingSettingsPage = null, settingsFeedback = null, settingsSaveError = false) }
    }
    fun stayInSettings() { mutable.update { it.copy(pendingSettingsPage = null) } }
    fun editSettings(value: AppSettings) {
        if (!state.value.settingsSaving) { mutable.update { it.copy(settingsDraft = value, settingsFeedback = null, settingsSaveError = false) }; protectSettingsDraft() }
    }
    fun editConversationSettings(value: ConversationConfig) {
        if (!state.value.settingsSaving) { mutable.update { it.copy(conversationDraft = value, settingsFeedback = null, settingsSaveError = false) }; protectSettingsDraft() }
    }
    fun saveVocabulary(book: VocabularyBook, onComplete: (VocabularyBook?) -> Unit) {
        if (state.value.vocabularySaving) return
        require(book.name.isNotBlank() && book.name.length <= 80 && book.source.isNotBlank() && book.source.length <= 500 && book.entries.isNotEmpty())
        val duplicate = state.value.vocabularyBooks.firstOrNull { it.id == book.id && it.version == book.version ||
            (!book.builtIn && it.name == book.name && it.source == book.source && it.originalText == book.originalText &&
                it.format == book.format && it.fileName == book.fileName && it.digest == book.digest) }
        if (duplicate != null) { onComplete(duplicate); return }
        mutable.update { it.copy(vocabularySaving = true) }
        viewModelScope.launch {
            try {
                val books = state.value.vocabularyBooks + book
                withContext(Dispatchers.IO) { vocabularyStore.save(books) }
                mutable.update { it.copy(vocabularyBooks = books, vocabularySuggestion = null) }
                onComplete(book)
            } catch (_: Exception) { error("词库保存失败，预览已保留，请重试。"); onComplete(null) }
            finally { mutable.update { it.copy(vocabularySaving = false) } }
        }
    }
    fun dismissVocabularySuggestion() { mutable.update { it.copy(vocabularySuggestion = null) } }
    fun requestVocabularyMeaning(book: VocabularyBook, entry: VocabularyEntry) {
        if (entry.meaning.isNotBlank() || entry.aiMeaning.isNotBlank() || state.value.settings.demoMode) {
            error("已有释义不自动覆盖；示例模式不生成词库释义。"); return
        }
        if (!canUseService()) return
        dismissVocabularySuggestion()
        runOperation("补充「${entry.expression.take(30)}」的释义…") {
            val token = operationGeneration
            val (meaning, example) = ai.vocabularyDefinition(ManagedService.config(state.value.settings), entry)
            if (token == operationGeneration) mutable.update { it.copy(vocabularySuggestion = VocabularySuggestion(
                book.id, book.version, entry.id, meaning, example, "AI 补充，应用服务，时间：${System.currentTimeMillis()}")) }
        }
    }
    fun confirmVocabularyMeaning(onComplete: (VocabularyBook?) -> Unit) {
        val suggestion = state.value.vocabularySuggestion ?: return
        val book = state.value.vocabularyBooks.firstOrNull { it.id == suggestion.bookId && it.version == suggestion.version } ?: return
        saveVocabulary(book.supplement(suggestion.entryId, suggestion.meaning, suggestion.example, suggestion.provenance), onComplete)
    }
    private fun vocabularySnapshot(session: ChatSession, context: String = session.messages.filter { it.isConversation }
        .takeLast(8).joinToString("\n") { it.text }): GenerationSnapshot {
        val scene = session.scenario?.takeIf { it.phase == ScenarioPhase.ACTIVE }?.template?.let {
            "${it.name} ${it.aiRole} ${it.userRole}"
        }.orEmpty()
        return GenerationSnapshot.capture(session, VocabularyCandidates.prepare(session, state.value.vocabularyBooks, "$scene\n$context"))
    }
    fun revertSettings() {
        if (!state.value.settingsSaving) mutable.update { it.copy(settingsDraft = it.settings,
            conversationDraft = it.sessions.firstOrNull { s -> s.id == it.settingsSessionId }?.config,
            settingsFeedback = "已撤销未保存的修改。", settingsSaveError = false) }
    }
    fun saveSettings() {
        val current = state.value
        if (current.settingsSaving || (!current.settingsDirty && !current.settingsSaveError)) return
        val draft = current.settingsDraft ?: return
        val conversation = current.conversationDraft ?: current.session.config
        val sessionId = current.settingsSessionId
        if (draft.demoMode != current.settings.demoMode && (current.busy != null || current.voiceActive)) {
            mutable.update { it.copy(settingsFeedback = "请先完成当前操作，再保存示例模式切换。", settingsSaveError = true) }; return
        }
        mutable.update { it.copy(settingsSaving = true, settingsFeedback = "正在保存…", settingsSaveError = false) }
        viewModelScope.launch {
            try {
                val savedSettings = SettingsUpdates.merge(state.value.settings, draft)
                withContext(Dispatchers.IO) { store.saveSettings(savedSettings) }
                mutable.update { latest ->
                    // Connection fields are outside this editor and may change independently.
                    val applied = SettingsUpdates.conversation(SettingsUpdates.apply(latest, SettingsUpdates.merge(latest.settings, draft)), sessionId, conversation)
                    applied.copy(settingsDraft = applied.settings, conversationDraft = conversation)
                }
                val persisted = CompletableDeferred<Boolean>()
                persist { persisted.complete(it) }
                val success = persisted.await()
                if (success) withContext(Dispatchers.IO) { store.clearEditorDraft() }
                mutable.update { SettingsNavigation.completed(it, success) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(settingsSaving = false,
                settingsFeedback = "保存尚未完成，更改已保留，请重试。", settingsSaveError = true) } }
        }
    }
    fun draft(text: String) {
        mutable.update { current -> current.copy(draft = text, sessions = current.sessions.map {
            if (it.id == current.currentSessionId) it.copy(draft = text) else it
        }) }
        draftPersistJob?.cancel()
        draftPersistJob = viewModelScope.launch { delay(400); persist() }
    }
    fun inputKind(kind: InputKind) { if (!state.value.voiceActive && state.value.busy == null) mutable.update { it.copy(inputKind = kind) } }
    fun clearFeedback() { mutable.update { it.copy(error = null, notice = null) } }
    fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun recapExported() { mutable.update { it.copy(notice = "复盘已导出。") } }
    fun preferences(value: AppSettings): Boolean {
        if (state.value.settingsSaving) return false
        return try {
            store.saveSettings(value)
            mutable.update { current ->
                val updated = SettingsUpdates.apply(current, value)
                if (!current.settingsDirty) updated.copy(settingsDraft = if (current.settingsDraft == null) null else value) else updated
            }
            true
        } catch (_: Exception) { error("设置保存失败，请重试。"); false }
    }
    fun connect(raw: String) {
        if (state.value.busy != null || state.value.voiceActive) { error("请先结束当前操作，再打开配对链接。"); return }
        try {
            val connection = ServicePairing.parse(raw)
            if (!preferences(state.value.settings.copy(serviceUrl = connection.baseUrl, serviceAccessToken = connection.accessToken, demoMode = false))) return
            mutable.update { it.copy(page = if (it.page == 2) 2 else 0, notice = "已连接电脑的口语服务。") }
            checkService()
        } catch (_: Exception) { error("服务连接链接无效，请复制电脑新生成的完整服务入口。"); }
    }
    fun checkService() {
        statusJob?.cancel()
        if (!state.value.serviceConfigured) { mutable.update { it.copy(serviceStatus = "尚未连接服务", serviceOnline = false, conversationControlsSupported = false) }; return }
        val settings = state.value.settings
        mutable.update { it.copy(serviceStatus = "正在检查服务连接…", serviceOnline = false, conversationControlsSupported = false) }
        statusJob = viewModelScope.launch {
            try {
                val result = ai.statusInfo(ManagedService.config(settings))
                if (state.value.settings.serviceUrl == settings.serviceUrl) mutable.update { it.copy(serviceStatus = result.label, serviceOnline = true, conversationControlsSupported = result.conversationControlsSupported) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutable.update { it.copy(serviceStatus = "连接已失效或网络不可达。请重新启动电脑服务，扫描新入口；也可点“重新连接”粘贴入口。", serviceOnline = false) }
            }
        }
    }
    private fun canUseService(): Boolean {
        if (state.value.settings.demoMode || ManagedService.configured(state.value.settings)) return true
        error("AI 服务尚未开通，可以先到设置中开启示例体验。")
        return false
    }
    fun showHistory(visible: Boolean) { mutable.update { it.copy(historyVisible = visible) } }
    fun showTargets(visible: Boolean) { mutable.update { it.copy(targetPickerVisible = visible) } }
    fun showScenarios(visible: Boolean) { mutable.update { it.copy(scenarioPickerVisible = visible) } }
    fun editScenario(template: ScenarioTemplate?) { mutable.update { it.copy(scenarioEditor = template) } }
    fun saveScenario(template: ScenarioTemplate) {
        template.validationError()?.let { error(it); return }
        val current = state.value
        val savedTemplate = template.copy(revision = template.revision + 1)
        val templates = current.scenarioTemplates.filterNot { it.id == template.id } + savedTemplate
        mutable.update { it.copy(scenarioTemplates = templates, scenarioEditor = null, scenarioPickerVisible = false, notice = "场景模板已保存。") }
        persist()
    }
    fun startScenario(template: ScenarioTemplate) {
        if (state.value.busy != null || state.value.voiceActive) return
        template.validationError()?.let { error(it); return }
        val current = state.value
        cancelCorrection(); clearLearningSurfaces()
        val session = Scenarios.start(template, current.settings.conversationDefaults)
        mutable.update { it.copy(sessions = listOf(session) + it.sessions, currentSessionId = session.id, page = 0,
            scenarioPickerVisible = false, scenarioEditor = null, draft = "", error = null, notice = "已开始场景：${template.name}" ) }
        persist()
        if (session.messages.isEmpty()) generateScenarioOpening() else checkScenario()
    }
    fun toggleScenarioGoals() { val s = state.value.session; val p = s.scenario ?: return; updateSession(s.copy(scenario = p.copy(goalsCollapsed = !p.goalsCollapsed))) }
    fun correctScenarioGoal(goalId: String, achieved: Boolean, note: String) {
        val updated = Scenarios.correct(state.value.session, goalId, achieved, note)
        if (updated != state.value.session) { updateSession(updated); checkScenario() }
    }
    fun continueScenario() {
        val updated = Scenarios.continueFreely(state.value.session)
        if (updated != state.value.session) { updateSession(updated); mutable.update { it.copy(notice = "已继续为自由对话；原场景结果保持不变。") } }
    }
    fun retryScenarioCheck() { if (state.value.session.scenario?.checkStatus == ScenarioCheckStatus.FAILED) checkScenario() }
    fun retryScenarioOpening() { generateScenarioOpening() }
    private fun generateScenarioOpening() {
        val session = state.value.session
        if (session.scenario?.phase != ScenarioPhase.ACTIVE || session.messages.any { it.scenarioOpening } || state.value.busy != null) return
        runOperation("准备场景开场白…") {
            val snapshot = vocabularySnapshot(session); val token = operationGeneration
            val text = if (state.value.settings.demoMode) session.scenario!!.template.opening.ifBlank { "Hi! Welcome. How can I help you today?" }
                else ai.chat(ManagedService.config(state.value.settings), ScenarioProtocol.opening(session.copy(config = snapshot.config)), "chat", snapshot.config.advanced)
            if (!isCurrent(snapshot, token)) return@runOperation
            val latest = state.value.sessions.first { it.id == session.id }
            updateSession(latest.copy(messages = latest.messages + ChatMessage(role = "assistant", text = text, generationConfig = snapshot.config, configRevision = snapshot.configRevision, scenarioOpening = true), updatedAt = System.currentTimeMillis()))
        }
    }
    private fun checkScenario() {
        val session = state.value.session
        val practice = session.scenario ?: return
        if (practice.phase != ScenarioPhase.ACTIVE || !practice.needsCheck || state.value.busy != null) return
        val snapshot = ScenarioCheckSnapshot(session); val generation = ++operationGeneration
        mutable.update { it.copy(busy = "检查场景目标…", error = null) }
        operation = viewModelScope.launch {
            try {
                val assessment = if (state.value.settings.demoMode) demoScenarioAssessment(session) else ai.scenarioAssessment(ManagedService.config(state.value.settings), session)
                if (generation == operationGeneration && snapshot.matches(state.value.session)) updateSession(Scenarios.assess(state.value.session, assessment))
            } catch (_: CancellationException) { throw CancellationException() }
            catch (error: Exception) { if (generation == operationGeneration && snapshot.matches(state.value.session)) {
                val failed = Scenarios.checkFailed(state.value.session)
                updateSession(failed.copy(scenario = failed.scenario?.copy(checkError = error.message ?: "判定失败")))
            } }
            finally { if (generation == operationGeneration) mutable.update { it.copy(busy = null) } }
        }
    }
    private fun demoScenarioAssessment(session: ChatSession): ScenarioAssessment {
        val practice = requireNotNull(session.scenario)
        val last = session.messages.lastOrNull { it.role == "user" && !it.scenarioOpening }
        val proof = last?.let { listOf(ScenarioEvidence(it.id, it.text.take(80))) }.orEmpty()
        return ScenarioAssessment(practice.template.goals.map { ScenarioGoalProgress(it.id, false, "示例体验不自动判定目标。") }, false, "示例体验不自动结束场景。", proof)
    }
    fun selectSession(id: String) {
        if (state.value.busy != null || state.value.voiceActive || state.value.settingsSaving) return
        val selected = state.value.sessions.firstOrNull { it.id == id } ?: return
        stopSpeaking()
        cancelCorrection()
        clearLearningSurfaces()
        mutable.update { it.copy(currentSessionId = id, draft = selected.draft, candidates = null, historyVisible = false,
            inputKind = InputKind.ENGLISH, error = null, streamingText = "", operationAnchorId = null, scrollToMessageId = null) }
        persist()
    }
    fun newSession(mode: ConversationMode, targets: List<String> = emptyList()) {
        if (state.value.busy != null || state.value.voiceActive) return
        if (mode == ConversationMode.SCENARIO) { showScenarios(true); return }
        if (mode == ConversationMode.REVIEW && targets.isEmpty()) { showTargets(true); return }
        stopSpeaking()
        cancelCorrection()
        clearLearningSurfaces()
        val current = state.value
        val started = ConversationSessions.start(SavedData(current.sessions, current.library, current.currentSessionId), mode, targets, current.settings.conversationDefaults)
        mutable.update { it.copy(sessions = started.sessions, currentSessionId = started.currentSessionId, page = 0,
            draft = "", candidates = null, targetPickerVisible = false, error = null, notice = null, inputKind = InputKind.ENGLISH, streamingText = "", operationAnchorId = null, scrollToMessageId = null) }
        persist()
    }
    fun send() {
        val current = state.value
        if (current.scenarioInputBlocked) return
        if (current.draft.isBlank() || current.voiceActive || current.busy != null) return
        if (current.needsReply || current.pendingVoice != null) { error("请先重试上一条消息或重说。草稿已保留。"); return }
        if (!canUseService()) return
        appendUser(ChatMessage(role = "user", text = current.draft.trim(), inputKind = current.inputKind))
        draft("")
        mutable.update { it.copy(notice = null) }
        reply()
    }
    private fun appendUser(message: ChatMessage) {
        cancelCorrection()
        val session = state.value.session
        updateSession(session.copy(messages = session.messages + message,
            title = if (session.messages.isEmpty()) message.text.take(26).ifBlank { "语音练习" } else session.title,
            updatedAt = System.currentTimeMillis()))
    }
    fun startConversation(topic: String = "") {
        if (state.value.session.scenario?.phase == ScenarioPhase.ACTIVE) { generateScenarioOpening(); return }
        if (state.value.session.messages.isNotEmpty()) return
        mutable.update { it.copy(draft = when {
            topic.isNotBlank() -> "Let's talk about $topic."
            it.session.mode == ConversationMode.REVIEW -> "Let's practice my review targets."
            else -> "Hi! Let's have a conversation."
        }, inputKind = InputKind.ENGLISH) }
        send()
    }
    fun reply() {
        if (state.value.scenarioInputBlocked) return
        if (!canUseService() || !state.value.needsReply) return
        runOperation("AI 正在回复…") { generateReply() }
    }
    private suspend fun generateReply() {
        val current = state.value; val session = current.session
        val requestGeneration = operationGeneration
        val snapshot = vocabularySnapshot(session)
        val targets = current.library.filter { it.id in session.targetIds }
        mutable.update { it.copy(busy = "AI 正在回复…", streamingText = "") }
        val text = if (current.settings.demoMode) {
            val sample = demoReply(session, targets)
            for (end in (1..sample.length).filter { it % 9 == 0 || it == sample.length }) { delay(35); publishStreamingText(snapshot, requestGeneration, sample.take(end)) }
            sample
        } else ai.streamChat(ManagedService.config(current.settings), listOf("system" to (CoachPrompts.system(session.mode, targets, snapshot.config) + "\n" + ScenarioProtocol.roleInstruction(session))) + CoachPrompts.history(session), snapshot.config.advanced) { text ->
            publishStreamingText(snapshot, requestGeneration, text)
        }
        if (!isCurrent(snapshot, requestGeneration)) return
        val userId = session.messages.last { it.role == "user" }.id
        val message = ChatMessage(role = "assistant", text = text, generationConfig = snapshot.config, configRevision = snapshot.configRevision, replyToMessageId = userId)
        val latest = state.value.sessions.first { it.id == session.id }
        updateSession(Scenarios.recordTurn(latest.copy(messages = latest.messages + message, updatedAt = System.currentTimeMillis()), userId, message.id))
        mutable.update { it.copy(streamingText = "") }
        if (state.value.settings.autoSpeak && foreground) playMessage(message)
        requestCorrection(userId, snapshot.config)
        checkScenario()
    }
    fun markStuck(id: String) {
        if (state.value.scenarioReadOnly || Scenarios.frozen(state.value.session, id)) return
        val session = state.value.session
        updateSession(session.copy(messages = session.messages.map { if (it.id == id) it.copy(markedStuck = !it.markedStuck) else it }))
    }
    fun redo() {
        if (state.value.busy != null || state.value.voiceActive) return
        val session = state.value.session
        val index = session.messages.indexOfLast { it.role == "user" }; if (index < 0) return
        val message = session.messages[index]
        if (state.value.scenarioReadOnly || Scenarios.frozen(session, message.id)) return
        stopSpeaking()
        updateSession(Scenarios.rewind(session, index))
        cancelCorrection()
        session.messages.drop(index).forEach { old ->
            val referenced = state.value.sessions.any { s -> s.messages.any { it.audioFileName == old.audioFileName } ||
                s.speakingPractices.any { it.audioFileName == old.audioFileName } || s.scenario?.ending?.messages?.any { it.audioFileName == old.audioFileName } == true }
            if (!referenced) recordings.remove(old.audioFileName)
        }
        mutable.update { it.copy(draft = if (message.audioFileName.isBlank()) message.text else "", inputKind = message.inputKind,
            streamingText = "", notice = "上一轮已回退，可以重新说一遍。") }
    }
    fun layeredHint() {
        if (state.value.scenarioReadOnly || !canUseService()) return
        val session = state.value.session
        val context = session.messages.filter { it.isConversation && it.text.isNotBlank() }.takeLast(8)
        if (context.isEmpty()) { error("先开启对话，再获取表达提示。"); return }
        val existing = session.messages.lastOrNull { it.hintKind == HintKind.SCAFFOLD && LearningFeatures.hintCurrent(session, it) }
        if (existing != null) {
            hintCollapsed(existing.id, false)
            mutable.update { it.copy(scrollToMessageId = existing.id) }; return
        }
        runOperation("准备表达提示…") {
            val snapshot = vocabularySnapshot(session); val token = operationGeneration
            val layers = if (state.value.settings.demoMode) listOf("think：思考；moment：片刻", "Could you give me ___?\n填入你需要的时间。", "Could you give me a moment to think?\n示例：能给我一点时间想一想吗？") else
                LearningFeatureProtocol.parseScaffold(ai.structuredLearning(ManagedService.config(state.value.settings),
                    LearningFeatureProtocol.scaffold(session.copy(messages = context, config = snapshot.config))))
            if (!isCurrent(snapshot, token)) return@runOperation
            appendHint(session.id, ChatMessage(role = "hint", text = layers.first(), hintKind = HintKind.SCAFFOLD,
                sourceMessageId = context.last().id, hintContextIds = context.map { it.id }, hintContextDigest = LearningFeatures.contextDigest(context),
                hintLayers = layers, generationConfig = snapshot.config, configRevision = snapshot.configRevision))
        }
    }
    fun revealHint(id: String) { updateSession(LearningFeatures.reveal(state.value.session, id)) }
    private fun cancelCorrection() {
        correctionGeneration++; correctionJob?.cancel()
        mutable.update { it.copy(correctionPendingId = null, correctionFailedId = null) }
    }
    private fun clearLearningSurfaces() { practiceRecordingId = null; mutable.update { it.copy(speakingPracticeId = null, recapVisible = false,
        scenarioReviewPromptId = null, rewardPracticeId = null) } }
    fun retryCorrection(id: String) {
        val config = state.value.session.messages.firstOrNull { it.replyToMessageId == id }?.generationConfig ?: state.value.session.config
        requestCorrection(id, config)
    }
    private fun requestCorrection(id: String, config: ConversationConfig) {
        if (!config.realtimeCorrection) return
        val session = state.value.session
        val selected = session.messages.firstOrNull { it.id == id && it.role == "user" } ?: return
        if (selected.inputKind == InputKind.HELP || selected.text.isBlank()) return
        cancelCorrection()
        val token = correctionGeneration
        val digest = LearningFeatures.contextDigest(session.messages)
        val settings = state.value.settings
        mutable.update { it.copy(correctionPendingId = id) }
        correctionJob = viewModelScope.launch {
            fun current() = token == correctionGeneration && state.value.currentSessionId == session.id &&
                LearningFeatures.contextDigest(state.value.session.messages) == digest
            try {
                val text = if (settings.demoMode) "" else LearningFeatureProtocol.parseCorrection(ai.structuredLearning(
                    ManagedService.config(settings), LearningFeatureProtocol.correction(session, selected, config)), selected)
                if (current() && text.isNotBlank()) {
                    val context = LearningPrompts.contextBefore(session, id) + selected
                    val hint = ChatMessage(role = "hint", text = text, hintKind = HintKind.CORRECTION, sourceMessageId = id,
                        hintContextIds = context.map { it.id }, hintContextDigest = LearningFeatures.contextDigest(context), generationConfig = config)
                    // Do not scroll, play speech or replace the role reply when the side request completes.
                    val latest = state.value.session
                    updateSession(LearningMessages.append(latest.copy(messages = latest.messages.filterNot {
                        it.hintKind == HintKind.CORRECTION && it.sourceMessageId == id && it.hintContextDigest == hint.hintContextDigest
                    }), hint))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current()) mutable.update { it.copy(correctionFailedId = id) } }
            finally { if (token == correctionGeneration) mutable.update { it.copy(correctionPendingId = null) } }
        }
    }
    fun openSpeakingPractice(hintId: String) {
        if (state.value.busy != null || state.value.voiceActive) return
        val session = state.value.session
        try {
            val draft = LearningFeatures.beginPractice(session, hintId)
            val old = session.speakingPractices.lastOrNull { it.hintId == hintId && it.hintRevision == draft.hintRevision && it.sentMessageId.isBlank() }
            val practice = old ?: draft
            if (old == null) updateSession(session.copy(speakingPractices = session.speakingPractices + practice))
            mutable.update { it.copy(speakingPracticeId = practice.id) }
        } catch (e: Exception) { error(e.message ?: "无法开始练习。") }
    }
    fun showSpeakingPractice(id: String?) {
        if (state.value.voiceActive || state.value.busy != null) return
        practiceRecordingId = null
        stopSpeaking(); mutable.update { it.copy(speakingPracticeId = id) }
    }
    private fun updatePractice(practice: SpeakingPractice) {
        val s = state.value.session
        if (s.speakingPractices.none { it.id == practice.id }) return
        updateSession(s.copy(speakingPractices = s.speakingPractices.map { if (it.id == practice.id) practice else it }))
    }
    private fun startPracticeRecording() {
        val current = state.value
        val p = current.session.speakingPractices.firstOrNull { it.id == current.speakingPracticeId } ?: return
        if (current.voiceActive || current.busy != null || p.sentMessageId.isNotBlank()) return
        if (!canUseService()) return
        stopSpeaking(); practiceRecordingId = p.id
        speech.start(SpeechMode.CLOUD, InputKind.ENGLISH)
    }
    private fun acceptPracticeRecording(id: String, file: File, duration: Long) {
        if (state.value.session.speakingPractices.none { it.id == id }) { file.delete(); return }
        try {
            val name = recordings.save(file)
            val (updated, practice) = LearningFeatures.recordPractice(state.value.session, id, name, duration)
            updateSession(updated)
            mutable.update { it.copy(speakingPracticeId = practice.id) }
            transcribePractice(practice.id)
        } catch (_: Exception) { file.delete(); error("练习录音保存失败，请重试。") }
    }
    fun transcribePractice(id: String) {
        val s = state.value.session; val p = s.speakingPractices.firstOrNull { it.id == id } ?: return
        if (p.audioFileName.isBlank() || p.sentMessageId.isNotBlank() || !canUseService()) return
        runOperation("转写练习录音…") {
            updatePractice(p.copy(transcriptState = TranscriptState.PENDING))
            val token = operationGeneration
            try {
                val text = if (state.value.settings.demoMode) "" else ai.transcribe(ManagedService.config(state.value.settings), recordings.file(p.audioFileName), InputKind.ENGLISH, emptyList())
                check(text.isNotBlank()) { "未完成真实转写，可回听并手动补充文字。" }
                if (token == operationGeneration && state.value.currentSessionId == s.id) updatePractice(p.copy(transcript = text, transcriptState = TranscriptState.READY))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == operationGeneration && state.value.currentSessionId == s.id) updatePractice(p.copy(transcriptState = TranscriptState.FAILED)); throw e }
        }
    }
    fun practiceTranscript(id: String, text: String) {
        if (state.value.busy != null || state.value.voiceActive) return
        val p = state.value.session.speakingPractices.firstOrNull { it.id == id && it.sentMessageId.isBlank() } ?: return
        updatePractice(p.copy(transcript = text.take(4000), transcriptState = if (text.isBlank()) TranscriptState.NONE else TranscriptState.READY))
    }
    fun playPractice(id: String) {
        val p = state.value.session.speakingPractices.firstOrNull { it.id == id } ?: return
        if (p.audioFileName.isNotBlank()) playMessage(ChatMessage(id = p.id, role = "user", text = p.transcript, audioFileName = p.audioFileName))
    }
    fun sendPractice(id: String) {
        val current = state.value
        if (current.busy != null || current.voiceActive || current.scenarioInputBlocked || current.needsReply || current.pendingVoice != null || !canUseService()) return
        val p = current.session.speakingPractices.firstOrNull { it.id == id } ?: return
        try {
            cancelCorrection()
            updateSession(LearningFeatures.sendPractice(current.session, p.id))
            mutable.update { it.copy(speakingPracticeId = null) }; reply()
        } catch (e: Exception) { error(e.message ?: "发送失败，练习已保留。") }
    }
    fun showRecaps(visible: Boolean, kind: RecapKind = state.value.recapKind) { mutable.update { it.copy(recapVisible = visible, recapKind = kind) } }
    fun finishReward() { mutable.update { it.copy(rewardPracticeId = null) } }
    fun dismissScenarioReviewPrompt() { mutable.update { it.copy(scenarioReviewPromptId = null) } }
    fun generateRecap(kind: RecapKind) {
        if (!canUseService()) return
        val session = state.value.session
        val snapshot = try { RecapSnapshot.capture(session, kind) } catch (e: Exception) { error(e.message ?: "无法生成复盘。"); return }
        runOperation("生成${kind.label}…") {
            val token = operationGeneration
            val raw = if (state.value.settings.demoMode) org.json.JSONObject().put("overview", "这是基于本地示例的复盘，不是实际能力评估。")
                .put("evidence", org.json.JSONArray().put(org.json.JSONObject().put("messageId", snapshot.messages.first().id)
                    .put("quote", snapshot.messages.first().text.take(60)).put("comment", "对话中的实际文字。")))
                .put("improvements", org.json.JSONArray()).put("vocabulary", org.json.JSONArray()).toString()
            else ai.structuredLearning(ManagedService.config(state.value.settings), LearningFeatureProtocol.recap(snapshot), "summary")
            val markdown = LearningFeatureProtocol.parseRecap(raw, snapshot)
            if (token != operationGeneration || state.value.currentSessionId != session.id) return@runOperation
            updateSession(snapshot.complete(state.value.session, markdown))
            mutable.update { it.copy(recapVisible = true, recapKind = kind) }
        }
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
            val snapshot = GenerationSnapshot.capture(session); val token = operationGeneration
            val selected = requireNotNull(session.messages.firstOrNull { it.id == id }) { "消息不存在。" }
            require(selected.text.isNotBlank()) { "请先完成语音转写或补充文字。" }
            val raw = if (state.value.settings.demoMode) LearningPrompts.demoExtraction(selected)
                else ai.chat(ManagedService.config(state.value.settings), LearningPrompts.extract(selected, snapshot.config), "summary")
            if (!isCurrent(snapshot, token)) return@runOperation
            val scoped = session.copy(messages = listOf(selected))
            val candidates = ApiProtocol.summaryCandidates(raw, scoped).map { candidate ->
                candidate.copy(item = candidate.item.copy(sourceNote = "单条消息：" + if (selected.isHint) "提示建议" else if (selected.role == "user") "自己的表达" else "AI 回复"))
            }
            mutable.update { it.copy(candidates = candidates, notice = if (candidates.isEmpty()) "这条消息没有提取到合适表达，可以手动积累。" else null) }
        }
    }
    fun optimizeExpression(id: String) {
        if (state.value.scenarioReadOnly) return
        val selected = state.value.session.messages.firstOrNull { it.id == id } ?: return
        if (selected.role != "user" || !canUseService()) return
        runOperation("优化你的英文表达…", anchorId = id) {
            val session = state.value.session
            val snapshot = vocabularySnapshot(session, (LearningPrompts.contextBefore(session, id) + selected).joinToString("\n") { it.text }); val token = operationGeneration
            require(selected.text.isNotBlank()) { "请先完成语音转写或补充文字。" }
            val result = if (state.value.settings.demoMode) OptimizedExpression("Could you give me a moment to think?", "示例参考句：请求一点思考时间，未分析你的原句。")
                else LearningFeatureProtocol.parseOptimize(ai.structuredLearning(ManagedService.config(state.value.settings), LearningFeatureProtocol.optimize(session.copy(config = snapshot.config), selected)))
            if (!isCurrent(snapshot, token)) return@runOperation
            val context = LearningPrompts.contextBefore(session, id) + selected
            appendHint(session.id, ChatMessage(role = "hint", text = result.text, practiceReference = result.reference, hintKind = HintKind.OPTIMIZE,
                generationConfig = snapshot.config, configRevision = snapshot.configRevision,
                sourceMessageId = id, hintContextIds = context.map { it.id }, hintContextDigest = LearningFeatures.contextDigest(context)))
        }
    }
    fun topicExpressions() {
        if (state.value.scenarioReadOnly) return
        if (!canUseService()) return
        runOperation("准备当前话题的地道表达…") {
            val session = state.value.session
            val snapshot = vocabularySnapshot(session); val token = operationGeneration
            val context = session.messages.filter { it.isConversation && it.text.isNotBlank() }.takeLast(12)
            require(context.isNotEmpty()) { "先开启一个话题，再获取相关语料。" }
            val text = if (state.value.settings.demoMode) "Talking about your day\n\n• catch up over coffee — 喝咖啡叙叙旧\nI caught up with a friend over coffee.\n\n• look forward to — 期待\nI'm looking forward to the weekend.\n\n（示例语料）"
                else ai.learning(ManagedService.config(state.value.settings), LearningPrompts.topic(context, snapshot.config))
            if (!isCurrent(snapshot, token)) return@runOperation
            appendHint(session.id, ChatMessage(role = "hint", text = text, hintKind = HintKind.TOPIC, hintContextIds = context.map { it.id },
                generationConfig = snapshot.config, configRevision = snapshot.configRevision))
        }
    }
    private fun appendHint(sessionId: String, hint: ChatMessage) {
        val session = state.value.sessions.first { it.id == sessionId }
        updateSession(LearningMessages.append(session, hint))
        mutable.update { it.copy(scrollToMessageId = hint.id, operationAnchorId = hint.id) }
    }
    fun hintCollapsed(id: String, collapsed: Boolean) {
        val session = state.value.session
        if (session.messages.none { it.id == id && it.isHint }) return
        updateSession(session.copy(messages = session.messages.map { if (it.id == id) it.copy(hintCollapsed = collapsed) else it }))
    }
    fun scrollConsumed(id: String) { mutable.update { if (it.scrollToMessageId == id) it.copy(scrollToMessageId = null) else it } }
    fun regenerate(id: String) {
        val selected = state.value.session.messages.firstOrNull { it.id == id } ?: return
        if (state.value.scenarioReadOnly || Scenarios.frozen(state.value.session, id) || selected.scenarioOpening) return
        if (selected.role == "user" || !canUseService()) return
        runOperation("重新生成这条${if (selected.isHint) "提示" else "回复"}…", anchorId = selected.id) {
            val session = state.value.session
            val snapshot = GenerationSnapshot.capture(session); val token = operationGeneration
            val config = selected.generationConfig ?: ConversationConfig()
            var reference = selected.practiceReference
            var layers = selected.hintLayers
            val text = if (state.value.settings.demoMode) "${selected.text}\n（重新生成示例）"
            else if (selected.isHint) {
                require(LearningFeatures.hintCurrent(session, selected)) { "对话或原句已变化，请从当前内容重新获取提示。" }
                val context = session.messages.filter { it.id in selected.hintContextIds && it.isConversation }
                val scoped = session.copy(messages = context, config = config)
                when (selected.hintKind) {
                    HintKind.OPTIMIZE -> {
                        val original = requireNotNull(session.messages.firstOrNull { it.id == selected.sourceMessageId }) { "原消息已不存在。" }
                        val result = LearningFeatureProtocol.parseOptimize(ai.structuredLearning(ManagedService.config(state.value.settings),
                            LearningFeatureProtocol.optimize(scoped.copy(messages = context.filterNot { it.id == original.id } + original), original)))
                        reference = result.reference; result.text
                    }
                    HintKind.SCAFFOLD -> {
                        layers = LearningFeatureProtocol.parseScaffold(ai.structuredLearning(ManagedService.config(state.value.settings), LearningFeatureProtocol.scaffold(scoped)))
                        layers.first()
                    }
                    HintKind.CORRECTION -> {
                        val original = requireNotNull(session.messages.firstOrNull { it.id == selected.sourceMessageId })
                        LearningFeatureProtocol.parseCorrection(ai.structuredLearning(ManagedService.config(state.value.settings), LearningFeatureProtocol.correction(scoped, original, config)), original)
                            .ifBlank { "这次未发现需要纠正的明确错误。" }
                    }
                    else -> ai.learning(ManagedService.config(state.value.settings), LearningPrompts.topic(context, config))
                }
            } else {
                val before = session.copy(messages = session.messages.takeWhile { it.id != id })
                val targets = state.value.library.filter { it.id in session.targetIds }
                ai.chat(ManagedService.config(state.value.settings), listOf("system" to (CoachPrompts.system(session.mode, targets, config) + "\n" + ScenarioProtocol.roleInstruction(session))) + CoachPrompts.history(before), advanced = config.advanced)
            }
            if (!isCurrent(snapshot, token)) return@runOperation
            stopSpeaking()
            val latest = state.value.sessions.first { it.id == session.id }
            val replaced = LearningMessages.replace(latest, id, text).let { replaced -> replaced.copy(messages = replaced.messages.map {
                if (it.id == id) it.copy(practiceReference = reference, hintLayers = layers, revealedLayers = 1) else it
            }) }
            updateSession(if (!selected.isHint) Scenarios.invalidateAssessment(replaced) else replaced)
            mutable.update { it.copy(scrollToMessageId = id) }
            mutable.update { it.copy(notice = "已替换此块，其他消息保留。") }
            if (!selected.isHint) checkScenario()
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
        if (current.speakingPracticeId != null) { startPracticeRecording(); return }
        if (current.scenarioInputBlocked) return
        if (current.busy != null || current.voiceActive) return
        if (current.needsReply || current.pendingVoice != null) { error("请先处理上一条消息，或点击重说。"); return }
        if (!canUseService()) return
        mutable.update { it.copy(error = null, notice = null) }
        stopSpeaking()
        practiceRecordingId = null
        speech.start(SpeechMode.CLOUD, current.inputKind)
    }
    fun stopRecording() { speech.stop(); if (!state.value.voiceActive) practiceRecordingId = null }
    fun cancelRecording() { speech.cancel(); practiceRecordingId = null; mutable.update { it.copy(notice = "已取消，这段录音没有发送。") } }
    private fun acceptRecording(file: File, kind: InputKind, duration: Long) {
        practiceRecordingId?.let { id ->
            practiceRecordingId = null
            acceptPracticeRecording(id, file, duration)
            return
        }
        if (state.value.scenarioInputBlocked) { file.delete(); return }
        try {
            val filename = recordings.save(file)
            val message = ChatMessage(role = "user", text = "", inputKind = kind, fromVoice = true,
                audioFileName = filename, audioDurationMs = duration, transcriptState = TranscriptState.PENDING)
            appendUser(message)
            transcribe(message.id)
        } catch (_: Exception) { file.delete(); error("保存录音失败，请重新录制。") }
    }
    fun transcribe(id: String) {
        if (state.value.scenarioReadOnly || Scenarios.frozen(state.value.session, id)) return
        if (!canUseService()) return
        runOperation("正在听你的语音…") {
            val sourceSessionId = state.value.currentSessionId
            val message = state.value.session.messages.first { it.id == id }
            updateMessage(message.copy(transcriptState = TranscriptState.PENDING))
            val snapshot = GenerationSnapshot.capture(state.value.session); val token = operationGeneration
            try {
                val current = state.value
                val text = if (current.settings.demoMode) {
                    delay(250)
                    if (message.inputKind == InputKind.HELP) "[示例体验：原录音已保存，未进行真实转写]" else "[Sample mode: recording saved, no real transcription]"
                } else ai.transcribe(ManagedService.config(state.value.settings), recordings.file(message.audioFileName), message.inputKind,
                    current.library.filter { it.id in current.session.targetIds }.map { it.content })
                if (!isCurrent(snapshot, token)) return@runOperation
                updateMessage(VoiceMessages.ready(message, text))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val originalSession = state.value.sessions.firstOrNull { it.id == sourceSessionId }
                val originalMessage = originalSession?.messages?.firstOrNull { it.id == id }
                if (isCurrent(snapshot, token) && originalSession != null && originalMessage != null) updateSession(originalSession.copy(messages = originalSession.messages.map {
                    if (it.id == id) originalMessage.copy(transcriptState = TranscriptState.FAILED) else it
                }))
                throw error
            }
            if (state.value.session.messages.lastOrNull { it.isConversation }?.id == id) generateReply()
        }
    }
    fun editTranscript(id: String, text: String) {
        if (state.value.scenarioReadOnly || Scenarios.frozen(state.value.session, id)) return
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
    fun foreground() { foreground = true; mutable.update { it.copy(appForeground = true) } }
    fun background() {
        foreground = false; mutable.update { it.copy(appForeground = false) }
        if (state.value.voiceActive) { speech.cancel(); practiceRecordingId = null }
        stopSpeaking()
        draftPersistJob?.cancel(); persist()
        if (state.value.page == 2 && (state.value.settingsDirty || state.value.settingsSaveError)) saveSettings()
    }
    fun cancelOperation() {
        operationGeneration++; operation?.cancel()
        cancelCorrection()
        val session = state.value.session
        if (session.scenario?.needsCheck == true) updateSession(Scenarios.checkFailed(session))
        if (session.messages.any { it.transcriptState == TranscriptState.PENDING }) {
            updateSession(session.copy(messages = session.messages.map(VoiceMessages::restore)))
        }
        val latest = state.value.session
        if (latest.speakingPractices.any { it.transcriptState == TranscriptState.PENDING }) updateSession(latest.copy(speakingPractices = latest.speakingPractices.map {
            if (it.transcriptState == TranscriptState.PENDING) it.copy(transcriptState = TranscriptState.FAILED) else it
        }))
        mutable.update { it.copy(busy = null, streamingText = "", notice = "请求已取消，消息和原录音已保留。") }
    }
    private fun isCurrent(snapshot: GenerationSnapshot, token: Int): Boolean {
        val current = state.value
        return snapshot.canApply(current.session, current.currentSessionId, token, operationGeneration)
    }
    private fun publishStreamingText(snapshot: GenerationSnapshot, token: Int, text: String) {
        mutable.update { current ->
            if (snapshot.canApply(current.session, current.currentSessionId, token, operationGeneration)) current.copy(streamingText = text) else current
        }
    }
    private fun runOperation(label: String, anchorId: String? = null, action: suspend () -> Unit) {
        if (state.value.busy != null || state.value.voiceActive) return
        mutable.update { it.copy(busy = label, error = null, notice = null, operationAnchorId = anchorId, scrollToMessageId = null) }
        val token = ++operationGeneration
        operation = viewModelScope.launch {
            try { action() } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (token == operationGeneration) mutable.update { it.copy(error = error.message?.take(180) ?: "操作失败，请重试。") } }
            finally { if (token == operationGeneration) { mutable.update { it.copy(busy = null) }; checkScenario() } }
        }
    }
    private fun updateSession(session: ChatSession) {
        mutable.update { current ->
            val before = current.sessions.firstOrNull { it.id == session.id }
            val justEnded = before?.scenario?.ending == null && session.scenario?.ending != null && session.scenario.completionPresented == false
            val presented = if (justEnded) session.copy(scenario = session.scenario!!.copy(completionPresented = true)) else session
            val data = Scenarios.settle(SavedData(current.sessions, current.library, current.currentSessionId, current.scenarioTemplates, current.scenarioSuccesses), presented)
            current.copy(sessions = data.sessions, scenarioSuccesses = data.scenarioSuccesses,
                scenarioReviewPromptId = if (justEnded) session.id else current.scenarioReviewPromptId,
                rewardPracticeId = if (justEnded && session.scenario?.ending?.succeeded == true && current.scenarioSuccesses.none { it.practiceId == session.scenario.practiceId }) session.scenario.practiceId else current.rewardPracticeId)
        }
        persist()
    }
    private fun persist(onComplete: ((Boolean) -> Unit)? = null) {
        val current = state.value; val data = SavedData(current.sessions, current.library, current.currentSessionId, current.scenarioTemplates, current.scenarioSuccesses); val previous = persistJob
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            val success = try { store.saveData(data); true } catch (_: Exception) { error("学习记录保存失败，请重试。"); false }
            onComplete?.invoke(success)
        }
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
        ReviewCandidate(ReviewItem(content = "Could you give me a moment to think?", meaning = "能给我一点时间想一想吗？", example = "That's a good question. Could you give me a moment to think?", sourceSessionId = session.id, sourceNote = "示例内容，请确认")),
        ReviewCandidate(ReviewItem(kind = ReviewKind.WORD, content = "look forward to", meaning = "期待", example = "I'm looking forward to the weekend.", sourceSessionId = session.id, sourceNote = "示例内容，请确认")),
    )
    override fun onCleared() { speech.release(); super.onCleared() }
}







