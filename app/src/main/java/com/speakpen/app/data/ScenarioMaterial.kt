package com.speakpen.app.data

import org.json.JSONObject

data class ScenarioMaterial(val source: String, val chunks: List<List<String>>, val dialogue: List<String> = chunks.flatten()) {
    fun chunk(index: Int): List<String> = chunks[index.coerceIn(0, chunks.lastIndex)]
    // Bound each audio request without truncating the complete dialogue (gateway limit: 2,000 chars).
    fun playbackParts(): List<String> = dialogue.flatMap { it.substringAfter(": ").chunked(1800) }
}
object ScenarioMaterials {
    fun read(raw: String, template: ScenarioTemplate): ScenarioMaterial {
        val scenes = JSONObject(raw).getJSONObject("scenes")
        val key = template.id.removePrefix("builtin:")
        val scene = scenes.optJSONObject(key)
        if (scene != null) {
            val lines = scene.getJSONArray("dialogue")
            // Keep complete source text in the asset; practice only meaningful short excerpts.
            val ranges = when (key) {
                "cafe" -> listOf(0..1, 4..5, 6..8)
                "shopping" -> listOf(4..5, 7..9, 11..14)
                "directions" -> listOf(0..3, 4..5, 6..7)
                "hotel" -> listOf(0..2, 8..8, 12..12, 19..20, 22..23, 26..27)
                "campus" -> listOf(1..5, 6..7, 12..14, 17..18)
                else -> listOf(0 until lines.length())
            }
            return ScenarioMaterial("用户提供的《英语口语场景对话示例》", ranges.flatMap { range ->
                shortChunks(range.filter { it < lines.length() }.map(lines::getString))
            }, (0 until lines.length()).map(lines::getString))
        }
        // Sixth preset and custom templates are app-authored; never attributed to the document.
        val lines = if (key == "interview") listOf("Interviewer: Could you tell me about your background?",
            "Candidate: I enjoy solving problems and working with others.", "Interviewer: Could you give me an example?",
            "Candidate: I helped my team plan a project and meet our deadline.")
        else listOf("${template.aiRole}: ${template.opening.ifBlank { "Hello. How can I help you today?" }}",
            "${template.userRole}: Could you help me, please?")
        return ScenarioMaterial("应用自编示范对话", shortChunks(lines), lines)
    }
    internal fun shortChunks(lines: List<String>): List<List<String>> = lines.flatMap { line ->
        val role = line.substringBefore(": ", "")
        line.substringAfter(": ").split(Regex("(?<=[.!?])\\s+(?=[A-Z])")).filter { it.isNotBlank() }
            .map { if (role.isBlank()) it else "$role: $it" }
    }.chunked(3).ifEmpty { listOf(listOf("Hello. How can I help you today?")) }
    fun guidance(stage: LessonStage): String = when(stage) {
        LessonStage.LISTEN -> "先听这一小段示范，再进入跟读。"
        LessonStage.REPEAT -> "跟读这一片段，尽量保留原句的意思和关键表达。"
        LessonStage.SUBSTITUTE -> "替换一个关键词，例如饮品、尺码、地点或姓名，再说一次。"
        LessonStage.WARM_UP -> "根据你的角色，用自己的话回应示范中的对方。"
        LessonStage.READY -> "学习已完成，可以开始正式对话。"
    }
}
