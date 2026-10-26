package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File

class ConversationConfigTest {
    private val simple = ConversationConfig(ExpressionDifficulty.SIMPLE, AdvancedSettings(0.2, ReplyLength.SHORT))
    private val deep = ConversationConfig(ExpressionDifficulty.DEEP, AdvancedSettings(1.0, ReplyLength.EXTENDED))
    private fun session() = ChatSession(id = "s", config = simple, configRevision = 3,
        messages = listOf(ChatMessage(id = "u", role = "user", text = "I go there yesterday.", audioFileName = "original.m4a")))

    @Test fun existingDataKeepsReviewTargetsRecordingsAndOriginalWording() {
        val raw = """{"sessions":[{"id":"s","mode":"REVIEW","targetIds":["target"],"messages":[{"id":"u","role":"user","text":"I go there yesterday.","audioFileName":"original.m4a"}]}],"library":[{"id":"target","content":"look forward to"}],"currentSessionId":"s"}"""
        val data = DataJson.read(raw)
        assertEquals(ConversationConfig(), data.sessions.single().config)
        assertEquals(ConversationMode.REVIEW, data.sessions.single().mode)
        assertEquals(listOf("target"), data.sessions.single().targetIds)
        assertEquals("original.m4a", data.sessions.single().messages.single().audioFileName)
        assertEquals("I go there yesterday.", data.sessions.single().messages.single().text)
        assertEquals("look forward to", data.library.single().content)
        assertEquals(data, DataJson.read(DataJson.write(data)))
    }

    @Test fun sessionConfigAndGeneratedConfigPersistIndependently() {
        val session = session().copy(config = deep, configRevision = 4, messages = session().messages +
            ChatMessage(role = "assistant", text = "How was it?", generationConfig = simple, configRevision = 3) +
            ChatMessage(role = "hint", text = "Private hint", hintKind = HintKind.TOPIC, generationConfig = simple, configRevision = 3))
        val data = SavedData(listOf(session), currentSessionId = session.id)
        assertEquals(data, DataJson.read(DataJson.write(data)))
        assertFalse(CoachPrompts.history(session).toString().contains("Private hint"))
    }

    @Test fun invalidStoredSettingsFallBackWithoutDroppingLearningData() {
        for (temperature in listOf("null", "-1", "2", "\"broken\"")) {
            val config = ConversationConfigJson.read(JSONObject("""{"temperature":$temperature,"difficulty":"UNKNOWN","replyLength":"INVALID"}"""))
            assertEquals(ConversationConfig(), config)
        }
        assertEquals(deep, ConversationConfigJson.read(ConversationConfigJson.write(deep)))
    }

    @Test fun newSessionUsesDefaultsAndLeavesExistingSessionUntouched() {
        val old = session()
        val data = ConversationSessions.start(SavedData(listOf(old), currentSessionId = old.id), ConversationMode.FREE, config = deep)
        assertEquals(old, data.sessions.first { it.id == old.id })
        assertEquals(deep, data.sessions.first { it.id == data.currentSessionId }.config)
        val again = ConversationSessions.start(data, ConversationMode.FREE, config = simple)
        assertEquals(2, again.sessions.size)
        assertEquals(data.currentSessionId, again.currentSessionId)
        assertEquals(listOf(old), ConversationSessions.history(again.sessions))
    }

    @Test fun restoringAdvancedSettingsRetainsDifficultyAndData() {
        val session = session().copy(config = deep)
        val reset = session.copy(config = session.config.copy(advanced = AdvancedSettings()))
        assertEquals(ExpressionDifficulty.DEEP, reset.config.difficulty)
        assertEquals(AdvancedSettings(), reset.config.advanced)
        assertEquals(session.messages, reset.messages)
    }

    @Test fun changedConfigDoesNotInvalidateOrRelabelAnInflightRequest() {
        val session = session()
        val snapshot = GenerationSnapshot.capture(session)
        assertTrue(snapshot.canApply(session.copy(config = deep, configRevision = 4), session.id, 5, 5))
        assertEquals(simple, snapshot.config)
        assertEquals(3, snapshot.configRevision)
    }

    @Test fun cancellationSessionSwitchEditsAndNewMessagesRejectOldResults() {
        val session = session()
        val snapshot = GenerationSnapshot.capture(session)
        assertFalse(snapshot.canApply(session, session.id, 5, 6))
        assertFalse(snapshot.canApply(session, "another-session", 5, 5))
        assertFalse(snapshot.matches(session.copy(id = "another-session")))
        assertFalse(snapshot.matches(session.copy(messages = session.messages.map { it.copy(text = "Changed") })))
        assertFalse(snapshot.matches(session.copy(messages = session.messages.map { it.copy(revision = 1) })))
        assertFalse(snapshot.matches(session.copy(messages = session.messages + ChatMessage(role = "assistant", text = "New turn"))))
        assertTrue(snapshot.matches(session.copy(messages = session.messages.map { it.copy(markedStuck = true) })))
    }

