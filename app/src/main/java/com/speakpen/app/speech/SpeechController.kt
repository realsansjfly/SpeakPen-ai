package com.speakpen.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.speakpen.app.data.InputKind
import com.speakpen.app.data.SpeechMode
import java.io.File
import java.util.Locale
import kotlin.math.ln

/** Always records real audio, so a voice message can be replayed after transcription. */
class SpeechController(
    private val context: Context,
    private val onFile: (File, InputKind, Long) -> Unit,
    private val onPhase: (SpeechPhase) -> Unit,
    private val onPlayback: (String?) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var recordedFile: File? = null
    private var recordingStarted = 0L
    private var kind = InputKind.ENGLISH
    private var player: MediaPlayer? = null
    private var playbackId: String? = null
    private var utteranceId: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val meter = object : Runnable {
        override fun run() {
            val current = recorder ?: return
            val amplitude = runCatching { current.maxAmplitude }.getOrDefault(0)
            onLevel((ln((amplitude + 1).toDouble()) / ln(32768.0)).toFloat().coerceIn(0f, 1f))
            handler.postDelayed(this, 100)
        }
    }
    init {
        tts = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val language = tts?.setLanguage(Locale.US)
                ttsReady = language != null && language >= TextToSpeech.LANG_AVAILABLE
                tts?.setSpeechRate(0.92f)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { handler.post { if (id == utteranceId) stopPlayback() } }
            @Deprecated("Platform callback")
            override fun onError(id: String?) { handler.post { if (id == utteranceId) { stopPlayback(); onError("朗读失败，请检查系统英文语音。") } } }
            override fun onStop(id: String?, interrupted: Boolean) { handler.post { if (id == utteranceId) stopPlayback() } }
        })
    }
    @Suppress("UNUSED_PARAMETER")
    fun start(mode: SpeechMode, inputKind: InputKind) {
        if (recorder != null) return
        stopPlayback(); kind = inputKind
        try {
            val temp = File.createTempFile("utterance_", ".m4a", context.cacheDir)
            recordedFile = temp
            @Suppress("DEPRECATION")
            val capture = MediaRecorder()
            recorder = capture
            capture.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1); setAudioSamplingRate(16000); setAudioEncodingBitRate(64000)
                setOutputFile(temp.absolutePath); setMaxDuration(60000)
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop() }
                prepare(); start()
            }
            recordingStarted = SystemClock.elapsedRealtime()
            onPhase(SpeechPhase.LISTENING); handler.post(meter)
        } catch (_: Exception) { cancel(); onError("无法开始录音，请检查麦克风权限或是否被其他应用占用。") }
    }
    fun stop() {
        val capture = recorder ?: return
        recorder = null
        handler.removeCallbacks(meter); onLevel(0f)
        val duration = SystemClock.elapsedRealtime() - recordingStarted
        val audio = recordedFile; recordedFile = null
        try {
            capture.stop()
            if (audio == null || !audio.exists() || audio.length() == 0L || duration < 400L) {
                audio?.delete(); onNotice("说话时间太短，请按住至少半秒后再松开。")
                return
            }
            onPhase(SpeechPhase.IDLE)
            onFile(audio, kind, duration)
        } catch (_: Exception) { audio?.delete(); onError("录音没有完成，请重新按住说话。") }
        finally { runCatching { capture.release() }; onPhase(SpeechPhase.IDLE) }
    }
    fun cancel() {
        val capture = recorder; recorder = null
        handler.removeCallbacks(meter)
        runCatching { capture?.stop() }; runCatching { capture?.release() }
        recordedFile?.delete(); recordedFile = null
        onLevel(0f); onPhase(SpeechPhase.IDLE)
    }
    fun play(file: File, id: String) {
        if (playbackId == id) { stopPlayback(); return }
        stopPlayback()
        if (!file.exists()) { onError("这条录音文件不可用。旧版文字记录没有保留原录音。"); return }
        try {
            val audio = MediaPlayer(); player = audio
            audio.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            audio.setDataSource(file.absolutePath)
            audio.setOnCompletionListener { if (player === audio) stopPlayback() }
            audio.setOnErrorListener { _, _, _ -> if (player === audio) { stopPlayback(); onError("录音播放失败，请重试。") }; true }
            playbackId = id; onPlayback(id)
            audio.setOnPreparedListener { if (player === audio) audio.start() }
            audio.prepareAsync()
        } catch (_: Exception) { stopPlayback(); onError("无法播放这条录音。") }
    }
    fun speak(text: String, id: String = "manual") {
        if (playbackId == id) { stopPlayback(); return }
        stopPlayback()
        if (!ttsReady) { onError("手机尚未安装可用的英文语音，请到系统文字转语音设置安装。"); return }
        val token = "tts_${System.nanoTime()}"; utteranceId = token
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, token)
        if (result == TextToSpeech.ERROR) { stopPlayback(); onError("无法开始朗读，请检查系统语音引擎。") }
        else { playbackId = id; onPlayback(id) }
    }
    fun stopPlayback() {
        utteranceId = null
        tts?.stop(); player?.release(); player = null
        playbackId = null; onPlayback(null)
    }
    fun stopSpeaking() = stopPlayback()
    fun release() { cancel(); stopPlayback(); tts?.shutdown(); tts = null }
}

