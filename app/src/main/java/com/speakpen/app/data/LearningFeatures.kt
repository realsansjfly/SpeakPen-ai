package com.speakpen.app.data

import org.json.JSONObject

/** Unsent speech belongs to a practice, never to the conversation transcript. */
data class SpeakingPractice(
    val id: String = newId(), val sourceMessageId: String, val sourceRevision: Int,
    val original: String, val hintId: String, val hintRevision: Int, val reference: String,
    val config: ConversationConfig, val audioFileName: String = "", val audioDurationMs: Long = 0,
    val transcript: String = "", val transcriptState: TranscriptState = TranscriptState.NONE,
    val sentMessageId: String = "", val createdAt: Long = System.currentTimeMillis(),
)

object LearningFeatures {
    fun contextDigest(messages: List<ChatMessage>): String = vocabularyHash(messages.filter { it.isConversation }
        .joinToString("\u0000") { "${it.id}:${it.revision}:${it.text}" })
    fun hintCurrent(session: ChatSession, hint: ChatMessage): Boolean {
        if (hint.hintKind == HintKind.SCAFFOLD && session.messages.lastOrNull { it.isConversation }?.id != hint.sourceMessageId) return false
        if (hint.hintContextDigest.isBlank()) return true
        val context = session.messages.filter { it.id in hint.hintContextIds && it.isConversation }
        return context.size == hint.hintContextIds.size && contextDigest(context) == hint.hintContextDigest
    }
    fun reveal(session: ChatSession, id: String): ChatSession = session.copy(messages = session.messages.map {
        if (it.id == id && it.hintKind == HintKind.SCAFFOLD && hintCurrent(session, it))
            it.copy(revealedLayers = (it.revealedLayers + 1).coerceAtMost(it.hintLayers.size)) else it
    })
    fun beginPractice(session: ChatSession, hintId: String): SpeakingPractice {
        val hint = requireNotNull(session.messages.firstOrNull { it.id == hintId && it.hintKind == HintKind.OPTIMIZE })
        val source = requireNotNull(session.messages.firstOrNull { it.id == hint.sourceMessageId && it.role == "user" })
        require(hintCurrent(session, hint)) { "原消息已变化，请重新优化后练习。" }
        require(hint.practiceReference.isNotBlank()) { "这条历史优化没有独立参考句，请重新优化。" }
        return SpeakingPractice(sourceMessageId = source.id, sourceRevision = source.revision, original = source.text,
            hintId = hint.id, hintRevision = hint.revision, reference = hint.practiceReference,
            config = hint.generationConfig ?: session.config)
    }
    fun sentMessage(practice: SpeakingPractice): ChatMessage {
        require(practice.transcript.isNotBlank() && practice.sentMessageId.isBlank()) { "请先确认转写，且每段练习只发送一次。" }
        return ChatMessage(role = "user", text = practice.transcript, fromVoice = practice.audioFileName.isNotBlank(),
            audioFileName = practice.audioFileName, audioDurationMs = practice.audioDurationMs,
            transcriptState = if (practice.audioFileName.isBlank()) TranscriptState.NONE else TranscriptState.READY)
    }
    fun sendPractice(session: ChatSession, id: String): ChatSession {
        require(session.scenario?.phase != ScenarioPhase.ENDED) { "请先继续为自由对话，再发送练习。" }
        require(session.messages.lastOrNull { it.isConversation }?.role != "user") { "请先处理上一条消息。" }
        val practice = requireNotNull(session.speakingPractices.firstOrNull { it.id == id })
        val message = sentMessage(practice)
        return session.copy(messages = session.messages + message, speakingPractices = session.speakingPractices.map {
            if (it.id == id) it.copy(sentMessageId = message.id) else it
        }, updatedAt = System.currentTimeMillis())
    }
    fun recordPractice(session: ChatSession, id: String, filename: String, duration: Long): Pair<ChatSession, SpeakingPractice> {
        val original = requireNotNull(session.speakingPractices.firstOrNull { it.id == id && it.sentMessageId.isBlank() })
        val next = original.copy(id = if (original.audioFileName.isBlank()) original.id else newId(),
            audioFileName = filename, audioDurationMs = duration, transcript = "", transcriptState = TranscriptState.PENDING,
            createdAt = System.currentTimeMillis())
        val practices = if (next.id == original.id) session.speakingPractices.map { if (it.id == id) next else it }
            else session.speakingPractices + next
        return session.copy(speakingPractices = practices) to next
    }
}

object LearningFeatureJson {
    fun practice(p: SpeakingPractice) = JSONObject().apply {
        put("id", p.id); put("sourceMessageId", p.sourceMessageId); put("sourceRevision", p.sourceRevision)
        put("original", p.original); put("hintId", p.hintId); put("hintRevision", p.hintRevision); put("reference", p.reference)
        put("config", ConversationConfigJson.write(p.config)); put("audioFileName", p.audioFileName); put("audioDurationMs", p.audioDurationMs)
        put("transcript", p.transcript); put("transcriptState", p.transcriptState.name); put("sentMessageId", p.sentMessageId); put("createdAt", p.createdAt)
    }
    fun readPractice(j: JSONObject) = SpeakingPractice(j.getString("id"), j.getString("sourceMessageId"), j.optInt("sourceRevision"),
        j.getString("original"), j.getString("hintId"), j.optInt("hintRevision"), j.getString("reference"),
        ConversationConfigJson.read(j.optJSONObject("config")), j.optString("audioFileName"), j.optLong("audioDurationMs"),
        j.optString("transcript"), TranscriptState.entries.firstOrNull { it.name == j.optString("transcriptState") } ?: TranscriptState.NONE,
        j.optString("sentMessageId"), j.optLong("createdAt"))
}
