package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VocabularyTest {
    private fun parse(raw: String, format: VocabularyFormat = VocabularyFormat.TXT) = VocabularyImport.parse(raw, format)
    private fun book(raw: String = "reservation\naccommodation\njourney\nbanana\ncheck in") = VocabularyBook(
        id = "test", name = "旅行", source = "课堂笔记", originalText = raw, entries = parse(raw).entries)
    @Test fun txtPreservesPhrasesOriginalRowsAndSenses() {
        val raw = "\uFEFFcheck in\t办理入住\tWe can check in.\nCHECK   IN\t办理入住\tWe can check in.\ncheck in\t登记\n\n"
        val preview = parse(raw)
        assertEquals(2, preview.entries.size); assertEquals(1, preview.duplicates)
        assertEquals("check in", preview.entries.first().expression)
        assertEquals(3, preview.entries.last().line)
        assertEquals("check in\t登记", preview.entries.last().original)
    }
    @Test fun csvQuotedCommaEscapedQuotesAndNewlinesKeepLineEvidence() {
        val preview = parse("expression,meaning,example,tags\r\n\"check in\",入住,\"Say \"\"hello\"\", then\ncheck in.\",travel\r\nbook,预订,,", VocabularyFormat.CSV)
        assertTrue(preview.issues.isEmpty()); assertEquals(2, preview.entries.size)
        assertEquals("Say \"hello\", then\ncheck in.", preview.entries.first().example)
        assertEquals(4, preview.entries.last().line)
    }
    @Test fun csvWithoutHeaderAndAliasesBothWork() {
        assertEquals("预订", parse("book,预订", VocabularyFormat.CSV).entries.single().meaning)
        assertEquals("book", parse("单词,释义\nbook,预订", VocabularyFormat.CSV).entries.single().expression)
    }
    @Test fun malformedCsvNeverSilentlySavesPartialRows() {
        for (raw in listOf("book,ok\n\"broken", "\"book\"junk,x", "bo\"ok,x", "expression,meaning,meaning\nbook,a,b")) {
            val result = parse(raw, VocabularyFormat.CSV)
            assertFalse(result.canSave); assertNotNull(result.fatal)
        }
    }
    @Test fun errorsHavePhysicalLinesAndValidEntriesRemainPreviewable() {
        val preview = parse("\n,坏\n=HYPERLINK(x)\nhello\u0000\nhello\n")
        assertEquals(listOf(2, 3, 4), preview.issues.map { it.line })
        assertEquals("hello", preview.entries.single().expression); assertTrue(preview.canSave)
    }
    @Test fun byteLineAndFieldLimitsRejectOversizedImports() {
        assertFalse(parse("a".repeat(VocabularyImport.MAX_BYTES + 1)).canSave)
        assertFalse(parse((1..20002).joinToString("\n") { "word$it" }).canSave)
        assertEquals(1, parse("a".repeat(121)).issues.size)
        assertEquals(1, parse("word\t" + "义".repeat(1001)).issues.size)
        val maximumCsv = "expression,meaning\n" + (1..20_000).joinToString("\n") { "word$it,含义" } + "\n"
        assertEquals(20_000, parse(maximumCsv, VocabularyFormat.CSV).entries.size)
        assertFalse(parse("\n".repeat(100_000)).canSave)
    }
    @Test fun invalidUtf8AndOversizedFilesNeverBecomeReplacementCharacters() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), ByteArray(VocabularyImport.MAX_BYTES + 1))) {
            try { VocabularyImport.decode(bytes); fail("must reject") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("hello 世界", VocabularyImport.decode("hello 世界".toByteArray()))
    }
    @Test fun immutableSupplementDoesNotChangeOriginalOrOldDigest() {
        val original = book(); val digest = original.digest; val entry = original.entries.first()
        val added = original.supplement(entry.id, "预订", "I have a reservation.", "AI · test")
        assertNotEquals(original.version, added.version); assertNotEquals(digest, added.digest)
        assertEquals(digest, original.digest); assertEquals(original.originalText, added.originalText)
        assertEquals("", added.entries.first().meaning); assertEquals("预订", added.entries.first().aiMeaning)
        assertEquals(original, VocabularyJson.readBook(VocabularyJson.book(original)))
        assertEquals(added, VocabularyJson.readBook(VocabularyJson.book(added)))
    }
    @Test fun supplementCannotOverwriteOriginalOrConfirmedAiMeaning() {
        val original = book("book\t原释义")
        try { original.supplement(original.entries.single().id, "新释义", "", "AI"); fail() } catch (_: IllegalArgumentException) { }
        val empty = book("word"); val once = empty.supplement(empty.entries.single().id, "单词", "", "AI")
        try { once.supplement(once.entries.single().id, "新", "", "AI"); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun tamperedVersionDataIsRejected() {
        val j = VocabularyJson.book(book()); j.getJSONArray("entries").getJSONObject(0).put("expression", "tampered")
        try { VocabularyJson.readBook(j); fail() } catch (_: IllegalStateException) { }
    }
    @Test fun oldConfigRemainsOffAndSwitchingDoesNotChangeDifficulty() {
        val old = ConversationConfigJson.read(JSONObject("{}")); assertNull(old.vocabulary)
        val on = old.copy(difficulty = ExpressionDifficulty.SIMPLE, vocabulary = book().selection())
        assertEquals(on, ConversationConfigJson.read(ConversationConfigJson.write(on)))
        assertEquals(ExpressionDifficulty.SIMPLE, on.copy(vocabulary = null).difficulty)
        assertEquals(on.advanced, on.copy(vocabulary = book("different").selection()).advanced)
    }
    @Test fun requestSnapshotSurvivesBookChangeAndConfigChange() {
        val b = book(); val session = ChatSession(id = "s", config = ConversationConfig(vocabulary = b.selection()),
            messages = listOf(ChatMessage(id = "u", role = "user", text = "I need a hotel room.")))
        val config = VocabularyCandidates.prepare(session, listOf(b), session.messages.single().text)
        val snapshot = GenerationSnapshot.capture(session, config)
        assertTrue(config.vocabulary!!.candidates.any { it.expression == "reservation" })
        assertTrue(snapshot.canApply(session.copy(config = session.config.copy(vocabulary = null)), "s", 1, 1))
        val message = ChatMessage(role = "assistant", text = "Do you have a reservation?", generationConfig = snapshot.config)
        val data = SavedData(listOf(session.copy(messages = session.messages + message)))
        assertEquals(data, DataJson.read(DataJson.write(data)))
        assertEquals(config, data.sessions.single().messages.last().generationConfig)
    }
    @Test fun missingOrChangedVersionFailsClearlyRatherThanSilentlyUsingAnother() {
        val b = book(); val s = ChatSession(config = ConversationConfig(vocabulary = b.selection()))
        for (books in listOf(emptyList(), listOf(b.copy(version = "2")), listOf(b.copy(entries = emptyList())))) {
            try { VocabularyCandidates.prepare(s, books, "hotel"); fail() } catch (_: IllegalStateException) { }
        }
    }
    @Test fun unrelatedContextDoesNotForceCandidateAndChineseTopicsWork() {
        val b = book()
        assertTrue(VocabularyCandidates.select(b, "quantum entanglement").isEmpty())
        assertTrue(VocabularyCandidates.select(b, "我想去酒店办理入住").any { it.expression == "reservation" })
        val chinese = book("check in\t办理入住")
        assertEquals("check in", VocabularyCandidates.select(chinese, "办理入住").single().expression)
    }
    @Test fun promptBudgetIsBoundedAndNeverUploadsBookOrRawSource() {
        val raw = (1..500).joinToString("\n") { "hotel phrase $it\t" + "释义".repeat(100) }
        val b = book(raw); val selected = VocabularyCandidates.select(b, "hotel")
        assertTrue(selected.size in 1..6); assertTrue(VocabularyPrompts.data(selected).length <= VocabularyCandidates.MAX_CHARS)
        val config = ConversationConfig(vocabulary = b.selection().copy(candidates = selected))
        val prompts = CoachPrompts.system(ConversationMode.FREE, emptyList(), config)
        assertFalse(prompts.contains("hotel phrase 500")); assertFalse(prompts.contains(b.originalText))
        assertTrue(prompts.contains("Never require the learner"))
        assertTrue(VocabularyPrompts.instruction(config, true).contains("exact intent"))
    }
    @Test fun optimizeAndTopicReceiveFrozenCandidatesButHintsStayPrivate() {
        val b = book(); val config = ConversationConfig(vocabulary = b.selection().copy(candidates = b.entries.take(1)))
        val u = ChatMessage(role = "user", text = "I want book a room.")
        val s = ChatSession(config = config, messages = listOf(u, ChatMessage(role = "hint", text = "PRIVATE")))
        assertTrue(LearningPrompts.optimize(s, u).toString().contains("reservation"))
        assertTrue(LearningPrompts.topic(s.messages, config).toString().contains("reservation"))
        assertFalse(CoachPrompts.history(s).toString().contains("PRIVATE"))
        assertEquals("", VocabularyPrompts.instruction(ConversationConfig()))
    }
    @Test fun aiDefinitionRequiresChineseAndValidExample() {
        assertEquals("预订" to "I have a reservation.", VocabularyPrompts.parseDefinition("""{"meaning":"预订","example":"I have a reservation."}"""))
        for (raw in listOf("{}", "{\"meaning\":\"English only\",\"example\":\"Test\"}", "not json")) {
            assertTrue(runCatching { VocabularyPrompts.parseDefinition(raw) }.isFailure)
        }
        assertFalse(VocabularyPrompts.definition(book().entries.first()).toString().contains("课堂笔记"))
    }
    @Test fun truncatedDefinitionIsRejectedBeforeConfirmation() = runBlocking {
        val client = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val body = JSONObject(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
            assertEquals("learning", body.getString("purpose"))
            assertEquals(2, body.getJSONArray("messages").length())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("finish_reason", "length")
                    .put("message", JSONObject().put("content", """{"meaning":"预订","example":"Book a room."}"""))))
                    .toString().toResponseBody("application/json".toMediaType())).build()
        }.build())
        assertTrue(runCatching { client.vocabularyDefinition(ApiConfig("https://example.test/v1", "token", "managed"), book().entries.first()) }.isFailure)
    }
    @Test fun vocabularyDefaultsAndConversationEditsStayIndependent() {
        val config = ConversationConfig(vocabulary = book().selection())
        val old = ChatSession(id = "old", config = ConversationConfig(ExpressionDifficulty.SIMPLE))
        val defaults = AppSettings(conversationDefaults = config)
        val state = PracticeState(settings = defaults, sessions = listOf(old), currentSessionId = old.id)
        val changed = SettingsUpdates.conversation(state, old.id, config.copy(difficulty = old.config.difficulty))
        assertEquals(ExpressionDifficulty.SIMPLE, changed.session.config.difficulty)
        assertEquals(defaults, changed.settings)
        assertEquals(1, changed.session.configRevision)
        val off = SettingsUpdates.apply(changed, defaults.copy(conversationDefaults = config.copy(vocabulary = null)))
        assertNotNull(off.session.config.vocabulary)
        assertNull(off.settings.conversationDefaults.vocabulary)
    }
    @Test fun realClientForwardsOnlyCandidateSubsetUsingExistingServiceBudget() = runBlocking {
        val b = book(); val config = ConversationConfig(vocabulary = b.selection().copy(candidates = b.entries.take(1)))
        val client = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val body = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8(); val json = JSONObject(body)
            assertEquals("learning", json.getString("purpose")); assertFalse(json.has("temperature"))
            assertTrue(body.contains("reservation")); assertFalse(body.contains("banana"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"message":{"content":"词库提示"},"finish_reason":"stop"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build())
        assertEquals("词库提示", client.learning(ApiConfig("https://example.test/v1", "service-token", "managed"),
            LearningPrompts.topic(listOf(ChatMessage(role = "user", text = "hotel")), config)))
    }
    @Test fun builtInResourceCountsHashesAndTopicSelectionAreReproducible() {
        val dir = File("src/main/assets/vocabulary").takeIf { it.exists() } ?: File("app/src/main/assets/vocabulary")
        val manifest = JSONObject(File(dir, "manifest.json").readText())
        val results = org.json.JSONArray()
        for (tag in listOf("cet4", "cet6", "ielts")) {
            val raw = File(dir, "$tag.txt").readText(); val expected = manifest.getJSONObject(tag)
            assertEquals(expected.getString("sha256"), vocabularyHash(raw))
            val b = book(raw); assertEquals(expected.getInt("entries"), b.entries.size); assertTrue(b.entries.size > 3000)
            for (topic in listOf("酒店预订 hotel room", "工作面试 job interview", "环境 climate pollution", "点餐 restaurant meal", "量子纠缠 zxqv")) {
                val picked = VocabularyCandidates.select(b, topic)
                assertTrue(picked.size <= 6); assertTrue(VocabularyPrompts.data(picked).length <= 1200)
                if (topic.contains("zxqv")) assertTrue(picked.isEmpty()) else assertTrue(picked.isNotEmpty())
                results.put(JSONObject().put("book", tag).put("topic", topic).put("count", picked.size)
                    .put("chars", VocabularyPrompts.data(picked).length).put("candidates", org.json.JSONArray(picked.map { it.expression })))
            }
        }
        File("build/outputs/vocabulary-offline-evaluation.json").apply { parentFile!!.mkdirs(); writeText(results.toString(2)) }
    }
}
