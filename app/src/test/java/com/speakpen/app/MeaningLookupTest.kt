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

class MeaningLookupTest {
    private fun item(content: String = "look forward to", meaning: String = "") = ReviewItem(id = "r1", content = content, meaning = meaning)
    private fun service(answer: String, status: String = "stop") = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", status)
                .put("message", JSONObject().put("content", answer)))).toString().toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun aiMeaningRoundTripsAndHandwrittenMeaningWins() {
        val saved = DataJson.read(DataJson.write(SavedData(library = listOf(ReviewItem(id = "r", content = "weekend",
            aiMeaning = "周末", aiExample = "See you this weekend.", aiProvenance = "AI 补充，应用服务，时间：1")))))
        val restored = saved.library.single()
        assertEquals("周末", restored.aiMeaning)
        assertEquals("See you this weekend.", restored.aiExample)
        assertEquals("AI 补充，应用服务，时间：1", restored.aiProvenance)
        assertEquals("周末", restored.effectiveMeaning)
        val handwritten = restored.copy(meaning = "我自己写的")
        assertEquals("我自己写的", handwritten.effectiveMeaning)
        assertEquals("See you this weekend.", handwritten.effectiveExample)
    }
    @Test fun olderLibraryEntriesWithoutLookupFieldsStillLoad() {
        val restored = DataJson.read("""{"library":[{"id":"r","content":"weekend","meaning":"周末"}]}""").library.single()
        assertEquals("", restored.aiMeaning)
        assertEquals("", restored.aiProvenance)
        assertEquals("周末", restored.effectiveMeaning)
        assertFalse(MeaningLookup.needsLookup(restored))
    }
    @Test fun queryValidationRejectsEmptyChineseAndOverlongExpressions() {
        assertEquals("look forward to", MeaningLookup.queryOf("  look   forward to  "))
        assertNull(MeaningLookup.queryOf(""))
        assertNull(MeaningLookup.queryOf("   "))
        assertNull(MeaningLookup.queryOf("这什么意思"))
        assertNull(MeaningLookup.queryOf("a".repeat(MeaningLookup.MAX_QUERY + 1)))
        assertNotNull(MeaningLookup.queryOf("a".repeat(MeaningLookup.MAX_QUERY)))
        assertEquals("look forward to", MeaningLookup.queryOf("look\tforward\n to"))
    }
    @Test fun kindFollowsWhetherTheExpressionHasSpaces() {
        assertEquals(ReviewKind.WORD, MeaningLookup.kindFor("weekend"))
        assertEquals(ReviewKind.PHRASE, MeaningLookup.kindFor("look forward to"))
        assertEquals(ReviewKind.WORD, MeaningLookup.kindFor("  weekend  "))
    }
    @Test fun lookupSkipsItemsThatAlreadyHaveAnyMeaning() {
        assertTrue(MeaningLookup.needsLookup(item()))
        assertFalse(MeaningLookup.needsLookup(item(meaning = "期待")))
        assertFalse(MeaningLookup.needsLookup(item().copy(aiMeaning = "期待")))
        assertFalse(MeaningLookup.needsLookup(item(content = "中文只有中文")))
    }
    @Test fun demoAndApplyNeverOverwriteHandwrittenMeaning() {
        val target = item()
        val demo = MeaningLookup.demo(target)
        assertTrue(demo.demo)
        assertEquals("r1", demo.itemId)
        assertEquals("look forward to", demo.expression)
        assertEquals(MeaningLookup.DEMO_PROVENANCE, demo.provenance)
        val adopted = MeaningLookup.apply(target, demo)
        assertEquals(demo.meaning, adopted.aiMeaning)
        assertEquals("", adopted.meaning)
        assertEquals(demo.meaning, adopted.effectiveMeaning)
        val kept = MeaningLookup.apply(item(meaning = "期待"), MeaningLookup.suggestion(item(), "期待", "I look forward to it.", "AI"))
        assertEquals("期待", kept.meaning)
        assertEquals("", kept.aiMeaning)
    }
    @Test fun definitionRequestCarriesPurposeAndTheExpressionItself() = runBlocking {
        val client = AiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val body = JSONObject(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
            assertEquals("learning", body.getString("purpose"))
            assertEquals(2, body.getJSONArray("messages").length())
            assertTrue(body.getJSONArray("messages").getJSONObject(1).getString("content").contains("look forward to"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"message":{"content":"{\"meaning\":\"期待\",\"example\":\"I look forward to it.\"}"},"finish_reason":"stop"}]}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build())
        assertEquals("期待" to "I look forward to it.",
            client.vocabularyDefinition(ApiConfig("https://example.test/v1", "token", "managed"), "look forward to"))
    }
    @Test fun truncatedDefinitionIsRejectedBeforeAdoption() = runBlocking {
        val client = service("""{"meaning":"期待","example":"I look forward to it."}""", status = "length")
        assertTrue(runCatching {
            client.vocabularyDefinition(ApiConfig("https://example.test/v1", "token", "managed"), "look forward to")
        }.isFailure)
    }
}
