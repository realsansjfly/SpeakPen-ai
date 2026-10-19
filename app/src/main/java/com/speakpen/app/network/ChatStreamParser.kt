package com.speakpen.app.network

import org.json.JSONObject

/** Incremental OpenAI-compatible SSE parser. Reasoning payloads never appear as learner replies. */
class ChatStreamParser {
    private val text = StringBuilder()
    var finished: Boolean = false
        private set
    fun line(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") { finished = true; return null }
        if (payload.isBlank()) return null
        val json = JSONObject(payload)
        require(!json.has("error")) { "AI 回复中断，请重试。" }
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return null
        if (!choice.isNull("finish_reason") && choice.optString("finish_reason").isNotBlank()) finished = true
        val piece = choice.optJSONObject("delta")?.optString("content", "").orEmpty()
        if (piece.isEmpty()) return null
        text.append(piece)
        return text.toString()
    }
    fun result(): String {
        check(finished) { "AI 回复中断，已保留部分内容，可以重试。" }
        return text.toString().trim().also { require(it.isNotBlank()) { "AI 没有返回有效回复，请重试。" } }
    }
}
