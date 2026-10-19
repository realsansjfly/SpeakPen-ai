package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.AiClient
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class AiClientContractTest {
    private fun client(handler: (Request) -> Pair<Int, String>): AiClient {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return AiClient(transport)
    }
    @Test fun streamingClientPublishesIncrementalReplyBeforeCompletion() = runBlocking {
        val tokens = mutableListOf<String>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            assertTrue(JSONObject(body).getBoolean("stream"))
            val stream = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\" there\"}}]}\n\ndata: [DONE]\n\n"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .header("Content-Type", "text/event-stream").body(stream.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val result = AiClient(transport).streamChat(ApiConfig("https://service.example/v1", model = "managed"), listOf("user" to "Hi")) { tokens += it }
        assertEquals(listOf("Hello", "Hello there"), tokens)
        assertEquals("Hello there", result)
    }
    @Test fun chatSendsAuthenticationModelAndTranscriptToConfiguredEndpoint() = runBlocking {
        val api = client { request ->
            assertEquals("https://provider.example/custom/chat/completions", request.url.toString())
            assertEquals("Bearer fixture-key", request.header("Authorization"))
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            val json = JSONObject(body)
            assertEquals("my-model", json.getString("model"))
            assertEquals("I went hiking.", json.getJSONArray("messages").getJSONObject(0).getString("content"))
            200 to """{"choices":[{"message":{"content":"How was the weather?"}}]}"""
        }
        assertEquals("How was the weather?", api.chat(ApiConfig("https://provider.example/custom", "fixture-key", "my-model"), listOf("user" to "I went hiking.")))
    }
    @Test fun unauthenticatedProxyDoesNotReceiveAnEmptyBearerHeader() = runBlocking {
        val api = client { request -> assertNull(request.header("Authorization")); 200 to """{"choices":[{"message":{"content":"OK"}}]}""" }
        assertEquals("OK", api.chat(ApiConfig("https://proxy.example/v1", "", "local-model"), listOf("user" to "test")))
    }
    @Test fun cloudTranscriptionUploadsActualRecordingWithEnglishLanguageAndVocabulary() = runBlocking {
        val audio = File.createTempFile("speech-test", ".m4a")
        try {
            audio.writeBytes(byteArrayOf(1, 2, 3, 4))
            val api = client { request ->
                assertEquals("https://asr.example/v1/audio/transcriptions", request.url.toString())
                val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                val languagePart = (request.body as MultipartBody).parts.first { it.headers?.get("Content-Disposition")?.contains("name=\"language\"") == true }
                assertEquals("en", Buffer().also { languagePart.body.writeTo(it) }.readUtf8())
                assertTrue(body.contains("name=\"file\"; filename=\"practice.m4a\""))
                assertTrue(body.contains("Content-Type: audio/mp4"))
                assertTrue(body.contains("Would you mind"))
                assertTrue(body.contains("preserve learner wording"))
                200 to """{"text":"Would you mind opening the window?"}"""
            }
            assertEquals("Would you mind opening the window?", api.transcribe(ApiConfig("https://asr.example/v1", "key", "asr-model"), audio, InputKind.ENGLISH, listOf("Would you mind")))
            assertTrue("A failed request must leave its recording available for retry", audio.exists())
        } finally { audio.delete() }
    }
    @Test fun chineseHelpUsesChineseLanguageInsteadOfEnglish() = runBlocking {
        val audio = File.createTempFile("speech-test", ".m4a")
        try {
            audio.writeText("fixture")
            val api = client { request ->
                val languagePart = (request.body as MultipartBody).parts.first { it.headers?.get("Content-Disposition")?.contains("name=\"language\"") == true }
                assertEquals("zh", Buffer().also { languagePart.body.writeTo(it) }.readUtf8())
                200 to """{"text":"我想礼貌地拒绝"}"""
            }
            assertEquals("我想礼貌地拒绝", api.transcribe(ApiConfig("https://asr.example/v1", "", "asr"), audio, InputKind.HELP, emptyList()))
        } finally { audio.delete() }
    }
    @Test fun unauthorizedResponseHasActionableErrorWithoutLeakingProviderResponse() = runBlocking {
        val api = client { 401 to "fixture-key should never appear in the UI" }
        try { api.chat(ApiConfig("https://provider.example/v1", "fixture-key", "model"), listOf("user" to "test")); fail("must fail") }
        catch (error: IOException) { assertTrue(error.message!!.contains("401")); assertFalse(error.message!!.contains("fixture-key")) }
    }
    @Test fun failedTranscriptionPreservesOriginalAudioForRetry() = runBlocking {
        val audio = File.createTempFile("speech-test", ".m4a")
        try {
            audio.writeText("fixture")
            val api = client { 429 to "rate limited" }
            try { api.transcribe(ApiConfig("https://asr.example/v1", "", "asr"), audio, InputKind.ENGLISH, emptyList()); fail("must fail") }
            catch (error: IOException) { assertTrue(error.message!!.contains("429")); assertEquals("fixture", audio.readText()) }
        } finally { audio.delete() }
    }
}



