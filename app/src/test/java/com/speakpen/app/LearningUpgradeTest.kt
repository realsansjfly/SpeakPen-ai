package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LearningUpgradeTest {
    private val user = ChatMessage(id = "latest-user", role = "user", text = "I would like orange juice.")
    private val ai = ChatMessage(id = "latest-ai", role = "assistant", text = "Regular or large?")
    @Test fun latestRoundExcludesHistoryHintsAndPendingTurns() {
        val old = listOf(ChatMessage(role = "user", text = "SECRET_OLD_TOPIC"), ChatMessage(role = "assistant", text = "old answer"))
        val messages = old + user + ChatMessage(role = "hint", text = "not conversation") + ai
        assertEquals(listOf(user, ai), TopicRound.latest(messages))
        assertThrows(IllegalArgumentException::class.java) { TopicRound.latest(messages + ChatMessage(role = "user", text = "pending")) }
        assertThrows(IllegalArgumentException::class.java) { TopicRound.latest(listOf(ai)) }
        assertThrows(IllegalArgumentException::class.java) { TopicRound.latest(listOf(user.copy(text = ""), ai)) }
        val prompt = LearningPrompts.topic(messages).joinToString { it.second }
        assertFalse(prompt.contains("SECRET_OLD_TOPIC")); assertFalse(prompt.contains("not conversation"))
        assertTrue(prompt.contains("Regular or large?"))
    }
    @Test fun allPurposesDifficultiesAndCountsHaveExplicitProtocol() {
        TopicPurpose.entries.forEach { purpose -> TopicDifficulty.entries.forEach { difficulty -> listOf(4, 5, 10).forEach { count ->
            val options = TopicOptions(purpose, count, difficulty)
            val prompt = LearningPrompts.topic(listOf(user, ai), options = options)
            val input = JSONObject(prompt.last().second)
            assertEquals(purpose.name, input.getString("purpose")); assertEquals(difficulty.name, input.getString("difficulty")); assertEquals(count, input.getInt("count"))
            assertEquals(2, input.getJSONArray("round").length())
            val items = JSONArray().apply { repeat(count) { put(JSONObject().put("expression", "I'd like a large, please.").put("meaning", "礼貌选择大杯")
                .put("example", "I'd like a large orange juice, please.").put("translation", "我想要一大杯橙汁。")) } }
            assertTrue(TopicBankProtocol.parse(JSONObject().put("items", items).toString(), options).startsWith(purpose.label))
            items.remove(0)
            assertThrows(IllegalArgumentException::class.java) { TopicBankProtocol.parse(JSONObject().put("items", items).toString(), options) }
        } } }
        listOf(3, 11).forEach { assertThrows(IllegalArgumentException::class.java) { TopicOptions(count = it) } }
    }
    @Test fun queueLimitsCombinationAndPersistence() {
        val queue = (1..6).fold(emptyList<VoiceSegment>()) { q, i -> VoiceQueue.append(q, VoiceSegment(id = "$i", file = "a.m4a", durationMs = 30000, text = "part $i")) }
        assertEquals(180000, queue.sumOf { it.durationMs }.toInt())
        assertEquals("part 1\npart 2\npart 3\npart 4\npart 5\npart 6", VoiceQueue.combined(queue))
        assertThrows(IllegalArgumentException::class.java) { VoiceQueue.append(queue, queue[0]) }
        assertThrows(IllegalArgumentException::class.java) { VoiceQueue.append(emptyList(), queue[0].copy(durationMs = 180001)) }
        assertThrows(IllegalArgumentException::class.java) { VoiceQueue.combined(queue.mapIndexed { i, s -> if (i == 1) s.copy(failed = true) else s }) }
        val session = ChatSession(voiceQueue = queue)
        val data = DataJson.read(DataJson.write(SavedData(sessions = listOf(session), currentSessionId = session.id)))
        assertEquals(queue, data.sessions.single().voiceQueue)
        assertEquals(1, ConversationSessions.history(data.sessions).size)
        assertEquals(2, ConversationSessions.start(data, ConversationMode.FREE).sessions.size)
    }
    @Test fun lessonSurvivesNormalizationAndNoFormalMessages() {
        val p = LessonProgress(ScenarioPresets.all[3], stage = LessonStage.SUBSTITUTE, chunk = 3, audio = "a.m4a")
        val session = ChatSession(lesson = p)
        val data = SavedData(sessions = listOf(session), currentSessionId = session.id)
        assertEquals(p, DataJson.read(DataJson.write(data)).sessions.single().lesson)
        assertEquals(1, ConversationSessions.history(ConversationSessions.normalize(data).sessions).size)
        assertTrue(session.messages.isEmpty())
        assertEquals(2, ConversationSessions.start(data, ConversationMode.FREE).sessions.size)
        assertThrows(IllegalArgumentException::class.java) { LessonProtocol.parse("""{"feedback":"你的发音得分 95 分","model":"Hello."}""") }
        assertTrue(LessonProtocol.parse("""{"feedback":"关键信息完整，可以补充姓名。","model":"My name is Charles Nelson."}""").contains("姓名"))
        assertThrows(IllegalArgumentException::class.java) { LessonProtocol.parse("""{"feedback":"你的语速稳定。","model":"Hello."}""") }
        assertThrows(IllegalArgumentException::class.java) { LessonProtocol.parse("""{"feedback":"意思完整。","model":"Your pronunciation score is 95."}""") }
    }
    @Test fun materialUsesBriefChunksAndHonestSource() {
        val source = java.io.File("src/main/assets/scenarios/material.json").readText()
        ScenarioPresets.all.forEach { template ->
            val material = ScenarioMaterials.read(source, template)
            assertTrue(material.chunks.all { it.size in 1..3 })
            assertTrue("${template.id}: ${material.chunks.size} chunks", material.chunks.size <= 12)
            assertEquals(template.id != "builtin:interview", material.source.contains("用户提供"))
        }
        val hotel = ScenarioMaterials.read(source, ScenarioPresets.all.first { it.id == "builtin:hotel" })
        assertTrue(hotel.chunks.size > 1)
        assertTrue(hotel.chunks.flatten().any { it.contains("reservation") })
        assertTrue(hotel.chunks.flatten().any { it.contains("discount") })
    }
    @Test fun completeDialoguePreservesEverySourceLineAndAudioNeverTruncates() {
        val source = java.io.File("src/main/assets/scenarios/material.json").readText()
        val scenes = JSONObject(source).getJSONObject("scenes")
        ScenarioPresets.all.filter { scenes.has(it.id.removePrefix("builtin:")) }.forEach { template ->
            val lines = scenes.getJSONObject(template.id.removePrefix("builtin:")).getJSONArray("dialogue")
            val material = ScenarioMaterials.read(source, template)
            assertEquals((0 until lines.length()).map(lines::getString), material.dialogue)
            assertEquals(material.dialogue.joinToString("") { it.substringAfter(": ") }, material.playbackParts().joinToString(""))
            assertTrue(material.playbackParts().all { it.length <= 1800 })
        }
        val long = ScenarioMaterial("test", listOf(listOf("Host: " + "x".repeat(4500))))
        assertEquals(4500, long.playbackParts().sumOf { it.length })
        val interview = ScenarioMaterials.read(source, ScenarioPresets.all.last())
        assertFalse(interview.source.contains("用户提供"))
        assertEquals(4, interview.dialogue.size)
    }
    @Test fun oldScaffoldAndNewPreferencesRoundTrip() {
        val message = ChatMessage(role = "hint", text = "old", hintKind = HintKind.SCAFFOLD, hintLayers = listOf("keywords", "skeleton", "reference"))
        val config = ConversationConfig(enhancement = VocabularyEnhancement(false, RetrievalScope.TURN, RetrievalStrength.ACTIVE, 12))
        assertEquals(config, ConversationConfigJson.read(ConversationConfigJson.write(config)))
        val data = SavedData(sessions = listOf(ChatSession(messages = listOf(message))))
        assertEquals(message, DataJson.read(DataJson.write(data)).sessions.single().messages.single())
        val history = CoachPrompts.history(data.sessions.single())
        assertFalse(history.any { it.second.contains("old") })
    }
}
