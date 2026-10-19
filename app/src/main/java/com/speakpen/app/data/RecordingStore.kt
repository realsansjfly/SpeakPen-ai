package com.speakpen.app.data

import android.content.Context
import java.io.File

class RecordingStore(context: Context) {
    private val directory = File(context.filesDir, "recordings").apply { mkdirs() }
    fun save(source: File): String {
        val name = "${newId()}.m4a"
        val target = File(directory, name)
        try { source.copyTo(target); source.delete() }
        catch (error: Exception) { target.delete(); throw error }
        return name
    }
    fun file(name: String): File {
        require(name.matches(Regex("[a-fA-F0-9-]+\\.m4a"))) { "录音文件名无效。" }
        return File(directory, name)
    }
    fun remove(name: String) { if (name.isNotBlank()) runCatching { file(name).delete() } }
}
