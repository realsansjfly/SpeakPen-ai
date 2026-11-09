package com.speakpen.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class RetrievalScope(val label: String) { TURN("当前回合"), RECENT("最近对话") }
enum class RetrievalStrength(val label: String, val words: Int) { RESTRAINED("克制", 1), BALANCED("均衡", 2), ACTIVE("积极", 3) }
data class VocabularyEnhancement(val enabled: Boolean = true, val scope: RetrievalScope = RetrievalScope.RECENT,
    val strength: RetrievalStrength = RetrievalStrength.BALANCED, val count: Int = 8) {
    init { require(count in listOf(4, 8, 12)) }
}
enum class TopicPurpose(val label: String) { ANSWER("回答"), FOLLOW_UP("追问"), RELATED("泛话题相关") }
enum class TopicDifficulty(val label: String) { DEFAULT("默认"), ADVANCED("高级表达"), IDIOMATIC("地道表达") }
data class TopicOptions(val purpose: TopicPurpose = TopicPurpose.ANSWER, val count: Int = 5,
    val difficulty: TopicDifficulty = TopicDifficulty.DEFAULT) { init { require(count in 4..10) } }

/** Never fall back to an earlier complete round when the latest user is unanswered. */
object TopicRound {
    fun latest(messages: List<ChatMessage>): List<ChatMessage> {
        val actual = messages.filter { it.isConversation }
        val userIndex = actual.indexOfLast { it.role == "user" }
        require(userIndex >= 0) { "请先说一句并等待 AI 回答，再获取话题语料。" }
        val user = actual[userIndex]
        val answer = actual.drop(userIndex + 1).firstOrNull { it.role == "assistant" && !it.scenarioOpening }
        require(user.text.isNotBlank() && answer != null && answer.text.isNotBlank()) { "最新一轮尚未完成，请先完成转写或重试 AI 回复。" }
        return listOf(user, answer)
    }
}

data class VoiceSegment(val id: String = newId(), val file: String, val durationMs: Long,
    val kind: InputKind = InputKind.ENGLISH, val text: String = "", val failed: Boolean = false)
object VoiceQueue {
    const val MAX_SEGMENTS = 6
    const val MAX_MS = 180000L
    fun append(queue: List<VoiceSegment>, segment: VoiceSegment): List<VoiceSegment> {
        require(queue.size < MAX_SEGMENTS) { "最多 6 段，请先提交或删除片段。" }
        require(segment.durationMs > 0 && queue.sumOf { it.durationMs } + segment.durationMs <= MAX_MS) { "总时长最多 3 分钟，请缩短这一段后重录。已有片段已保留。" }
        return queue + segment
    }
    fun combined(queue: List<VoiceSegment>): String {
        require(queue.isNotEmpty() && queue.all { it.text.isNotBlank() && !it.failed })
        return queue.joinToString("\n") { it.text.trim() }
    }
    fun json(queue: List<VoiceSegment>) = JSONArray().apply { queue.forEach { s -> put(JSONObject()
        .put("id", s.id).put("file", s.file).put("durationMs", s.durationMs).put("kind", s.kind.name).put("text", s.text).put("failed", s.failed)) } }
    fun read(array: JSONArray?) = DataJson.objects(array).map { VoiceSegment(it.getString("id"), it.getString("file"),
        it.getLong("durationMs"), InputKind.entries.firstOrNull { k -> k.name == it.optString("kind") } ?: InputKind.ENGLISH,
        it.optString("text"), it.optBoolean("failed")) }
}

enum class LessonStage(val label: String) { LISTEN("听示范"), REPEAT("分句跟读"), SUBSTITUTE("关键词替换"), WARM_UP("角色热身"), READY("正式对话") }
data class LessonProgress(val template: ScenarioTemplate, val stage: LessonStage = LessonStage.LISTEN, val chunk: Int = 0,
    val transcript: String = "", val feedback: String = "", val audio: String = "", val durationMs: Long = 0)
object LessonJson {
    fun write(p: LessonProgress) = JSONObject().put("template", ScenarioJson.template(p.template)).put("stage", p.stage.name)
        .put("chunk", p.chunk).put("transcript", p.transcript).put("feedback", p.feedback).put("audio", p.audio).put("durationMs", p.durationMs)
    fun read(j: JSONObject) = LessonProgress(ScenarioJson.readTemplate(j.getJSONObject("template")),
        LessonStage.entries.firstOrNull { it.name == j.optString("stage") } ?: LessonStage.LISTEN, j.optInt("chunk").coerceAtLeast(0),
        j.optString("transcript"), j.optString("feedback"), j.optString("audio"), j.optLong("durationMs"))
}
