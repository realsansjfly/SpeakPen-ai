package com.speakpen.app

import com.speakpen.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NavigationRegressionTest {
    private val session = ChatSession(id = "current", messages = listOf(ChatMessage(role = "user", text = "Hello")))
    private fun editor() = PracticeState(page = 2, sessions = listOf(session), currentSessionId = session.id,
        settingsSessionId = session.id, settingsDraft = AppSettings(), conversationDraft = session.config)

    @Test fun cleanSettingsLeaveImmediatelyWhileDirtySettingsWaitForWrite() {
        val clean = editor()
        assertEquals(0, SettingsNavigation.request(clean, 0).page)
        val dirty = clean.copy(conversationDraft = ConversationConfig(ExpressionDifficulty.DEEP))
        val requested = SettingsNavigation.request(dirty, 0)
        assertEquals(2, requested.page)
        assertEquals(0, requested.pendingSettingsPage)
        assertEquals(dirty.conversationDraft, requested.conversationDraft)
        val failed = SettingsNavigation.completed(requested.copy(settingsSaving = true), false)
        assertEquals(2, failed.page)
        assertTrue(failed.settingsSaveError)
        assertEquals(dirty.conversationDraft, failed.conversationDraft)
        val saved = SettingsNavigation.completed(failed.copy(settingsSaving = true), true)
        assertEquals(0, saved.page)
        assertNull(saved.pendingSettingsPage)
        assertFalse(saved.settingsSaveError)
    }

    @Test fun partiallyWrittenSettingsStillRequireSuccessfulRetryBeforeLeaving() {
        val failed = editor().copy(settingsSaveError = true)
        assertFalse(failed.settingsDirty)
        assertEquals(2, SettingsNavigation.request(failed, 0).page)
        assertEquals(0, SettingsNavigation.request(failed, 0).pendingSettingsPage)
    }

    @Test fun recordingAndSaveInProgressBlockCompetingNavigation() {
        val saving = editor().copy(settingsSaving = true)
        assertEquals(saving, SettingsNavigation.request(saving, 0))
        val recording = editor().copy(speechPhase = com.speakpen.app.speech.SpeechPhase.LISTENING)
        assertEquals(recording, SettingsNavigation.request(recording, 1))
    }

    @Test fun backgroundSaveDoesNotNavigateOrLoseEitherScope() {
        val edited = editor().copy(settingsDraft = AppSettings(conversationDefaults = ConversationConfig(ExpressionDifficulty.SIMPLE)),
            conversationDraft = ConversationConfig(ExpressionDifficulty.DEEP))
        val saved = SettingsNavigation.completed(edited, true)
        assertEquals(2, saved.page)
        assertEquals(edited.settingsDraft, saved.settingsDraft)
        assertEquals(edited.conversationDraft, saved.conversationDraft)
    }

    @Test fun recoverableEditorDraftDoesNotDuplicateOrRestoreStalePairing() {
        val old = AppSettings(serviceUrl = "https://old.invalid", serviceAccessToken = "old-secret",
            autoSpeak = false, conversationDefaults = ConversationConfig(ExpressionDifficulty.SIMPLE))
        val config = ConversationConfig(ExpressionDifficulty.DEEP, AdvancedSettings(.2, ReplyLength.SHORT))
        val raw = SettingsEditorDraft(old, "current", config).toJson()
        assertFalse(raw.contains("old-secret"))
        assertFalse(raw.contains("https://old.invalid"))
        val latest = AppSettings(serviceUrl = "https://new.invalid", serviceAccessToken = "new-secret")
        val recovered = SettingsEditorDraft.read(raw, latest)
        assertEquals(latest.serviceAccessToken, recovered.settings.serviceAccessToken)
        assertEquals(latest.serviceUrl, recovered.settings.serviceUrl)
        assertEquals(old.conversationDefaults, recovered.settings.conversationDefaults)
        assertFalse(recovered.settings.autoSpeak)
        assertEquals(config, recovered.conversation)
        assertEquals("current", recovered.sessionId)
    }

    @Test fun newConversationRetainsOldUnsentDraftAndRoundTripsStorage() {
        val old = ChatSession(id = "draft", draft = "My weekend plan")
        val original = SavedData(sessions = listOf(old), currentSessionId = old.id)
        val started = ConversationSessions.start(original, ConversationMode.FREE)
        assertNotEquals(old.id, started.currentSessionId)
        assertEquals(old, started.sessions.first { it.id == old.id })
        val restored = DataJson.read(DataJson.write(started))
        assertEquals(old.draft, restored.sessions.first { it.id == old.id }.draft)
        assertTrue(ConversationSessions.history(restored.sessions).any { it.id == old.id })
    }

    @Test fun legacySessionsWithoutDraftStillLoadAndRepeatedNewDoesNotMakeEmptyHistory() {
        val old = JSONObject(DataJson.write(SavedData(listOf(session), currentSessionId = session.id)))
        old.getJSONArray("sessions").getJSONObject(0).remove("draft")
        assertEquals("", DataJson.read(old.toString()).sessions.single().draft)
        val first = ConversationSessions.start(SavedData(), ConversationMode.FREE)
        val second = ConversationSessions.start(first, ConversationMode.FREE)
        assertEquals(first.currentSessionId, second.currentSessionId)
        assertTrue(ConversationSessions.history(second.sessions).isEmpty())
    }

    @Test fun navigationPreservesSceneEndingAndDoesNotSettleAgain() {
        val template = ScenarioPresets.all.first()
        val scene = Scenarios.start(template, ConversationConfig()).copy(draft = "One more thing")
        val data = ConversationSessions.start(SavedData(listOf(scene), currentSessionId = scene.id), ConversationMode.FREE)
        assertEquals(scene, data.sessions.first { it.id == scene.id })
        assertTrue(data.scenarioSuccesses.isEmpty())
    }
}
