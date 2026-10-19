package com.speakpen.app.network

import com.speakpen.app.BuildConfig
import com.speakpen.app.data.ApiConfig
import com.speakpen.app.data.AppSettings

object ManagedService {
    fun configured(settings: AppSettings): Boolean = settings.serviceUrl.ifBlank { BuildConfig.SERVICE_BASE_URL }.isNotBlank()
    fun config(settings: AppSettings): ApiConfig {
        check(configured(settings)) { "请打开电脑生成的服务连接入口。" }
        val overridden = settings.serviceUrl.isNotBlank()
        return ApiConfig(baseUrl = if (overridden) settings.serviceUrl else BuildConfig.SERVICE_BASE_URL,
            apiKey = if (overridden) settings.serviceAccessToken else BuildConfig.SERVICE_ACCESS_TOKEN, model = "managed")
    }
}
