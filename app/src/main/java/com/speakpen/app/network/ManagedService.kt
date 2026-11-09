package com.speakpen.app.network

import com.speakpen.app.BuildConfig
import com.speakpen.app.data.ApiConfig
import com.speakpen.app.data.AppSettings

object ManagedService {
    fun configured(settings: AppSettings): Boolean = resolve(settings, BuildConfig.SERVICE_BASE_URL, BuildConfig.SERVICE_ACCESS_TOKEN, BuildConfig.FIXED_SERVICE) != null
    // Explicit inputs make the upgrade rule testable without embedding real credentials in tests.
    internal fun resolve(settings: AppSettings, base: String, token: String, fixed: Boolean): ApiConfig? {
        if (fixed) return base.takeIf { it.isNotBlank() }?.let { ApiConfig(baseUrl = it, apiKey = token, model = "managed") }
        val overridden = settings.serviceUrl.isNotBlank()
        val url = if (overridden) settings.serviceUrl else base
        return url.takeIf { it.isNotBlank() }?.let {
            ApiConfig(baseUrl = it, apiKey = if (overridden) settings.serviceAccessToken else token, model = "managed")
        }
    }
    fun config(settings: AppSettings): ApiConfig {
        return checkNotNull(resolve(settings, BuildConfig.SERVICE_BASE_URL, BuildConfig.SERVICE_ACCESS_TOKEN, BuildConfig.FIXED_SERVICE)) {
            "此安装包尚未配置 AI 服务，请联系应用维护者。"
        }
    }
}
