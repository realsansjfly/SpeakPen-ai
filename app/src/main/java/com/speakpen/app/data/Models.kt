package com.speakpen.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
enum class ConversationMode(val label: String) { FREE("自由对话"), REVIEW("复习对话") }
enum class InputKind { ENGLISH, HELP }
enum class SpeechMode(val label: String) { SYSTEM("系统语音识别"), CLOUD("云端录音转写") }
enum class HintKind(val label: String) { OPTIMIZE("优化表达"), TOPIC("话题语料") }
enum class ReviewKind(val label: String) { WORD("词汇"), PHRASE("表达"), TOPIC("话题") }

data class ApiConfig(val baseUrl: String = "https://api.openai.com/v1", val apiKey: String = "", val model: String = "")
data class AppSettings(
    val autoSpeak: Boolean = true,
    val demoMode: Boolean = false,
    val voiceReplies: Boolean = true,
    val serviceUrl: String = "", val serviceAccessToken: String = "",
    val conversationDefaults: ConversationConfig = ConversationConfig(),
)
data class ChatMessage(
    val id: String = newId(), val role: String, val text: String,
    val inputKind: InputKind = InputKind.ENGLISH, val fromVoice: Boolean = false,
    val markedStuck: Boolean = false, val createdAt: Long = System.currentTimeMillis(),
    val audioFileName: String = "", val audioDurationMs: Long = 0L,
    val transcriptState: TranscriptState = TranscriptState.NONE,
    val hintKind: HintKind? = null, val sourceMessageId: String = "",
    val hintContextIds: List<String> = emptyList(), val revision: Int = 0,
    val generationConfig: ConversationConfig? = null, val configRevision: Int = 0,
    val hintCollapsed: Boolean = false,
) {
    val isHint: Boolean get() = role == "hint"
    val isConversation: Boolean get() = role == "user" || role == "assistant"
}
data class ChatSession(
    val id: String = newId(), val title: String = "新的练习", val mode: ConversationMode = ConversationMode.FREE,
    val targetIds: List<String> = emptyList(), val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val config: ConversationConfig = ConversationConfig(), val configRevision: Int = 0,
)
data class ReviewItem(
    val id: String = newId(), val kind: ReviewKind = ReviewKind.PHRASE,
    val content: String, val meaning: String = "", val example: String = "", val reason: String = "",
    val sourceSessionId: String = "", val sourceNote: String = "手动添加",
    val stage: Int = 0, val nextReviewAt: Long = 0L, val createdAt: Long = System.currentTimeMillis(),
)
data class ReviewCandidate(val item: ReviewItem, val selected: Boolean = true)
data class SavedData(val sessions: List<ChatSession> = emptyList(), val library: List<ReviewItem> = emptyList(), val currentSessionId: String = "")

object ReviewSchedule {
    private val days = listOf(1L, 3L, 7L, 14L, 30L)
    fun update(item: ReviewItem, remembered: Boolean, now: Long = System.currentTimeMillis()): ReviewItem {
        val nextStage = if (remembered) (item.stage + 1).coerceAtMost(days.size) else 0
        val delay = if (remembered) days[(nextStage - 1).coerceAtLeast(0)] * 86_400_000L else 0L
        return item.copy(stage = nextStage, nextReviewAt = now + delay)
    }
}