    @Test fun everyDifficultyReachesChatOptimizationAndTopicWhilePreservingHelpAndReview() {
        for ((difficulty, cefr) in listOf(ExpressionDifficulty.SIMPLE to "A1–A2", ExpressionDifficulty.DAILY to "B1", ExpressionDifficulty.DEEP to "B2")) {
            val config = ConversationConfig(difficulty)
            val source = session().copy(config = config)
            val chat = CoachPrompts.system(ConversationMode.REVIEW, listOf(ReviewItem(content = "Would you mind")), config)
            assertTrue(chat.contains(cefr))
            assertTrue(chat.contains("Would you mind"))
            assertTrue(chat.contains("[CHINESE HELP]"))
            assertTrue(chat.contains("Do not give unsolicited grammar corrections"))
            assertTrue(LearningPrompts.optimize(source, source.messages.first())[0].second.contains(cefr))
            assertTrue(LearningPrompts.topic(source.messages, config)[0].second.contains(cefr))
            assertTrue(LearningPrompts.extract(source.messages.first(), config)[0].second.contains(cefr))
        }
        // Synthetic requests built by production code, reused by the explicit paid smoke command.
        val fixtures = JSONArray()
        fun fixture(name: String, prompts: List<Pair<String, String>>, config: ConversationConfig?, purpose: String = "chat") {
            fixtures.put(JSONObject().put("name", name).put("body", JSONObject(ApiProtocol.chatBody("managed", prompts, config?.advanced)).put("purpose", purpose)))
        }
        for (difficulty in ExpressionDifficulty.entries) {
            val config = ConversationConfig(difficulty)
            fixture(difficulty.name, listOf("system" to CoachPrompts.system(ConversationMode.FREE, emptyList(), config)) + CoachPrompts.history(session()), config)
        }
        fixture("temperature-zero-short", listOf("system" to CoachPrompts.system(ConversationMode.FREE, emptyList(), simple.copy(advanced = simple.advanced.copy(temperature = 0.0)))) + CoachPrompts.history(session()), simple.copy(advanced = simple.advanced.copy(temperature = 0.0)))
        fixture("temperature-one-extended", listOf("system" to CoachPrompts.system(ConversationMode.FREE, emptyList(), deep)) + CoachPrompts.history(session()), deep)
        fixture("optimize-simple", LearningPrompts.optimize(session(), session().messages.first()), null, "learning")
        fixture("topic-simple", LearningPrompts.topic(session().messages, simple), null, "learning")
        File("build/outputs/conversation-config-live-requests.json").apply { parentFile?.mkdirs(); writeText(fixtures.toString(2)) }
    }

    private fun api(handler: (JSONObject) -> Unit): AiClient = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
        handler(JSONObject(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()))
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .header("Content-Type", "application/json")
            .body("""{"choices":[{"message":{"content":"How was it?"}}]}""".toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun streamJsonFallbackAndRegenerationActuallySendTheSelectedAdvancedSettings() = runBlocking {
        var calls = 0
        val client = api { body ->
            assertEquals(0.2, body.getDouble("temperature"), 0.0)
            assertEquals("short", body.getString("reply_length"))
            assertEquals("I go there yesterday.", body.getJSONArray("messages").getJSONObject(0).getString("content"))
            calls++
        }
        val config = ApiConfig("https://service.example/v1", model = "managed")
        val messages = listOf("user" to "I go there yesterday.")
        assertEquals("How was it?", client.streamChat(config, messages, simple.advanced) {})
        assertEquals("How was it?", client.chat(config, messages, advanced = simple.advanced))
        assertEquals(2, calls)
    }

    @Test fun learningAndExtractionNeverSendConversationAdvancedSettings() = runBlocking {
        var calls = 0
        val client = api { body ->
            assertFalse(body.has("temperature")); assertFalse(body.has("reply_length")); calls++
        }
        val config = ApiConfig("https://service.example/v1", model = "managed")
        client.learning(config, listOf("user" to "Test"))
        client.chat(config, listOf("user" to "Test"), "summary", deep.advanced)
        assertEquals(2, calls)
    }

    @Test fun serviceCapabilitiesDistinguishTheRunningLegacyBackendFromUpdatedCode() = runBlocking {
        for (supported in listOf(false, true)) {
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                val body = JSONObject().put("ready", true).apply { if (supported) put("conversationControlsVersion", 1) }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(body.toString().toResponseBody("application/json".toMediaType())).build()
            }.build()
            val status = AiClient(transport).statusInfo(ApiConfig("https://service.example/v1", model = "managed"))
            assertEquals(supported, status.conversationControlsSupported)
            assertEquals(!supported, status.label.contains("需更新"))
        }
    }

    @Test fun delayedNetworkResultRetainsOriginalConfigAndCannotApplyAfterCancellation() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val snapshot = GenerationSnapshot.capture(session())
        val client = api { body ->
            assertEquals("short", body.getString("reply_length"))
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            val result = async {
                client.streamChat(ApiConfig("https://service.example/v1", model = "managed"), CoachPrompts.history(session()), snapshot.config.advanced) {}
            }
            withContext(Dispatchers.IO) { check(started.await(5, TimeUnit.SECONDS)) }
            val edited = session().copy(config = deep, configRevision = 4)
            release.countDown()
            assertEquals("How was it?", result.await())
            assertTrue(snapshot.canApply(edited, edited.id, 1, 1))
            assertFalse(snapshot.canApply(edited, edited.id, 1, 2))
            assertFalse(snapshot.canApply(edited, "other", 1, 1))
        } finally { release.countDown() }
    }
}
