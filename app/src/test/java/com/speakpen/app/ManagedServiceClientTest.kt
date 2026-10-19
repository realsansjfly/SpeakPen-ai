package com.speakpen.app

import com.speakpen.app.data.ApiConfig
import com.speakpen.app.network.AiClient
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ManagedServiceClientTest {
    private val config = ApiConfig("https://paired.example/v1", "local-pairing-token", "managed")
    @Test fun pairedStatusUsesAuthorizationAndRequiresReadyService() = runBlocking {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https://paired.example/v1/status", chain.request().url.toString())
            assertEquals("Bearer local-pairing-token", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"ready\":true}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertTrue(AiClient(transport).status(config).contains("已连接"))
    }
    @Test fun mobileCanAcceptJsonWhenTemporaryTunnelCannotStream() = runBlocking {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("Bearer local-pairing-token", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"choices\":[{\"message\":{\"content\":\"How was your day?\"}}]}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val deltas = mutableListOf<String>()
        val result = AiClient(transport).streamChat(config, listOf("user" to "Hi")) { deltas += it }
        assertEquals("How was your day?", result)
        assertEquals(listOf(result), deltas)
    }
    @Test fun cloudSpeechReturnsAudioBytesAndAuthenticatesToLocalService() = runBlocking {
        val expected = "RIFF fixture audio".toByteArray()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https://paired.example/v1/audio/speech", chain.request().url.toString())
            assertEquals("Bearer local-pairing-token", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .header("Content-Type", "audio/wav").body(expected.toResponseBody("audio/wav".toMediaType())).build()
        }.build()
        assertArrayEquals(expected, AiClient(transport).speech(config, "Hello"))
    }
    @Test fun nonAudioResponseCannotBecomeAPlayableVoiceMessage() = runBlocking {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"error\":\"no speech\"}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        try { AiClient(transport).speech(config, "Hello"); fail("must reject non-audio response") }
        catch (_: IllegalStateException) { }
    }
}

