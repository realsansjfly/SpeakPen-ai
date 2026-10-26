package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class LearningActionsTest {
    private fun sample(): ChatSession = ChatSession(id = "session", messages = listOf(
        ChatMessage(id = "u1", role = "user", text = "I want talk about coffee."),
        ChatMessage(id = "a1", role = "assistant", text = "What do you like to order?"),
        ChatMessage(id = "h1", role = "hint", text = "PRIVATE HINT MUST NOT ENTER CONTEXT", hintKind = HintKind.TOPIC, hintContextIds = listOf("u1", "a1")),
        ChatMessage(id = "u2", role = "user", text = "我想要少点糖", inputKind = InputKind.HELP),
    ))
    @Test fun allHintsAreExcludedFromChatAndSummaryContext() {
        val session = sample()
        val history = CoachPrompts.history(session)
        assertEquals(listOf("user", "assistant", "user"), history.map { it.first })
        assertFalse(history.toString().contains("PRIVATE HINT"))
        assertFalse(CoachPrompts.summary(session).toString().contains("PRIVATE HINT"))
        assertEquals("[CHINESE HELP] 我想要少点糖", history.last().second)
    }
    @Test fun hintAtEndDoesNotHideAnUnansweredUserMessage() {
        val session = sample().copy(messages = sample().messages + ChatMessage(role = "hint", text = "phrase bank", hintKind = HintKind.TOPIC))
        assertTrue(PracticeState(sessions = listOf(session), currentSessionId = session.id).needsReply)
    }
    @Test fun singleMessageExtractionNeverContainsOtherTurns() {
        val session = sample()
        val selected = session.messages.first()
        val prompts = LearningPrompts.extract(selected)
        assertTrue(prompts[0].second.contains("ONLY from the single selected block"))
        assertEquals(selected.id, JSONObject(prompts[1].second).getString("selectedId"))
        assertFalse(prompts.toString().contains("What do you like to order?"))
        assertFalse(prompts.toString().contains("PRIVATE HINT"))
    }
    @Test fun optimizingIntentUsesOnlyPriorActualConversation() {
        val session = sample().copy(messages = sample().messages + ChatMessage(role = "assistant", text = "FUTURE_TURN"))
        val prompts = LearningPrompts.optimize(session, session.messages.first { it.id == "u2" })
        assertFalse(prompts.toString().contains("PRIVATE HINT"))
        assertFalse(prompts.toString().contains("FUTURE_TURN"))
        assertTrue(prompts[0].second.contains("Preserve facts"))
        assertTrue(prompts[1].second.contains("我想要少点糖"))
    }
    @Test fun topicPhraseBankUsesRealConversationAndFiltersHints() {
        val prompts = LearningPrompts.topic(sample().messages)
        assertFalse(prompts[1].second.contains("PRIVATE HINT"))
        assertTrue(prompts[0].second.contains("4-6"))
        assertTrue(prompts[1].second.contains("coffee"))
    }
    @Test fun regenerationReplacesOnlySelectedBlockAndInvalidatesAudioRevision() {
        val session = sample()
        val updated = LearningMessages.replace(session, "a1", "Would you like a latte?")
        assertEquals(session.messages.size, updated.messages.size)
        assertEquals("a1", updated.messages[1].id)
        assertEquals("Would you like a latte?", updated.messages[1].text)
        assertEquals(1, updated.messages[1].revision)
        assertEquals(session.messages[2], updated.messages[2])
        assertEquals(session.messages[3], updated.messages[3])
    }
    @Test fun hintRegenerationKeepsTypeSourceAndContextAnchor() {
        val original = sample()
        val updated = LearningMessages.replace(original, "h1", "New phrase bank · 新语料")
        assertEquals(HintKind.TOPIC, updated.messages[2].hintKind)
        assertEquals(listOf("u1", "a1"), updated.messages[2].hintContextIds)
        assertFalse(CoachPrompts.history(updated).toString().contains("New phrase bank"))
    }
    @Test(expected = IllegalArgumentException::class) fun userOriginalCannotBeRegenerated() { LearningMessages.replace(sample(), "u1", "overwrite") }
    @Test(expected = IllegalArgumentException::class) fun emptyRegenerationPreservesOldMessage() { LearningMessages.replace(sample(), "a1", " ") }
    @Test fun hintMetadataSurvivesRestart() {
        val data = SavedData(sessions = listOf(sample()), currentSessionId = "session")
        assertEquals(data, DataJson.read(DataJson.write(data)))
    }
    @Test fun oldDataWithoutHintMetadataStillLoads() {
        val raw = """{"sessions":[{"id":"s","messages":[{"id":"m","role":"assistant","text":"Hello"}]}],"library":[]}"""
        val message = DataJson.read(raw).sessions.single().messages.single()
        assertFalse(message.isHint)
        assertEquals(0, message.revision)
        assertNull(message.hintKind)
    }
}
