package com.speakpen.app.ui

import java.net.URI

/** Explicit offline routes; the WebView never resolves this host on the network. */
internal object HomePageResources {
    const val origin = "https://appassets.androidplatform.net"
    const val document = "$origin/home/topic-cylinder.html"
    fun route(url: String, method: String = "GET"): String? = runCatching {
        val uri = URI(url)
        if (method != "GET" || uri.scheme != "https" || uri.host != "appassets.androidplatform.net" ||
            uri.port != -1 || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        when (uri.rawPath) {
            "/home/topic-cylinder.html" -> "document"
            "/fonts/nunito-regular.ttf" -> "regular"
            "/fonts/nunito-bold.ttf" -> "bold"
            else -> null
        }
    }.getOrNull()
}

internal enum class HomePagePhase { LOADING, READY, FAILED }

/** A load event alone cannot hide the working native home. Late callbacks cannot undo a failure. */
internal data class HomePageLoad(val phase: HomePagePhase = HomePagePhase.LOADING, val reason: String = "") {
    val showNative get() = phase != HomePagePhase.READY
    fun rendered(apiReady: Boolean, width: Int, height: Int, topics: Int, expectedTopics: Int): HomePageLoad =
        if (phase == HomePagePhase.LOADING && apiReady && width > 0 && height > 0 &&
            expectedTopics > 0 && topics == expectedTopics) copy(phase = HomePagePhase.READY) else this
    fun fail(reason: String) = copy(phase = HomePagePhase.FAILED, reason = reason.take(120))
}
