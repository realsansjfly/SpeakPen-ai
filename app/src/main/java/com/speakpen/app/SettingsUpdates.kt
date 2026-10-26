package com.speakpen.app

import com.speakpen.app.data.AppSettings
import com.speakpen.app.data.ConversationConfig

/** Shared state transition used after a successful settings write. */
object SettingsUpdates {
    fun apply(state: PracticeState, settings: AppSettings): PracticeState = state.copy(settings = settings)

    fun conversation(state: PracticeState, sessionId: String, config: ConversationConfig): PracticeState = state.copy(
        sessions = state.sessions.map { session ->
            if (session.id != sessionId || session.config == config) session else
                session.copy(config = config, configRevision = session.configRevision + 1)
        },
    )

    /** A draft never overwrites pairing changes received while the editor is open. */
    fun merge(latest: AppSettings, draft: AppSettings): AppSettings = latest.copy(
        autoSpeak = draft.autoSpeak, voiceReplies = draft.voiceReplies, demoMode = draft.demoMode,
        conversationDefaults = draft.conversationDefaults,
    )
}
