package com.speakpen.app

import com.speakpen.app.data.*
import org.junit.Assert.*
import org.junit.Test

class LearningDataTest {
    @Test fun learningDataRoundTripPreservesSourcesMarkersAndSchedule() {
        val item = ReviewItem(id = "r", kind = ReviewKind.PHRASE, content = "Let me think.", meaning = "让我想一下", example = "Let me think for a moment.", sourceSessionId = "s", sourceNote = "用户标记卡壳", stage = 2, nextReviewAt = 5000L, createdAt = 1000L)
        val session = ChatSession(id = "s", mode = ConversationMode.REVIEW, targetIds = listOf("r"), messages = listOf(ChatMessage(id = "m", role = "user", text = "这句话怎么说？", inputKind = InputKind.HELP, fromVoice = true, markedStuck = true, createdAt = 1200L)), createdAt = 1000L, updatedAt = 1300L)
        val data = SavedData(listOf(session), listOf(item), "s")
        assertEquals(data, DataJson.read(DataJson.write(data)))
    }
    @Test fun rememberedTargetsGetIncreasingIntervalsAndFailuresReturnToNow() {
        val now = 1000L
        val first = ReviewSchedule.update(ReviewItem(content = "enjoy"), true, now)
        assertEquals(1, first.stage)
        assertEquals(now + 86_400_000L, first.nextReviewAt)
        val second = ReviewSchedule.update(first, true, now)
        assertEquals(2, second.stage)
        assertEquals(now + 3L * 86_400_000L, second.nextReviewAt)
        val failed = ReviewSchedule.update(second, false, now)
        assertEquals(0, failed.stage)
        assertEquals(now, failed.nextReviewAt)
    }
    @Test fun reviewIntervalIsCappedAfterRepeatedSuccesses() {
        var item = ReviewItem(content = "Would you mind…?")
        repeat(15) { item = ReviewSchedule.update(item, true, 0) }
        assertEquals(5, item.stage)
        assertEquals(30L * 86_400_000L, item.nextReviewAt)
    }
    @Test fun olderLocalFilesWithoutNewFieldsStillLoad() {
        val raw = """{"sessions":[],"library":[{"id":"r","content":"weekend"}]}"""
        val result = DataJson.read(raw)
        assertEquals(ReviewKind.PHRASE, result.library.single().kind)
        assertEquals(0L, result.library.single().nextReviewAt)
    }
}