object DataJson {
    fun write(data: SavedData): String = JSONObject().apply {
        put("version", 2); put("currentSessionId", data.currentSessionId)
        put("sessions", JSONArray().apply { data.sessions.forEach { put(sessionToJson(it)) } })
        put("library", JSONArray().apply { data.library.forEach { put(itemToJson(it)) } })
    }.toString()
    fun read(raw: String): SavedData {
        val root = JSONObject(raw)
        return SavedData(
            objects(root.optJSONArray("sessions")).map(::sessionFromJson),
            objects(root.optJSONArray("library")).map(::itemFromJson), root.optString("currentSessionId"),
        )
    }
    fun itemToJson(item: ReviewItem): JSONObject = JSONObject().apply {
        put("id", item.id); put("kind", item.kind.name); put("content", item.content)
        put("meaning", item.meaning); put("example", item.example); put("reason", item.reason)
        put("sourceSessionId", item.sourceSessionId); put("sourceNote", item.sourceNote)
        put("stage", item.stage); put("nextReviewAt", item.nextReviewAt); put("createdAt", item.createdAt)
    }
    fun itemFromJson(json: JSONObject): ReviewItem = ReviewItem(
        id = json.optString("id").ifBlank { newId() }, kind = enumOr(json.optString("kind"), ReviewKind.PHRASE),
        content = json.getString("content"), meaning = json.optString("meaning"), example = json.optString("example"),
        reason = json.optString("reason"), sourceSessionId = json.optString("sourceSessionId"),
        sourceNote = json.optString("sourceNote", "手动添加"), stage = json.optInt("stage").coerceIn(0, 5),
        nextReviewAt = json.optLong("nextReviewAt"), createdAt = json.optLong("createdAt", System.currentTimeMillis()),
    )
    private fun sessionToJson(session: ChatSession): JSONObject = JSONObject().apply {
        put("id", session.id); put("title", session.title); put("mode", session.mode.name)
        put("config", ConversationConfigJson.write(session.config)); put("configRevision", session.configRevision)
        put("targetIds", JSONArray(session.targetIds)); put("createdAt", session.createdAt); put("updatedAt", session.updatedAt)
        put("messages", JSONArray().apply { session.messages.forEach { message -> put(JSONObject().apply {
            put("id", message.id); put("role", message.role); put("text", message.text)
            put("inputKind", message.inputKind.name); put("fromVoice", message.fromVoice)
            put("markedStuck", message.markedStuck); put("createdAt", message.createdAt)
            put("hintKind", message.hintKind?.name ?: ""); put("sourceMessageId", message.sourceMessageId)
            put("hintContextIds", JSONArray(message.hintContextIds)); put("revision", message.revision)
            message.generationConfig?.let { put("generationConfig", ConversationConfigJson.write(it)) }
            put("configRevision", message.configRevision)
            put("hintCollapsed", message.hintCollapsed)
            put("audioFileName", message.audioFileName); put("audioDurationMs", message.audioDurationMs); put("transcriptState", message.transcriptState.name)
        }) } })
    }
    private fun sessionFromJson(json: JSONObject): ChatSession = ChatSession(
        id = json.getString("id"), title = json.optString("title", "练习"), mode = enumOr(json.optString("mode"), ConversationMode.FREE),
        config = ConversationConfigJson.read(json.optJSONObject("config")), configRevision = json.optInt("configRevision").coerceAtLeast(0),
        targetIds = json.optJSONArray("targetIds")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
        messages = objects(json.optJSONArray("messages")).map { message -> ChatMessage(
            id = message.getString("id"), role = message.getString("role"), text = message.getString("text"),
            inputKind = enumOr(message.optString("inputKind"), InputKind.ENGLISH), fromVoice = message.optBoolean("fromVoice"),
            markedStuck = message.optBoolean("markedStuck"), createdAt = message.optLong("createdAt"),
            audioFileName = message.optString("audioFileName"), audioDurationMs = message.optLong("audioDurationMs"),
            transcriptState = enumOr(message.optString("transcriptState"), TranscriptState.NONE),
            hintKind = HintKind.entries.firstOrNull { it.name == message.optString("hintKind") },
            sourceMessageId = message.optString("sourceMessageId"), revision = message.optInt("revision"),
            generationConfig = message.optJSONObject("generationConfig")?.let(ConversationConfigJson::read),
            configRevision = message.optInt("configRevision").coerceAtLeast(0),
            hintCollapsed = message.optBoolean("hintCollapsed"),
            hintContextIds = message.optJSONArray("hintContextIds")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        ) }, createdAt = json.optLong("createdAt"), updatedAt = json.optLong("updatedAt"),
    )
    fun objects(array: JSONArray?): List<JSONObject> = array?.let { (0 until it.length()).map(it::getJSONObject) } ?: emptyList()
    private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}



