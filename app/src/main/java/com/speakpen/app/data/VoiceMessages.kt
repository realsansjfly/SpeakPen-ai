package com.speakpen.app.data

/** Voice state is persisted independently from text to retain recordings on network failures. */
enum class TranscriptState { NONE, PENDING, READY, FAILED }

object VoiceMessages {
    fun ready(message: ChatMessage, text: String): ChatMessage {
        require(text.isNotBlank()) { "没有识别到语音，请重试或手动补充文字。" }
        return message.copy(text = text.trim(), transcriptState = TranscriptState.READY)
    }
    fun restore(message: ChatMessage): ChatMessage = if (message.transcriptState == TranscriptState.PENDING) message.copy(transcriptState = TranscriptState.FAILED) else message
    fun displaySeconds(duration: Long): Int = ((duration + 999L) / 1000L).coerceAtLeast(1).toInt()
}
