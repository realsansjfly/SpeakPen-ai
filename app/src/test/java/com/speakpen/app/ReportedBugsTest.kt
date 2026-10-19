package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.speech.*
import org.junit.Assert.*
import org.junit.Test

class ReportedBugsTest {
    private class VoiceFixture {
        var stopCalls = 0
        var releaseCalls = 0
        var currentPhase = SpeechPhase.IDLE
        val texts = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val notices = mutableListOf<String>()
        val deadlines = mutableListOf<Pair<Long, () -> Unit>>()
        val session = SystemSpeechSession(
            stopListening = { stopCalls++ }, release = { releaseCalls++ },
            schedule = { delay, action ->
                val task = delay to action
                deadlines += task
                val cancel: () -> Unit = { deadlines.remove(task); Unit }
                cancel
            },
            onPhase = { currentPhase = it }, onPartial = {}, onText = { texts += it },
            onNotice = { notices += it }, onError = { errors += it },
        )
        fun advanceDeadline() { deadlines.toList().forEach { it.second() } }
    }
    @Test fun pressingStopImmediatelyLeavesRecordingAndShowsRecognitionPending() {
        val fixture = VoiceFixture()
        fixture.session.start()
        fixture.session.stop()
        assertEquals(1, fixture.stopCalls)
        assertEquals(SpeechPhase.FINALIZING, fixture.currentPhase)
        assertEquals(1, fixture.deadlines.size)
    }
    @Test fun silentServiceTimesOutAndKeepsLatestPartialAsEditableText() {
        val fixture = VoiceFixture()
        fixture.session.start(); fixture.session.partial("I enjoy hiking."); fixture.session.stop()
        fixture.advanceDeadline()
        assertEquals(SpeechPhase.IDLE, fixture.currentPhase)
        assertEquals(listOf("I enjoy hiking."), fixture.texts)
        assertEquals(1, fixture.releaseCalls)
        assertTrue(fixture.notices.single().contains("最终"))
    }
    @Test fun silentServiceWithNoTextShowsErrorAndUnlocksRecording() {
        val fixture = VoiceFixture()
        fixture.session.start(); fixture.session.stop(); fixture.advanceDeadline()
        assertEquals(SpeechPhase.IDLE, fixture.currentPhase)
        assertTrue(fixture.texts.isEmpty())
        assertTrue(fixture.errors.single().contains("超时"))
        assertEquals(1, fixture.releaseCalls)
    }
    @Test fun repeatedStopDoesNotRestartDeadlineOrStopRecognitionTwice() {
        val fixture = VoiceFixture()
        fixture.session.start(); repeat(4) { fixture.session.stop() }
        assertEquals(1, fixture.stopCalls)
        assertEquals(1, fixture.deadlines.size)
    }
    @Test fun naturalEndOfSpeechAlsoShowsPendingAndHasBoundedWait() {
        val fixture = VoiceFixture()
        fixture.session.start(); fixture.session.endOfSpeech()
        assertEquals(SpeechPhase.FINALIZING, fixture.currentPhase)
        assertEquals(0, fixture.stopCalls)
        assertEquals(1, fixture.deadlines.size)
    }
    @Test fun finalResultCancelsDeadlineAndLateCallbacksCannotOverwriteDraft() {
        val fixture = VoiceFixture()
        fixture.session.start(); fixture.session.stop(); fixture.session.results("Final sentence.")
        fixture.advanceDeadline(); fixture.session.results("Late sentence."); fixture.session.partial("Late partial")
        assertEquals(listOf("Final sentence."), fixture.texts)
        assertEquals(SpeechPhase.IDLE, fixture.currentPhase)
        assertEquals(1, fixture.releaseCalls)
        assertTrue(fixture.deadlines.isEmpty())
    }
    @Test fun cancelWhileWaitingRemovesDeadlineAndDiscardsLateResult() {
        val fixture = VoiceFixture()
        fixture.session.start(); fixture.session.stop(); fixture.session.cancel(); fixture.session.results("late")
        fixture.advanceDeadline()
        assertEquals(SpeechPhase.IDLE, fixture.currentPhase)
        assertTrue(fixture.texts.isEmpty())
        assertTrue(fixture.deadlines.isEmpty())
        assertEquals(1, fixture.releaseCalls)
    }
    @Test fun lateReadyCallbackCannotReactivateRecordingAfterStop() {
        val fixture = VoiceFixture()
        fixture.session.start(); fixture.session.stop(); fixture.session.ready()
        assertEquals(SpeechPhase.FINALIZING, fixture.currentPhase)
    }
    @Test fun repeatedNewConversationReusesUntouchedSession() {
        val empty = ChatSession(id = "empty")
        var data = SavedData(sessions = listOf(empty), currentSessionId = empty.id)
        repeat(12) { data = ConversationSessions.start(data, ConversationMode.FREE) }
        assertEquals(1, data.sessions.size)
        assertEquals("empty", data.currentSessionId)
        assertTrue(ConversationSessions.history(data.sessions).isEmpty())
    }
    @Test fun creatingNewConversationPreservesAllNonemptyHistory() {
        val completed = ChatSession(id = "used", messages = listOf(ChatMessage(role = "user", text = "Hello")))
        var data = SavedData(sessions = listOf(completed), currentSessionId = completed.id)
        repeat(8) { data = ConversationSessions.start(data, ConversationMode.FREE) }
        assertEquals(2, data.sessions.size)
        assertEquals(listOf(completed), ConversationSessions.history(data.sessions))
        assertEquals(1, data.sessions.count { it.messages.isEmpty() })
    }
    @Test fun oldEmptyRecordsAreCleanedWithoutDeletingActualMessages() {
        val current = ChatSession(id = "current")
        val meaningful = ChatSession(id = "used", messages = listOf(ChatMessage(role = "user", text = "Hello")))
        val data = SavedData(sessions = listOf(ChatSession(id = "old-empty"), meaningful, current), currentSessionId = current.id)
        val normalized = ConversationSessions.normalize(data)
        assertEquals(setOf("used", "current"), normalized.sessions.map { it.id }.toSet())
        assertEquals("current", normalized.currentSessionId)
        assertEquals(listOf(meaningful), ConversationSessions.history(normalized.sessions))
    }
    @Test fun switchingModeOnAnEmptySessionUpdatesTargetsWithoutAddingHistory() {
        val empty = ChatSession(id = "empty")
        val data = ConversationSessions.start(SavedData(listOf(empty), currentSessionId = empty.id), ConversationMode.REVIEW, listOf("word", "phrase"))
        assertEquals(1, data.sessions.size)
        assertEquals("empty", data.currentSessionId)
        assertEquals(ConversationMode.REVIEW, data.sessions.single().mode)
        assertEquals(listOf("word", "phrase"), data.sessions.single().targetIds)
    }
}
