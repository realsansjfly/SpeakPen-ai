package com.speakpen.app.data

import org.json.JSONObject

/** Recovery contains editable preferences only; pairing credentials are never duplicated. */
data class SettingsEditorDraft(val settings: AppSettings, val sessionId: String, val conversation: ConversationConfig) {
    fun toJson(): String = JSONObject().apply {
        put("sessionId", sessionId); put("conversation", ConversationConfigJson.write(conversation))
        put("autoSpeak", settings.autoSpeak); put("voiceReplies", settings.voiceReplies); put("demoMode", settings.demoMode)
        put("defaults", ConversationConfigJson.write(settings.conversationDefaults))
    }.toString()

    companion object {
        fun read(raw: String, latest: AppSettings): SettingsEditorDraft {
            val json = JSONObject(raw)
            return SettingsEditorDraft(latest.copy(autoSpeak = json.getBoolean("autoSpeak"),
                voiceReplies = json.getBoolean("voiceReplies"), demoMode = json.getBoolean("demoMode"),
                conversationDefaults = ConversationConfigJson.read(json.getJSONObject("defaults"))),
                json.getString("sessionId"), ConversationConfigJson.read(json.getJSONObject("conversation")))
        }
    }
}
