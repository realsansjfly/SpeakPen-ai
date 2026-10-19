package com.speakpen.app.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Keeps TLS hostname verification intact; only failed tunnel DNS lookups have a fallback. */
class TunnelDns(
    private val system: Dns = Dns.SYSTEM,
    private val resolveJson: (String) -> String = ::resolveEncrypted,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        try { return system.lookup(hostname) }
        catch (original: UnknownHostException) {
            if (!hostname.matches(Regex("[a-z0-9-]+\\.trycloudflare\\.com"))) throw original
            try {
                val addresses = parseAddresses(resolveJson(hostname))
                if (addresses.isNotEmpty()) return addresses
            } catch (_: Exception) { }
            throw original
        }
    }
    companion object {
        fun parseAddresses(raw: String): List<InetAddress> {
            val result = JSONObject(raw)
            if (result.optInt("Status", -1) != 0) return emptyList()
            val answers = result.optJSONArray("Answer") ?: return emptyList()
            return (0 until answers.length()).mapNotNull { index ->
                val record = answers.optJSONObject(index) ?: return@mapNotNull null
                if (record.optInt("type") != 1) return@mapNotNull null
                val chunks = record.optString("data").split('.')
                if (chunks.size != 4 || chunks.any { !it.matches(Regex("[0-9]{1,3}")) }) return@mapNotNull null
                val numbers = chunks.map { it.toInt() }
                if (numbers.any { it !in 0..255 }) return@mapNotNull null
                val a = numbers[0]; val b = numbers[1]
                if (a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && b == 254 ||
                    a == 172 && b in 16..31 || a == 192 && b == 168 || a == 100 && b in 64..127) return@mapNotNull null
                InetAddress.getByAddress(numbers.map { it.toByte() }.toByteArray())
            }.distinct()
        }
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
        private fun resolveEncrypted(hostname: String): String {
            cache[hostname]?.takeIf { it.first > System.currentTimeMillis() }?.let { return it.second }
            val client = OkHttpClient.Builder().dns(Dns.SYSTEM).callTimeout(3, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build()
            for (resolver in listOf("https://dns.alidns.com/resolve", "https://doh.pub/dns-query")) {
                try {
                    val request = Request.Builder().url("$resolver?name=$hostname&type=A")
                        .header("Accept", "application/dns-json").get().build()
                    val raw = client.newCall(request).execute().use {
                        check(it.isSuccessful) { "DNS query failed" }
                        val reply = it.body?.string().orEmpty()
                        check(reply.length < 65536) { "DNS reply too large" }; reply
                    }
                    if (parseAddresses(raw).isNotEmpty()) {
                        val answers = JSONObject(raw).getJSONArray("Answer")
                        val ttl = (0 until answers.length()).mapNotNull {
                            answers.optJSONObject(it)?.takeIf { record -> record.optInt("type") == 1 }?.optLong("TTL", 30)
                        }.minOrNull()?.coerceIn(1, 120) ?: 30L
                        cache[hostname] = (System.currentTimeMillis() + ttl * 1000) to raw
                        return raw
                    }
                } catch (_: Exception) { }
            }
            throw UnknownHostException(hostname)
        }
    }
}
