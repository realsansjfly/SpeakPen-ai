package com.speakpen.app.network

import java.net.URI
import java.net.URLDecoder

data class ServiceConnection(val baseUrl: String, val accessToken: String)
object ServicePairing {
    fun validate(base: String, token: String): ServiceConnection {
        val trimmed = base.trim().trimEnd('/')
        val url = URI(trimmed)
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.rawQuery == null && url.rawFragment == null) { "连接地址必须是完整 HTTPS 地址。" }
        require(url.path == "/v1") { "服务地址应以 /v1 结束。" }
        require(token.matches(Regex("[a-f0-9]{64}"))) { "服务配对码无效，请重新复制电脑生成的完整入口。" }
        return ServiceConnection(trimmed, token)
    }
    private fun parameters(raw: String): Map<String, String> {
        val pairs = raw.split('&').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8") else null
        }
        require(pairs.map { it.first }.distinct().size == pairs.size) { "配对参数重复。" }
        return pairs.toMap()
    }
    fun parse(raw: String): ServiceConnection {
        val link = URI(raw.trim())
        return when {
            link.scheme == "speakpen" && link.host == "connect" -> {
                require(link.rawFragment == null && link.userInfo == null) { "无效的配对链接。" }
                val values = parameters(link.rawQuery.orEmpty())
                validate(values["url"].orEmpty(), values["token"].orEmpty())
            }
            link.scheme == "https" && link.rawQuery == null && link.path in listOf("", "/") -> {
                val values = parameters(link.rawFragment.orEmpty())
                validate("https://${link.rawAuthority}/v1", values["token"].orEmpty())
            }
            else -> throw IllegalArgumentException("请粘贴带配对信息的完整服务入口。")
        }
    }
}
