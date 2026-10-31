package com.speakpen.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class RecapKind(val label: String) { LATEST("最新复盘"), SCENARIO("场景复盘") }
data class RecapDocument(
    val id: String = newId(), val kind: RecapKind, val markdown: String,
    val sourceMessages: List<ChatMessage>, val config: ConversationConfig,
    val practiceId: String = "", val createdAt: Long = System.currentTimeMillis(),
)

/** Captures the full requested transcript. Reject oversize input instead of silently dropping history. */
data class RecapSnapshot(val sessionId: String, val kind: RecapKind, val messages: List<ChatMessage>,
    val config: ConversationConfig, val practiceId: String, val scenario: ScenarioPractice?, val previousId: String?) {
    fun matches(session: ChatSession): Boolean {
        if (session.id != sessionId) return false
        val current = if (kind == RecapKind.SCENARIO) session.scenario?.ending?.messages.orEmpty() else session.messages
        val previous = if (kind == RecapKind.SCENARIO) session.fixedRecap else session.latestRecap
        return previous?.id == previousId && LearningFeatures.contextDigest(current) == LearningFeatures.contextDigest(messages) &&
            (kind != RecapKind.SCENARIO || session.scenario?.practiceId == practiceId)
    }
    fun complete(session: ChatSession, markdown: String): ChatSession {
        check(matches(session)) { "对话已变化，请重新生成复盘；原文档已保留。" }
        require(markdown.isNotBlank())
        check(kind != RecapKind.SCENARIO || session.fixedRecap == null) { "固定场景复盘已保存。" }
        val document = RecapDocument(kind = kind, markdown = markdown, sourceMessages = messages, config = config, practiceId = practiceId)
        return if (kind == RecapKind.SCENARIO) session.copy(fixedRecap = document) else session.copy(latestRecap = document)
    }
    companion object {
        fun capture(session: ChatSession, kind: RecapKind): RecapSnapshot {
            val messages = (if (kind == RecapKind.SCENARIO) {
                check(session.fixedRecap == null) { "固定场景复盘已保存，可直接查看。" }
                requireNotNull(session.scenario?.ending) { "场景结束后才能生成固定复盘。" }.messages
            } else session.messages).filter { it.isConversation && it.text.isNotBlank() }.toList()
            require(messages.any { it.role == "user" }) { "先进行一轮对话，再生成复盘。" }
            // Long transcripts are split into data blocks within the service's 70,000-char total budget.
            require(JSONArray().apply { messages.forEach { put(JSONObject().put("id", it.id).put("role", it.role).put("text", it.text)) } }.toString().length <= 60000) {
                "这段对话超出当前复盘容量，原文档已保留。"
            }
            return RecapSnapshot(session.id, kind, messages, session.config, session.scenario?.practiceId.orEmpty(),
                session.scenario, (if (kind == RecapKind.SCENARIO) session.fixedRecap else session.latestRecap)?.id)
        }
    }
}

object RecapJson {
    fun write(d: RecapDocument) = JSONObject().apply {
        put("id", d.id); put("kind", d.kind.name); put("markdown", d.markdown); put("createdAt", d.createdAt)
        put("practiceId", d.practiceId); put("config", ConversationConfigJson.write(d.config))
        put("sourceMessages", JSONArray().apply { d.sourceMessages.forEach { put(DataJson.messageToJson(it)) } })
    }
    fun read(j: JSONObject) = RecapDocument(j.getString("id"), RecapKind.valueOf(j.getString("kind")), j.getString("markdown"),
        DataJson.objects(j.getJSONArray("sourceMessages")).map(DataJson::messageFromJson),
        ConversationConfigJson.read(j.optJSONObject("config")), j.optString("practiceId"), j.getLong("createdAt"))
}
