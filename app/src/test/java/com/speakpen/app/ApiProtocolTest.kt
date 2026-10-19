package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiProtocolTest {
    @Test fun baseAndCompleteEndpointsDoNotDuplicatePaths() {
        assertEquals("https://example.org/v1/chat/completions", ApiProtocol.endpoint(" https://example.org/v1/ ", "chat/completions"))
        assertEquals("https://example.org/v1/chat/completions", ApiProtocol.endpoint("https://example.org/v1/chat/completions", "chat/completions"))
        assertEquals("https://example.org/custom/audio/transcriptions", ApiProtocol.endpoint("https://example.org/custom", "audio/transcriptions"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCleartextApiCredentials() { ApiProtocol.endpoint("http://example.org/v1", "chat/completions") }
    @Test(expected = IllegalArgumentException::class) fun rejectsQueryCredentials() { ApiProtocol.endpoint("https://example.org/v1?api_key=secret", "chat/completions") }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmbeddedPassword() { ApiProtocol.endpoint("https://name:secret@example.org/v1", "chat/completions") }
    @Test(expected = IllegalArgumentException::class) fun refusesBlankModel() { ApiProtocol.requireModel(ApiConfig(model = "  ")) }
    @Test fun escapesTranscriptWithoutChangingLearnerEnglish() {
        val text = "I go yesterday.\nHe said \"hello\"."
        val parsed = JSONObject(ApiProtocol.chatBody(" model-id ", listOf("user" to text)))
        assertEquals("model-id", parsed.getString("model"))
        assertEquals(text, parsed.getJSONArray("messages").getJSONObject(0).getString("content"))
    }
    @Test fun readsStringAndTypedTextParts() {
        assertEquals("Hello!", ApiProtocol.chatText("""{"choices":[{"message":{"content":" Hello! "}}]}"""))
        assertEquals("Hello\nworld", ApiProtocol.chatText("""{"choices":[{"message":{"content":[{"type":"text","text":"Hello"},{"type":"text","text":"world"}]}}]}"""))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyReplyIsNotReportedAsSuccess() { ApiProtocol.chatText("""{"choices":[{"message":{"content":null}}]}""") }
    @Test fun transcriptionDoesNotSilentlyCorrectLearnerGrammar() {
        assertEquals("I go there yesterday.", ApiProtocol.transcriptionText("""{"text":" I go there yesterday. "}"""))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyTranscriptionAllowsRetry() { ApiProtocol.transcriptionText("""{"text":" "}""") }
    @Test fun summaryEvidenceComesFromActualUserMarkers() {
        val session = ChatSession(id = "s", messages = listOf(
            ChatMessage(id = "marked", role = "user", text = "I don't know how to refuse.", markedStuck = true),
            ChatMessage(id = "help", role = "user", text = "我想礼貌拒绝", inputKind = InputKind.HELP),
            ChatMessage(id = "assistant", role = "assistant", text = "I'd love to, but…", markedStuck = true),
        ))
        val result = ApiProtocol.summaryCandidates("""{"items":[
            {"kind":"PHRASE","content":"I'd love to, but…","sourceMessageId":"marked"},
            {"kind":"WORD","content":"decline","sourceMessageId":"help"},
            {"kind":"TOPIC","content":"Polite refusals","sourceMessageId":"assistant"},
            {"kind":"WORD","content":"invitation","sourceMessageId":"invented"}
        ]}""", session)
        assertTrue(result[0].item.sourceNote.startsWith("用户标记卡壳"))
        assertTrue(result[1].item.sourceNote.startsWith("中文求助"))
        assertTrue(result[2].item.sourceNote.startsWith("AI 建议"))
        assertTrue(result[3].item.sourceNote.startsWith("AI 建议"))
        assertTrue(result.all { it.item.sourceSessionId == "s" })
    }
    @Test fun summaryAcceptsJsonFenceAndSkipsEmptyOrDuplicateCandidates() {
        val raw = "```json\n" + """{"items":[{"kind":"WORD","content":"enjoy"},{"kind":"WORD","content":"ENJOY"},{"content":" "}]}""" + "\n```"
        val result = ApiProtocol.summaryCandidates(raw, ChatSession())
        assertEquals(1, result.size)
        assertEquals(ReviewKind.WORD, result.single().item.kind)
    }
    @Test(expected = IllegalStateException::class) fun malformedSummaryDoesNotCreateLibraryEntries() { ApiProtocol.summaryCandidates("Here are some words…", ChatSession()) }
    @Test fun bilingualHelpIsExplicitAndDoesNotRewritePriorTurns() {
        val session = ChatSession(messages = listOf(
            ChatMessage(role = "user", text = "I go there yesterday."),
            ChatMessage(role = "assistant", text = "How was it?"),
            ChatMessage(role = "user", text = "这句话怎么说", inputKind = InputKind.HELP),
        ))
        val history = CoachPrompts.history(session)
        assertEquals("I go there yesterday.", history[0].second)
        assertEquals("[CHINESE HELP] 这句话怎么说", history[2].second)
    }
    @Test fun reviewPromptIncludesSelectedLearningTargets() {
        val prompt = CoachPrompts.system(ConversationMode.REVIEW, listOf(ReviewItem(content = "Would you mind…?", meaning = "礼貌请求")))
        assertTrue(prompt.contains("Would you mind…?"))
        assertTrue(prompt.contains("礼貌请求"))
        assertTrue(prompt.contains("one question at a time"))
    }
}
