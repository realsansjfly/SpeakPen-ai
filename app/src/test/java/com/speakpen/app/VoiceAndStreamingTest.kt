package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.ChatStreamParser
import org.junit.Assert.*
import org.junit.Test

class VoiceAndStreamingTest {
    @Test fun voiceRecordAndDurationSurviveDataRoundTrip() {
        val voice = ChatMessage(id = "voice", role = "user", text = "I had coffee.", fromVoice = true,
            audioFileName = "1234-abcd.m4a", audioDurationMs = 8300, transcriptState = TranscriptState.READY)
        val data = SavedData(listOf(ChatSession(id = "s", messages = listOf(voice))), currentSessionId = "s")
        assertEquals(data, DataJson.read(DataJson.write(data)))
        assertEquals(9, VoiceMessages.displaySeconds(8300))
    }
    @Test fun oldMessagesWithoutAudioFieldsRemainReadable() {
        val raw = """{"sessions":[{"id":"s","messages":[{"id":"m","role":"user","text":"Hello"}]}],"library":[]}"""
        val message = DataJson.read(raw).sessions.single().messages.single()
        assertEquals("", message.audioFileName)
        assertEquals(TranscriptState.NONE, message.transcriptState)
        assertEquals("Hello", message.text)
    }
    @Test fun interruptedTranscriptionRestoresAsRetryableWithoutDroppingRecording() {
        val original = ChatMessage(role = "user", text = "", audioFileName = "abcd.m4a", transcriptState = TranscriptState.PENDING)
        val restored = VoiceMessages.restore(original)
        assertEquals(TranscriptState.FAILED, restored.transcriptState)
        assertEquals("abcd.m4a", restored.audioFileName)
        assertEquals(original.id, restored.id)
    }
    @Test fun transcriptionAttachesTextWithoutDiscardingOriginalAudio() {
        val original = ChatMessage(role = "user", text = "", audioFileName = "abcd.m4a", audioDurationMs = 5000, transcriptState = TranscriptState.PENDING)
        val ready = VoiceMessages.ready(original, " I go there yesterday. ")
        assertEquals("I go there yesterday.", ready.text)
        assertEquals(original.audioFileName, ready.audioFileName)
        assertEquals(original.audioDurationMs, ready.audioDurationMs)
        assertEquals(TranscriptState.READY, ready.transcriptState)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyTranscriptionDoesNotMarkVoiceAsComplete() {
        VoiceMessages.ready(ChatMessage(role = "user", text = "", audioFileName = "abcd.m4a"), " ")
    }
    @Test fun sseTokensAppearBeforeStreamFinishesAndReasoningIsHidden() {
        val stream = ChatStreamParser()
        assertNull(stream.line(": keep alive"))
        assertNull(stream.line("data: {\"choices\":[{\"delta\":{\"reasoning\":\"private thought\"}}]}"))
        assertEquals("Hello", stream.line("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"))
        assertFalse(stream.finished)
        assertEquals("Hello there!", stream.line("data: {\"choices\":[{\"delta\":{\"content\":\" there!\"}}]}"))
        stream.line("data: [DONE]")
        assertEquals("Hello there!", stream.result())
    }
    @Test(expected = IllegalStateException::class) fun truncatedStreamIsNotSilentlyAcceptedAsComplete() {
        val stream = ChatStreamParser(); stream.line("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}")
        stream.result()
    }
    @Test fun finishReasonSupportsProvidersThatCloseWithoutDoneSentinel() {
        val stream = ChatStreamParser()
        stream.line("data: {\"choices\":[{\"delta\":{\"content\":\"All done.\"}}]}")
        stream.line("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}")
        assertEquals("All done.", stream.result())
    }
    @Test(expected = IllegalArgumentException::class) fun sseErrorCannotBeDisplayedAsAnswer() {
        ChatStreamParser().line("data: {\"error\":{\"message\":\"provider failed\"}}")
    }
}
