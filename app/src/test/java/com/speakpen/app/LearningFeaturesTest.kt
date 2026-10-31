package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LearningFeaturesTest {
    private val user = ChatMessage(id = "user", role = "user", text = "I very like coffee.")
    private val reply = ChatMessage(id = "reply", role = "assistant", text = "Do you prefer coffee or tea?", replyToMessageId = user.id)
    private fun session() = ChatSession(id = "session", messages = listOf(user, reply))
    private fun optimized(s: ChatSession = session()) = ChatMessage(id = "hint", role = "hint", text = "I really like coffee.\n用 really 修饰 like。",
        hintKind = HintKind.OPTIMIZE, sourceMessageId = user.id, hintContextIds = listOf(user.id), hintContextDigest = LearningFeatures.contextDigest(listOf(user)),
        practiceReference = "I really like coffee.", generationConfig = s.config)
    private fun practice() = LearningFeatures.beginPractice(session().let { it.copy(messages = it.messages + optimized(it)) }, "hint")
    private fun rejected(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }
    private fun recapRaw(snapshot: RecapSnapshot) = JSONObject().put("overview", "你讨论了咖啡偏好。")
        .put("evidence", JSONArray().put(JSONObject().put("messageId", user.id).put("quote", "coffee").put("comment", "你提到了咖啡。")))
        .put("improvements", JSONArray().put(JSONObject().put("messageId", user.id).put("original", "I very like coffee.")
            .put("improved", "I really like coffee.").put("explanation", "用 really 修饰 like。")))
        .put("vocabulary", JSONArray().put(JSONObject().put("expression", "coffee").put("meaning", "咖啡").put("example", "I enjoy coffee.")))
    private fun ended(success: Boolean = true): ChatSession {
        val template = ScenarioTemplate(id = "cafe", name = "咖啡店", aiRole = "店员", userRole = "顾客", goals = listOf(ScenarioGoal("g", "点单")),
            opening = "Hi!", endCondition = "点单完成", maxTurns = 1)
        val s = Scenarios.start(template, ConversationConfig()).let { it.copy(messages = it.messages + user + reply) }
        return Scenarios.assess(Scenarios.recordTurn(s, user.id, reply.id), ScenarioAssessment(listOf(ScenarioGoalProgress("g", success, "依据")), success, "依据", emptyList()))
    }
    @Test fun oldConfigurationDefaultsCorrectionOffAndRoundTripsIndependently() {
        assertFalse(ConversationConfigJson.read(JSONObject()).realtimeCorrection)
        val config = ConversationConfig(difficulty = ExpressionDifficulty.DEEP, realtimeCorrection = true)
        assertEquals(config, ConversationConfigJson.read(ConversationConfigJson.write(config)))
        assertEquals(ExpressionDifficulty.DEEP, config.copy(realtimeCorrection = false).difficulty)
        assertFalse(session().config.realtimeCorrection)
    }
    @Test fun scaffoldRevealsOneLayerAndCannotRevealPastThree() {
        val h = ChatMessage(id = "h", role = "hint", text = "coffee：咖啡", hintKind = HintKind.SCAFFOLD, hintLayers = listOf("a", "b", "c"),
            sourceMessageId = reply.id, hintContextIds = listOf(user.id, reply.id), hintContextDigest = LearningFeatures.contextDigest(session().messages))
        var s = session().copy(messages = session().messages + h)
        assertEquals(1, s.messages.last().revealedLayers)
        repeat(5) { s = LearningFeatures.reveal(s, h.id) }
        assertEquals(3, s.messages.last().revealedLayers)
    }
    @Test fun scaffoldInvalidatesOnNewTurnAndEditedSource() {
        val h = ChatMessage(id = "h", role = "hint", text = "x", hintKind = HintKind.SCAFFOLD, hintLayers = listOf("a", "b", "c"),
            sourceMessageId = reply.id, hintContextIds = listOf(user.id, reply.id), hintContextDigest = LearningFeatures.contextDigest(session().messages))
        val s = session().copy(messages = session().messages + h)
        assertFalse(LearningFeatures.hintCurrent(s.copy(messages = s.messages + user.copy(id = "new")), h))
        assertFalse(LearningFeatures.hintCurrent(s.copy(messages = s.messages.map { if (it.id == reply.id) it.copy(revision = 1) else it }), h))
    }
    @Test fun structuredScaffoldRequiresBilingualLayersAndBlankSkeleton() {
        assertEquals(3, LearningFeatureProtocol.parseScaffold("""{"keywords":"coffee：咖啡", "skeleton":"I'd like ___. 填入饮品。", "reference":"I'd like coffee. 我想要咖啡。"}""").size)
        assertEquals(3, LearningFeatureProtocol.parseScaffold("""{"keywords":"journal：日记", "skeleton":"I prefer to <记录方式> because... 说明原因。", "reference":"I prefer to keep a journal because it helps me remember small moments. 我更喜欢写日记，因为它能帮我记住小事。"}""").size)
        rejected { LearningFeatureProtocol.parseScaffold("""{"keywords":"coffee", "skeleton":"I like it.", "reference":"Hello"}""") }
    }
    @Test fun scaffoldAcceptsPairedPlaceholdersButRejectsAnUnmatchedBracketOrEllipsis() {
        for (skeleton in listOf("I prefer [an activity]. 填入活动。", "I prefer {活动}. 填入活动。", "I prefer 【活动】. 填入活动。")) {
            val raw = JSONObject().put("keywords", "prefer：更喜欢").put("skeleton", skeleton)
                .put("reference", "I prefer walking. 示例：我更喜欢散步。").toString()
            assertEquals(skeleton, LearningFeatureProtocol.parseScaffold(raw)[1])
        }
        for (skeleton in listOf("I prefer walking... 我更喜欢散步。", "I prefer [walking. 我更喜欢散步。")) {
            rejected { LearningFeatureProtocol.parseScaffold(JSONObject().put("keywords", "prefer：更喜欢")
                .put("skeleton", skeleton).put("reference", "I prefer walking. 我更喜欢散步。").toString()) }
        }
    }
    @Test fun practiceStartsWithoutAddingConversationAndKeepsOriginalAndReference() {
        val s = session().copy(messages = session().messages + optimized())
        val p = LearningFeatures.beginPractice(s, "hint")
        assertEquals(2, s.messages.count { it.isConversation }); assertEquals(user.text, p.original)
        assertEquals("I really like coffee.", p.reference); assertEquals(user.revision, p.sourceRevision)
    }
    @Test fun changedOriginalCannotStartPracticeFromStaleOptimization() {
        val s = session().copy(messages = listOf(user.copy(text = "Changed", revision = 1), reply, optimized()))
        rejected { LearningFeatures.beginPractice(s, "hint") }
    }
    @Test fun unconfirmedPracticeNeverAppearsInHistoryOrLearningContext() {
        val p = practice().copy(transcript = "PRIVATE PRACTICE", audioFileName = "a.m4a")
        val s = session().copy(speakingPractices = listOf(p), messages = session().messages + optimized())
        assertFalse(CoachPrompts.history(s).toString().contains("PRIVATE"))
        assertFalse(LearningFeatureProtocol.scaffold(s).toString().contains("PRIVATE"))
        assertEquals(2, RecapSnapshot.capture(s, RecapKind.LATEST).messages.size)
    }
    @Test fun explicitlySendingPracticeIsAtomicAndCannotSendTwice() {
        val p = practice().copy(transcript = "I really like coffee.", audioFileName = "a.m4a", transcriptState = TranscriptState.READY)
        val s = session().copy(speakingPractices = listOf(p))
        val sent = LearningFeatures.sendPractice(s, p.id)
        assertEquals(3, sent.messages.size); assertEquals(p.audioFileName, sent.messages.last().audioFileName)
        assertEquals(sent.messages.last().id, sent.speakingPractices.single().sentMessageId)
        rejected { LearningFeatures.sendPractice(sent.copy(messages = sent.messages + reply.copy(id = "later")), p.id) }
    }
    @Test fun repeatedRecordingPreservesEveryTakeAndItsReference() {
        val p = practice(); val initial = session().copy(speakingPractices = listOf(p))
        val (first, take1) = LearningFeatures.recordPractice(initial, p.id, "a.m4a", 1000)
        val (second, take2) = LearningFeatures.recordPractice(first, take1.id, "b.m4a", 2000)
        assertEquals(listOf("a.m4a", "b.m4a"), second.speakingPractices.map { it.audioFileName })
        assertNotEquals(take1.id, take2.id); assertEquals(take1.reference, take2.reference)
        assertEquals(initial.messages, second.messages)
    }
    @Test fun practiceCannotSendWhenEndedPendingReplyOrMissingTranscript() {
        val p = practice().copy(transcript = "Hello")
        rejected { LearningFeatures.sendPractice(ended().copy(speakingPractices = listOf(p)), p.id) }
        rejected { LearningFeatures.sendPractice(session().copy(messages = listOf(user), speakingPractices = listOf(p)), p.id) }
        rejected { LearningFeatures.sentMessage(p.copy(transcript = "")) }
    }
    @Test fun optimizationRequiresEnglishAndChineseAndExposesCleanReference() {
        val scenario = ended()
        val payload = JSONObject(LearningFeatureProtocol.optimize(scenario, user).last().second)
        assertEquals("店员", payload.getJSONObject("scenario").getString("aiRole"))
        assertEquals("顾客", payload.getJSONObject("scenario").getString("userRole"))
        val result = LearningFeatureProtocol.parseOptimize("""{"reference":"I really like coffee.", "explanation":"用 really 修饰 like。"}""")
        assertEquals("I really like coffee.", result.reference)
        rejected { LearningFeatureProtocol.parseOptimize("""{"reference":"你好", "explanation":"English only"}""") }
    }
    @Test fun correctionMustQuoteActualLearnerAndMayBeEmpty() {
        val raw = """{"corrections":[{"original":"very like", "improved":"really like", "explanation":"搭配更自然。"}]}"""
        assertTrue(LearningFeatureProtocol.parseCorrection(raw, user).contains("very like → really like"))
        assertEquals("", LearningFeatureProtocol.parseCorrection("""{"corrections":[]}""", user))
        rejected { LearningFeatureProtocol.parseCorrection(raw.replace("very like", "invented"), user) }
    }
    @Test fun correctionNeverEntersModelHistoryAndDoesNotInvalidateInFlightReply() {
        val s = session(); val snapshot = GenerationSnapshot.capture(s)
        val hint = ChatMessage(role = "hint", text = "PRIVATE CORRECTION", hintKind = HintKind.CORRECTION, sourceMessageId = user.id)
        val updated = LearningMessages.append(s, hint)
        assertTrue(snapshot.matches(updated)); assertFalse(CoachPrompts.history(updated).toString().contains("PRIVATE"))
        assertEquals(listOf(user.id, hint.id, reply.id), LearningMessages.display(updated.messages).map { it.id })
    }
    @Test fun sideHintDoesNotInvalidateOrGetLostByScenarioAssessment() {
        val s = ended().let { it.copy(scenario = it.scenario!!.copy(phase = ScenarioPhase.ACTIVE, ending = null, checkStatus = ScenarioCheckStatus.PENDING)) }
        val snapshot = ScenarioCheckSnapshot(s)
        val h = ChatMessage(role = "hint", text = "旁路", hintKind = HintKind.CORRECTION)
        val updated = s.copy(messages = s.messages + h)
        assertTrue(snapshot.matches(updated))
        val assessed = Scenarios.assess(updated, ScenarioAssessment(listOf(ScenarioGoalProgress("g", false, "依据")), false, "依据", emptyList()))
        assertTrue(assessed.messages.contains(h))
    }
    @Test fun recapUsesOnlyActualDialogueAndLatestIncludesContinuation() {
        val end = ended(); val continued = Scenarios.continueFreely(end).let { it.copy(messages = it.messages + ChatMessage(role = "user", text = "AFTER END")) }
        assertFalse(RecapSnapshot.capture(continued, RecapKind.SCENARIO).messages.any { it.text == "AFTER END" })
        assertTrue(RecapSnapshot.capture(continued, RecapKind.LATEST).messages.any { it.text == "AFTER END" })
        assertEquals(end.scenario?.ending, continued.scenario?.ending)
    }
    @Test fun failedOrCancelledRecapKeepsPreviousAndSuccessfulLatestReplacesOnlyLatest() {
        val end = ended(); val fixed = RecapSnapshot.capture(end, RecapKind.SCENARIO).complete(end, "场景")
        val first = RecapSnapshot.capture(fixed, RecapKind.LATEST).complete(fixed, "第一份")
        val request = RecapSnapshot.capture(first, RecapKind.LATEST)
        assertEquals("第一份", first.latestRecap?.markdown)
        val second = request.complete(first, "第二份")
        assertEquals("第二份", second.latestRecap?.markdown); assertEquals("场景", second.fixedRecap?.markdown)
        rejected { request.complete(second, "过期") }
    }
    @Test fun fixedRecapCannotBeOverwrittenAndRejectsUnfinishedScenario() {
        val end = ended(); val fixed = RecapSnapshot.capture(end, RecapKind.SCENARIO).complete(end, "固定")
        rejected { RecapSnapshot.capture(fixed, RecapKind.SCENARIO) }
        rejected { RecapSnapshot.capture(session(), RecapKind.SCENARIO) }
    }
    @Test fun recapRejectsEditsAndAnotherSessionButAllowsHintsAndConfigChanges() {
        val s = session(); val snapshot = RecapSnapshot.capture(s, RecapKind.LATEST)
        assertFalse(snapshot.matches(s.copy(id = "other")))
        assertFalse(snapshot.matches(s.copy(messages = listOf(user.copy(revision = 1), reply))))
        assertTrue(snapshot.matches(s.copy(config = s.config.copy(realtimeCorrection = true), messages = s.messages + optimized())))
    }
    @Test fun longRecapUsesAllMessagesWithinExistingServiceBounds() {
        val messages = (1..20).map { ChatMessage(id = "long$it", role = if (it % 2 == 1) "user" else "assistant", text = "word ".repeat(400)) }
        val snapshot = RecapSnapshot.capture(session().copy(messages = messages), RecapKind.LATEST)
        val prompts = LearningFeatureProtocol.recap(snapshot)
        assertTrue(prompts.size > 3); assertTrue(prompts.all { it.second.length <= 20000 })
        assertTrue(prompts.sumOf { it.second.length } <= 70000)
        val all = prompts.joinToString { it.second }
        assertTrue(messages.all { all.contains(it.id) })
        rejected { RecapSnapshot.capture(session().copy(messages = messages + messages.map { it.copy(id = newId()) }), RecapKind.LATEST) }
    }
    @Test fun recapEvidenceMustExistAndImprovementsMustComeFromLearner() {
        val snapshot = RecapSnapshot.capture(session(), RecapKind.LATEST)
        val raw = recapRaw(snapshot)
        assertTrue(LearningFeatureProtocol.parseRecap(raw.toString(), snapshot).contains("## 表达改进"))
        raw.getJSONArray("evidence").getJSONObject(0).put("quote", "INVENTED")
        rejected { LearningFeatureProtocol.parseRecap(raw.toString(), snapshot) }
        val assistantAsLearner = recapRaw(snapshot).apply { getJSONArray("improvements").getJSONObject(0).put("messageId", reply.id) }
        rejected { LearningFeatureProtocol.parseRecap(assistantAsLearner.toString(), snapshot) }
    }
    @Test fun recapAndPracticeRoundTripWithOldDataDefaultsAndRecordingReferences() {
        val s = session().copy(speakingPractices = listOf(practice().copy(audioFileName = "a.m4a")), messages = session().messages + optimized())
        val snapshot = RecapSnapshot.capture(s, RecapKind.LATEST)
        val documented = snapshot.complete(s, LearningFeatureProtocol.parseRecap(recapRaw(snapshot).toString(), snapshot))
        val restored = DataJson.read(DataJson.write(SavedData(sessions = listOf(documented)))).sessions.single()
        assertEquals(documented, restored)
        val old = DataJson.read("""{"sessions":[{"id":"old","messages":[]}]}""").sessions.single()
        assertNull(old.latestRecap); assertNull(old.fixedRecap); assertTrue(old.speakingPractices.isEmpty())
    }
    @Test fun generatingRecapDoesNotSettleOrAddFavorites() {
        val end = ended(false); val snapshot = RecapSnapshot.capture(end, RecapKind.SCENARIO)
        val documented = snapshot.complete(end, "复盘")
        val data = Scenarios.settle(SavedData(sessions = listOf(documented)), documented)
        assertTrue(data.library.isEmpty()); assertTrue(data.scenarioSuccesses.isEmpty())
        assertEquals(end.scenario, documented.scenario)
    }
    @Test fun completionPresentationFlagAndFixedBoundaryPersistWithoutRepeatSuccess() {
        val end = ended().let { it.copy(scenario = it.scenario!!.copy(completionPresented = true)) }
        val data = Scenarios.settle(SavedData(sessions = listOf(end)), end)
        val restored = DataJson.read(DataJson.write(data)); val continued = Scenarios.continueFreely(restored.sessions.single())
        assertTrue(continued.scenario!!.completionPresented)
        assertEquals(1, Scenarios.settle(restored, continued).scenarioSuccesses.size)
    }
    @Test fun auxiliaryRequestsUseOwnBudgetsAndRejectTruncatedOutput() = runBlocking {
        var body = JSONObject()
        fun client(finish: String) = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer); body = JSONObject(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", finish).put("message", JSONObject().put("content", "{}"))))
                    .toString().toResponseBody("application/json".toMediaType())).build()
        }.build())
        val api = ApiConfig("https://example.test/v1", "test", "managed")
        client("stop").structuredLearning(api, LearningFeatureProtocol.scaffold(session()))
        assertEquals("learning", body.getString("purpose")); assertFalse(body.has("temperature")); assertFalse(body.has("reply_length"))
        client("stop").structuredLearning(api, LearningFeatureProtocol.recap(RecapSnapshot.capture(session(), RecapKind.LATEST)), "summary")
        assertEquals("summary", body.getString("purpose"))
        assertTrue(runCatching { client("length").structuredLearning(api, listOf("user" to "test")) }.isFailure)
    }
    @Test fun exportSyntheticProductionRequestsAndMarkdownForSeparateAcceptance() = runBlocking {
        val s = session(); val snapshot = RecapSnapshot.capture(s, RecapKind.LATEST)
        val fixtures = JSONArray()
        var outgoing = JSONObject()
        val client = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer); outgoing = JSONObject(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"choices":[{"finish_reason":"stop","message":{"content":"{}"}}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build())
        val prompts = listOf("scaffold" to LearningFeatureProtocol.scaffold(s), "optimize" to LearningFeatureProtocol.optimize(s, user),
            "correction" to LearningFeatureProtocol.correction(s, user, s.config.copy(realtimeCorrection = true)), "recap" to LearningFeatureProtocol.recap(snapshot))
        for ((name, messages) in prompts) {
            client.structuredLearning(ApiConfig("https://example.test/v1", "test", "managed"), messages, if (name == "recap") "summary" else "learning")
            fixtures.put(JSONObject().put("name", name).put("body", outgoing))
        }
        val output = java.io.File("build/outputs").apply { mkdirs() }
        java.io.File(output, "learning-recap-synthetic-requests.json").writeText(fixtures.toString(2))
        java.io.File(output, "recap-format-sample.md").writeText(LearningFeatureProtocol.parseRecap(recapRaw(snapshot).toString(), snapshot), Charsets.UTF_8)
        assertEquals(4, fixtures.length())
    }
}
