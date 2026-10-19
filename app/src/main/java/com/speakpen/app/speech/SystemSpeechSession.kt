package com.speakpen.app.speech

/** Capture and final recognition are separate so stopping always has visible feedback. */
enum class SpeechPhase { IDLE, LISTENING, FINALIZING }

/** Coordinates platform callbacks on the main thread; platform operations and time are injectable. */
class SystemSpeechSession(
    private val stopListening: () -> Unit,
    private val release: () -> Unit,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onPhase: (SpeechPhase) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onText: (String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object { const val RESULT_TIMEOUT_MS = 8_000L }
    var phase: SpeechPhase = SpeechPhase.IDLE
        private set
    private var closed = false
    private var latestPartial = ""
    private var cancelDeadline: (() -> Unit)? = null

    fun start() { if (!closed && phase == SpeechPhase.IDLE) setPhase(SpeechPhase.LISTENING) }
    fun ready() { if (!closed && phase == SpeechPhase.LISTENING) onPhase(phase) }
    fun partial(text: String) {
        if (closed) return
        if (text.isNotBlank()) latestPartial = text
        onPartial(text)
    }
    fun stop() {
        if (!beginFinalizing()) return
        try { stopListening() } catch (_: Exception) { error("无法结束系统语音识别，请重试或切换云端转写。") }
    }
    fun endOfSpeech() { beginFinalizing() }
    fun results(text: String) {
        if (closed) return
        if (text.isBlank()) {
            partialOrError("识别结果为空，请重新尝试。")
        } else {
            finish(); onText(text)
        }
    }
    fun error(message: String) {
        if (closed) return
        if (phase == SpeechPhase.FINALIZING && latestPartial.isNotBlank()) partialOrError(message)
        else { finish(); onError(message) }
    }
    fun cancel() { if (!closed) finish() }
    private fun beginFinalizing(): Boolean {
        if (closed || phase != SpeechPhase.LISTENING) return false
        setPhase(SpeechPhase.FINALIZING)
        cancelDeadline = schedule(RESULT_TIMEOUT_MS) {
            if (!closed && phase == SpeechPhase.FINALIZING) partialOrError("系统语音识别超时，请重试，或在设置中切换云端转写。")
        }
        return true
    }
    private fun partialOrError(message: String) {
        val fallback = latestPartial
        finish()
        if (fallback.isBlank()) onError(message)
        else {
            onText(fallback)
            onNotice("系统未返回最终识别结果，已保留最近识别文本，请检查后再发送。")
        }
    }
    private fun finish() {
        closed = true
        cancelDeadline?.invoke(); cancelDeadline = null
        // A failing vendor recognizer must not leave the UI permanently locked.
        runCatching(release)
        setPhase(SpeechPhase.IDLE); onPartial("")
    }
    private fun setPhase(value: SpeechPhase) { phase = value; onPhase(value) }
}
