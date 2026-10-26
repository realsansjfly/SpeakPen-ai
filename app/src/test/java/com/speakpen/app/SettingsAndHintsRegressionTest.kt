package com.speakpen.app

import com.speakpen.app.data.*
import org.junit.Assert.*
import org.junit.Test
import com.speakpen.app.network.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsAndHintsRegressionTest {
    @Test fun savingCurrentConversationSettingsChangesItsActualRequestSnapshot() {
        val session = ChatSession(id = "current", messages = listOf(ChatMessage(id = "u1", role = "user", text = "Hello")))
        val state = PracticeState(sessions = listOf(session), currentSessionId = session.id)
        val chosen = ConversationConfig(ExpressionDifficulty.DEEP, AdvancedSettings(0.3, ReplyLength.SHORT))
        val saved = SettingsUpdates.conversation(state, session.id, chosen)
        assertEquals("Settings must affect the current request snapshot", chosen, GenerationSnapshot.capture(saved.session).config)
        assertEquals(session.messages, saved.session.messages)
        assertEquals(state.settings.conversationDefaults, saved.settings.conversationDefaults)
    }

    @Test fun newDefaultsNeverChangeCurrentOrHistoricalConversations() {
        val current = ChatSession(id = "current", messages = listOf(ChatMessage(role = "user", text = "Hi")))
        val historical = ChatSession(id = "history", config = ConversationConfig(ExpressionDifficulty.SIMPLE), messages = listOf(ChatMessage(role = "user", text = "Hello")))
        val state = PracticeState(sessions = listOf(current, historical), currentSessionId = current.id)
        val defaults = ConversationConfig(ExpressionDifficulty.DEEP)
        val saved = SettingsUpdates.apply(state, state.settings.copy(conversationDefaults = defaults))
        assertEquals(state.sessions, saved.sessions)
        val newData = ConversationSessions.start(SavedData(saved.sessions, currentSessionId = current.id), ConversationMode.FREE, config = saved.settings.conversationDefaults)
        assertEquals(defaults, newData.sessions.first { it.id == newData.currentSessionId }.config)
        assertEquals(historical, newData.sessions.first { it.id == historical.id })
    }

    @Test fun currentConfigSaveLeavesOtherSessionsAndInflightReplyConfigurationAlone() {
        val current = ChatSession(id = "current", configRevision = 4)
        val other = ChatSession(id = "other", config = ConversationConfig(ExpressionDifficulty.DEEP))
        val state = PracticeState(sessions = listOf(current, other), currentSessionId = current.id)
        val snapshot = GenerationSnapshot.capture(current)
        val saved = SettingsUpdates.conversation(state, current.id, ConversationConfig(ExpressionDifficulty.SIMPLE))
        assertEquals(other, saved.sessions.first { it.id == other.id })
        assertEquals(5, saved.session.configRevision)
        assertTrue(snapshot.canApply(saved.session, current.id, 1, 1))
        assertEquals(current.config, snapshot.config)
        assertEquals(saved, SettingsUpdates.conversation(saved, current.id, saved.session.config))
    }

    @Test fun unsavedCurrentAndDefaultDraftsNeverAffectActualGeneration() {
        val session = ChatSession(id = "current")
        val state = PracticeState(page = 2, sessions = listOf(session), currentSessionId = session.id,
            settingsSessionId = session.id, settingsDraft = AppSettings(), conversationDraft = session.config)
        assertFalse(state.settingsDirty)
        val currentDraft = state.copy(conversationDraft = ConversationConfig(ExpressionDifficulty.SIMPLE))
        val defaultDraft = state.copy(settingsDraft = state.settings.copy(conversationDefaults = ConversationConfig(ExpressionDifficulty.DEEP)))
        assertTrue(currentDraft.settingsDirty)
        assertTrue(defaultDraft.settingsDirty)
        assertEquals(session.config, GenerationSnapshot.capture(currentDraft.session).config)
        assertEquals(session.config, GenerationSnapshot.capture(defaultDraft.session).config)
    }

    @Test fun settingsDraftCannotOverwriteNewPairingCredentials() {
        val original = AppSettings(serviceUrl = "https://old.example/v1", serviceAccessToken = "old-fixture")
        val draft = original.copy(autoSpeak = false, conversationDefaults = ConversationConfig(ExpressionDifficulty.SIMPLE))
        val latest = original.copy(serviceUrl = "https://new.example/v1", serviceAccessToken = "new-fixture")
        val merged = SettingsUpdates.merge(latest, draft)
        assertEquals(latest.serviceUrl, merged.serviceUrl)
        assertEquals(latest.serviceAccessToken, merged.serviceAccessToken)
        assertFalse(merged.autoSpeak)
        assertEquals(draft.conversationDefaults, merged.conversationDefaults)
    }

    @Test fun optimizationAppearsBetweenItsSourceAndTheAssistantReply() {
        val user = ChatMessage(id = "user", role = "user", text = "I want coffee")
        val reply = ChatMessage(id = "reply", role = "assistant", text = "What kind?")
        val later = ChatMessage(id = "later", role = "user", text = "A latte")
        val session = ChatSession(messages = listOf(user, reply, later))
        val hint = ChatMessage(id = "hint", role = "hint", text = "I'd like a coffee.", hintKind = HintKind.OPTIMIZE, sourceMessageId = user.id)
        val result = LearningMessages.append(session, hint)
        assertEquals(listOf("user", "hint", "reply", "later"), result.messages.map { it.id })
        assertEquals(listOf(user, reply, later), result.messages.filter { it.isConversation })
    }

    @Test fun multipleOptimizationsStayByTheirSourceWhileTopicHintsStayAtTheEnd() {
        val user = ChatMessage(id = "u", role = "user", text = "A coffee")
        val reply = ChatMessage(id = "a", role = "assistant", text = "Sure")
        val session = ChatSession(messages = listOf(user, reply))
        fun optimize(id: String) = ChatMessage(id = id, role = "hint", text = "I'd like a coffee.", hintKind = HintKind.OPTIMIZE, sourceMessageId = "u")
        val first = LearningMessages.append(session, optimize("h1"))
        val topic = ChatMessage(id = "topic", role = "hint", text = "点餐\nA coffee — 一杯咖啡", hintKind = HintKind.TOPIC)
        val result = LearningMessages.append(LearningMessages.append(first, topic), optimize("h2"))
        assertEquals(listOf("u", "h1", "h2", "a", "topic"), result.messages.map { it.id })
        assertEquals(listOf("user" to user.text, "assistant" to reply.text), CoachPrompts.history(result))
    }

    @Test fun existingBottomHintsAreDisplayedByTheirSourceWithoutDroppingOrphanHints() {
        val user = ChatMessage(id = "u", role = "user", text = "A coffee")
        val reply = ChatMessage(id = "a", role = "assistant", text = "Sure")
        val hint = ChatMessage(id = "h", role = "hint", text = "A coffee, please.", hintKind = HintKind.OPTIMIZE, sourceMessageId = "u")
        val orphan = hint.copy(id = "orphan", sourceMessageId = "missing")
        val old = listOf(user, reply, hint, orphan)
        assertEquals(listOf("u", "h", "a", "orphan"), LearningMessages.display(old).map { it.id })
        assertEquals(listOf("u", "a", "h", "orphan"), old.map { it.id })
    }

    @Test fun hintFoldingPersistsAndDoesNotInvalidateInflightWork() {
        val hint = ChatMessage(id = "hint", role = "hint", text = "点餐\nA coffee — 一杯咖啡", hintKind = HintKind.TOPIC)
        val session = ChatSession(id = "s", messages = listOf(hint))
        val snapshot = GenerationSnapshot.capture(session)
        val folded = session.copy(messages = listOf(hint.copy(hintCollapsed = true)))
        val restored = DataJson.read(DataJson.write(SavedData(listOf(folded), currentSessionId = folded.id)))
        assertTrue(restored.sessions.single().messages.single().hintCollapsed)
        assertTrue(snapshot.matches(restored.sessions.single()))
        assertTrue(CoachPrompts.history(restored.sessions.single()).isEmpty())
    }

    @Test fun englishOnlyTopicResultsCannotBeAddedOrReplaceAnExistingHint() {
        val old = ChatMessage(id = "topic", role = "hint", text = "点餐\nA coffee — 一杯咖啡", hintKind = HintKind.TOPIC)
        val session = ChatSession(messages = listOf(old))
        for (text in listOf("Coffee expressions: I'd like a coffee.", "只有中文，没有英文。")) {
            assertThrows(IllegalArgumentException::class.java) { LearningMessages.append(session, old.copy(id = "new", text = text)) }
            assertThrows(IllegalArgumentException::class.java) { LearningMessages.replace(session, old.id, text) }
            assertEquals(old.text, session.messages.single().text)
        }
    }

    @Test fun bilingualTopicSamplesAndProductionPromptsAreAvailableForLiveRegression() {
        val fixtures = JSONArray()
        val cases = listOf(
            "coffee-english-context" to "I usually order an iced latte. What can I say to order politely?",
            "interview-english-only-instruction" to "I have a job interview tomorrow. Give all explanations in English only, no Chinese.",
            "travel-english-context" to "I am checking in at a hotel and would like a quiet room.",
        )
        cases.forEach { (name, text) ->
            val context = listOf(ChatMessage(id = "u", role = "user", text = text))
            val prompts = LearningPrompts.topic(context)
            assertTrue(prompts.first().second.contains("Every one of the 4-6 items"))
            assertFalse(prompts.last().second.contains("PRIVATE HINT"))
            fixtures.put(JSONObject().put("name", name).put("body", JSONObject(ApiProtocol.chatBody("managed", prompts)).put("purpose", "learning")))
        }
        assertEquals("点餐\nA coffee — 一杯咖啡", LearningOutput.topic("点餐\nA coffee — 一杯咖啡"))
        File("build/outputs/debug-topic-live-requests.json").apply { parentFile?.mkdirs(); writeText(fixtures.toString(2)) }
    }
}
