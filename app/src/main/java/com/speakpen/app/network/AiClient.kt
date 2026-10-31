package com.speakpen.app.network

import com.speakpen.app.data.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ServiceStatus(val label: String, val conversationControlsSupported: Boolean)

class AiClient(private val client: OkHttpClient = defaultClient()) {
    companion object {
        private fun defaultClient() = OkHttpClient.Builder().dns(TunnelDns()).connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun status(config: ApiConfig): String = statusInfo(config).label
    suspend fun statusInfo(config: ApiConfig): ServiceStatus = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(ApiProtocol.endpoint(config.baseUrl, "status"))
            .header("Authorization", "Bearer ${config.apiKey}").get().build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        check(it.isSuccessful) { "服务不可用。" }
                        val json = JSONObject(it.body?.string().orEmpty())
                        check(json.optBoolean("ready")) { "服务未开通。" }
                        val supported = json.optInt("conversationControlsVersion") >= 1
                        ServiceStatus(if (supported) "已连接千问对话与语音识别" else "已连接，本机服务需更新后才能使用高级参数", supported)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
    suspend fun chat(config: ApiConfig, messages: List<Pair<String, String>>, purpose: String = "chat", advanced: AdvancedSettings? = null): String {
        ApiProtocol.requireModel(config)
        val body = JSONObject(ApiProtocol.chatBody(config.model, messages, if (purpose == "chat") advanced else null)).apply { if (config.model == "managed") put("purpose", purpose) }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        return ApiProtocol.chatText(request(config, "chat/completions", body))
    }
    suspend fun learning(config: ApiConfig, messages: List<Pair<String, String>>): String = chat(config, messages, "learning")
    suspend fun structuredLearning(config: ApiConfig, messages: List<Pair<String, String>>, purpose: String = "learning"): String {
        require(purpose == "learning" || purpose == "summary")
        val body = JSONObject(ApiProtocol.chatBody(config.model, messages)).put("purpose", purpose).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val raw = request(config, "chat/completions", body)
        check(JSONObject(raw).getJSONArray("choices").getJSONObject(0).optString("finish_reason") == "stop") {
            "生成未完整返回，请重试；原内容已保留。"
        }
        return ApiProtocol.chatText(raw)
    }
    suspend fun vocabularyDefinition(config: ApiConfig, entry: VocabularyEntry): Pair<String, String> {
        val body = JSONObject(ApiProtocol.chatBody(config.model, VocabularyPrompts.definition(entry)))
            .put("purpose", "learning").toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val raw = request(config, "chat/completions", body)
        check(JSONObject(raw).getJSONArray("choices").getJSONObject(0).optString("finish_reason") == "stop") {
            "释义未完整返回，请重试；原内容未修改。"
        }
        return VocabularyPrompts.parseDefinition(ApiProtocol.chatText(raw))
    }

    suspend fun scenarioAssessment(config: ApiConfig, session: ChatSession): ScenarioAssessment {
        val body = JSONObject(ApiProtocol.chatBody(config.model, ScenarioProtocol.assessment(session)))
            .put("purpose", ScenarioProtocol.PURPOSE).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val raw = request(config, "chat/completions", body)
        check(JSONObject(raw).getJSONArray("choices").getJSONObject(0).optString("finish_reason") == "stop") {
            "场景判定未完整返回，未记录成功。请重试判定。"
        }
        return ScenarioProtocol.parse(ApiProtocol.chatText(raw), session)
    }

    suspend fun streamChat(config: ApiConfig, messages: List<Pair<String, String>>, advanced: AdvancedSettings? = null, onText: (String) -> Unit): String {
        ApiProtocol.requireModel(config)
        val body = JSONObject(ApiProtocol.chatBody(config.model, messages, advanced)).put("stream", true).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(ApiProtocol.endpoint(config.baseUrl, "chat/completions")).post(body).header("Accept", "text/event-stream")
        if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("服务连接失败，请检查网络后重试。", error))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use { reply ->
                            if (!reply.isSuccessful) throw serviceError(reply.code)
                            val replyBody = requireNotNull(reply.body) { "服务返回为空。" }
                            if (reply.header("Content-Type")?.contains("text/event-stream") != true) {
                                ApiProtocol.chatText(replyBody.string()).also(onText)
                            } else {
                                val parser = ChatStreamParser()
                                val source = replyBody.source()
                                while (continuation.isActive) {
                                    val line = source.readUtf8Line() ?: break
                                    parser.line(line)?.let(onText)
                                }
                                parser.result()
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
    private fun serviceError(code: Int) = IOException(when (code) {
        401, 403 -> "服务配对已失效，请重新打开电脑的连接入口。"
        503 -> "AI 服务尚未开通，可先在设置中体验示例。"
        429 -> "服务暂时繁忙，请稍后重试。"
        502, 504 -> "AI 服务暂时没有响应，消息已保留，请重试。"
        else -> "服务请求失败（$code），请重试。"
    })
    suspend fun speech(config: ApiConfig, text: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val body = JSONObject().put("input", text.take(2000)).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(ApiProtocol.endpoint(config.baseUrl, "audio/speech"))
            .header("Authorization", "Bearer ${config.apiKey}").post(body).build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        check(it.isSuccessful && it.header("Content-Type")?.startsWith("audio/") == true) { "语音生成失败。" }
                        val content = requireNotNull(it.body)
                        check(content.contentLength() <= 6 * 1024 * 1024) { "语音文件过大。" }
                        content.bytes().also { data -> check(data.isNotEmpty()) { "语音为空。" } }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
    suspend fun transcribe(config: ApiConfig, file: File, kind: InputKind, vocabulary: List<String>): String {
        ApiProtocol.requireModel(config)
        require(file.exists() && file.length() > 0) { "录音文件为空，请重新录音。" }
        require(file.length() <= 24L * 1024 * 1024) { "录音过大，请缩短到两分钟以内。" }
        val prompt = if (kind == InputKind.ENGLISH) "English language practice. Transcribe the speaker faithfully; preserve learner wording. Relevant vocabulary: ${vocabulary.take(20).joinToString(", ").take(600)}"
            else "中文英语学习求助，保留中英文混合内容。"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model.trim()).addFormDataPart("language", if (kind == InputKind.ENGLISH) "en" else "zh")
            .addFormDataPart("response_format", "json").addFormDataPart("prompt", prompt)
            .addFormDataPart("file", "practice.m4a", file.asRequestBody("audio/mp4".toMediaType())).build()
        return ApiProtocol.transcriptionText(request(config, "audio/transcriptions", body))
    }
    private suspend fun request(config: ApiConfig, path: String, body: RequestBody): String {
        val builder = Request.Builder().url(ApiProtocol.endpoint(config.baseUrl, path)).post(body).header("Accept", "application/json")
        if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        val request = builder.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("网络连接失败或超时，请检查网络与接口地址。", error))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val raw = response.use { reply ->
                            if (!reply.isSuccessful) {
                                if (config.model == "managed") throw serviceError(reply.code)
                                val hint = when (reply.code) {
                                    401, 403 -> "请检查 API Key 和模型权限。"
                                    404 -> "请检查 Base URL、接口路径和模型 ID。"
                                    429 -> "请求限流或余额不足，请稍后重试并检查账户。"
                                    in 300..399 -> "接口发生重定向，请填写最终的 HTTPS 地址。"
                                    else -> "服务商拒绝请求，请确认接口兼容性。"
                                }
                                throw IOException("HTTP ${reply.code}：$hint")
                            }
                            reply.body?.string() ?: throw IOException("接口没有返回内容。")
                        }
                        if (continuation.isActive) continuation.resume(raw)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
}





