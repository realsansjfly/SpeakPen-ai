package com.speakpen.app.data

import org.json.JSONObject

enum class ExpressionDifficulty(val label: String, val description: String) {
    SIMPLE("简单", "常用词与短句优先，参考 A1–A2"),
    DAILY("日常", "自然交流与常见搭配，参考 B1"),
    DEEP("深入", "更细致的观点与表达，参考 B2"),
}

enum class ReplyLength(val label: String, val maxTokens: Int) {
    SHORT("简短", 120), STANDARD("适中", 240), EXTENDED("较充分", 400),
}

data class AdvancedSettings(val temperature: Double = 0.7, val replyLength: ReplyLength = ReplyLength.STANDARD) {
    init { require(temperature.isFinite() && temperature in 0.0..1.0) { "温度须在 0–1 之间。" } }
}

data class ConversationConfig(
    val difficulty: ExpressionDifficulty = ExpressionDifficulty.DAILY,
    val advanced: AdvancedSettings = AdvancedSettings(),
)

object ConversationConfigJson {
    fun write(config: ConversationConfig): JSONObject = JSONObject().apply {
        put("version", 1); put("difficulty", config.difficulty.name)
        put("temperature", config.advanced.temperature); put("replyLength", config.advanced.replyLength.name)
    }

    fun read(json: JSONObject?): ConversationConfig {
        if (json == null) return ConversationConfig()
        val temperature = json.optDouble("temperature", 0.7).takeIf { it.isFinite() && it in 0.0..1.0 } ?: 0.7
        return ConversationConfig(
            difficulty = ExpressionDifficulty.entries.firstOrNull { it.name == json.optString("difficulty") } ?: ExpressionDifficulty.DAILY,
            advanced = AdvancedSettings(temperature, ReplyLength.entries.firstOrNull { it.name == json.optString("replyLength") } ?: ReplyLength.STANDARD),
        )
    }
}

/** A config edit affects future requests; it must not relabel a reply already being generated. */
data class GenerationSnapshot(
    val sessionId: String,
    val config: ConversationConfig,
    val configRevision: Int,
    private val messages: List<ChatMessage>,
) {
    fun matches(session: ChatSession): Boolean = session.id == sessionId &&
        session.messages.map { Triple(it.id, it.revision, it.text) } == messages.map { Triple(it.id, it.revision, it.text) }

    fun canApply(session: ChatSession, activeSessionId: String, requestGeneration: Int, currentGeneration: Int): Boolean =
        requestGeneration == currentGeneration && activeSessionId == sessionId && matches(session)

    companion object {
        fun capture(session: ChatSession) = GenerationSnapshot(session.id, session.config, session.configRevision, session.messages.toList())
    }
}
