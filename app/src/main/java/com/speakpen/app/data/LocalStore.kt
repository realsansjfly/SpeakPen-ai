package com.speakpen.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import com.speakpen.app.BuildConfig
import java.io.File

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("speakpen_settings", Context.MODE_PRIVATE)
    private val dataFile = AtomicFile(File(context.filesDir, "learning.json"))
    var loadWarning: String? = null
        private set
    private var writesEnabled = true

    fun readSettings(): AppSettings = try {
        val json = JSONObject(prefs.getString("settings", "{}") ?: "{}")
        AppSettings(autoSpeak = json.optBoolean("autoSpeak", true),
            demoMode = json.optBoolean("demoModeV2", false) && (json.optInt("liveServiceVersion") >= 1 || BuildConfig.SERVICE_BASE_URL.isBlank()),
            voiceReplies = json.optBoolean("voiceReplies", true), serviceUrl = json.optString("serviceUrl"),
            serviceAccessToken = json.optString("serviceAccessToken"),
            conversationDefaults = ConversationConfigJson.read(json.optJSONObject("conversationDefaults")))
    } catch (_: Exception) {
        loadWarning = "设置读取失败，可重新设置；学习记录未删除。"
        AppSettings()
    }
    fun saveSettings(settings: AppSettings) {
        val json = JSONObject().apply {
            put("liveServiceVersion", 1); put("serviceUrl", settings.serviceUrl); put("serviceAccessToken", settings.serviceAccessToken)
            put("autoSpeak", settings.autoSpeak); put("demoModeV2", settings.demoMode); put("voiceReplies", settings.voiceReplies)
            put("conversationDefaults", ConversationConfigJson.write(settings.conversationDefaults))
        }
        // Replacing legacy settings removes obsolete encrypted provider credentials.
        check(prefs.edit().putString("settings", json.toString()).commit()) { "设置保存失败，请重试。" }
    }
    fun readData(): SavedData = try {
        if (!dataFile.baseFile.exists()) SavedData() else DataJson.read(dataFile.openRead().bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        loadWarning = "本地学习数据读取失败。为保护原文件，已暂停写入，请先备份 learning.json。"
        writesEnabled = false
        SavedData()
    }
    fun saveData(data: SavedData) {
        check(writesEnabled) { "学习数据文件异常，已暂停写入以保护原文件。" }
        val stream = dataFile.startWrite()
        try { stream.write(DataJson.write(data).toByteArray(Charsets.UTF_8)); dataFile.finishWrite(stream) }
        catch (error: Exception) { dataFile.failWrite(stream); throw error }
    }
}

