package com.speakpen.app

/** Pure exit transitions: keep failed drafts visible; only confirmed writes complete navigation. */
object SettingsNavigation {
    fun request(state: PracticeState, destination: Int): PracticeState {
        if (state.settingsSaving || state.voiceActive || destination == state.page) return state
        if (state.page == 2 && (state.settingsDirty || state.settingsSaveError)) return state.copy(pendingSettingsPage = destination)
        return state.copy(page = destination, error = null, notice = null,
            settingsDraft = if (destination == 2) state.settings else state.settingsDraft,
            conversationDraft = if (destination == 2) state.session.config else state.conversationDraft,
            settingsSessionId = if (destination == 2) state.currentSessionId else state.settingsSessionId,
            pendingSettingsPage = null, settingsFeedback = null, settingsSaveError = false)
    }

    fun completed(state: PracticeState, success: Boolean): PracticeState = state.copy(
        settingsSaving = false, settingsSaveError = !success,
        page = if (success) state.pendingSettingsPage ?: state.page else state.page,
        pendingSettingsPage = if (success) null else state.pendingSettingsPage,
        settingsFeedback = if (success) "设置已保存" else "保存尚未完成，更改已保留，请重试。",
    )
}
