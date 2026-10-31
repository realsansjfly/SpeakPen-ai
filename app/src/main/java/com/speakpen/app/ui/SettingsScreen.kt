package com.speakpen.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.speakpen.app.BuildConfig
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel

@Composable
fun SettingsScreen(state: PracticeState, vm: PracticeViewModel) {
    var pairingVisible by remember { mutableStateOf(false) }
    var pairingText by remember { mutableStateOf("") }
    var defaultsTab by rememberSaveable { mutableStateOf(false) }
    var vocabularyVisible by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val draft = state.settingsDraft ?: state.settings
    val currentConfig = state.conversationDraft ?: state.session.config
    val editedSession = state.sessions.firstOrNull { it.id == state.settingsSessionId } ?: state.session
    val enabled = !state.settingsSaving
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = !keyboardVisible) { if (!state.settingsSaving) vm.page(0) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.page(0) }, enabled = enabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "保存设置并返回") }
            Text("设置", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (state.settingsSaving) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
            else Text(if (state.settingsSaveError) "未保存" else if (state.settingsDirty) "返回时保存" else "已保存",
                Modifier.padding(end = 12.dp), color = if (state.settingsSaveError) MaterialTheme.colorScheme.error else Muted,
                style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            if (state.settingsSaveError) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(state.settingsFeedback ?: "保存尚未完成，更改已保留。", color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = vm::saveSettings, enabled = enabled) { Text("重试保存") }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = if (defaultsTab) 1 else 0, containerColor = Color.White) {
                    Tab(selected = !defaultsTab, onClick = { defaultsTab = false }, enabled = enabled, text = { Text("当前对话") })
                    Tab(selected = defaultsTab, onClick = { defaultsTab = true }, enabled = enabled, text = { Text("新对话默认") })
                }
                Text(if (defaultsTab) "仅用于以后新建的对话，不改变当前或已有对话。"
                    else "仅影响「${editedSession.title}」的后续回复。历史消息和生成中的回复保留原设置。",
                    Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                key(defaultsTab, state.settingsSessionId) {
                    val scopedConfig = if (defaultsTab) draft.conversationDefaults else currentConfig
                    ConversationControls(if (defaultsTab) draft.conversationDefaults else currentConfig,
                        advancedEnabled = state.conversationControlsSupported || draft.demoMode, enabled = enabled) { config ->
                        if (defaultsTab) vm.editSettings(draft.copy(conversationDefaults = config)) else vm.editConversationSettings(config)
                    }
                    SettingSwitch("实时纠错", "每轮额外请求一次 AI，建议不自动朗读。", scopedConfig.realtimeCorrection, enabled) { checked ->
                        val config = scopedConfig.copy(realtimeCorrection = checked)
                        if (defaultsTab) vm.editSettings(draft.copy(conversationDefaults = config)) else vm.editConversationSettings(config)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled) { vocabularyVisible = true; vm.clearFeedback() }, verticalAlignment = Alignment.CenterVertically) {
                        Text("词库增强", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(scopedConfig.vocabulary?.name ?: "关闭", Modifier.widthIn(max = 190.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (vocabularyVisible) VocabularyScreen(state, vm, scopedConfig.vocabulary, onChoose = { choice ->
                        val updated = scopedConfig.copy(vocabulary = choice)
                        if (defaultsTab) vm.editSettings(draft.copy(conversationDefaults = updated)) else vm.editConversationSettings(updated)
                    }, onDismiss = { vocabularyVisible = false })
                }
            }
            item {
                SettingsSection("学习记录") {
                    Text("场景成功 ${state.scenarioSuccesses.size} 次", style = MaterialTheme.typography.bodyLarge)
                    Text("已完成 ${state.scenarioSuccesses.map { it.templateId }.distinct().size} 个不同场景", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SettingsSection("显示与播放") {
                    SettingSwitch("显示英文原文", "关闭后显示语音块，仍可逐条展开。", !draft.voiceReplies, enabled) { vm.editSettings(draft.copy(voiceReplies = !it)) }
                    SettingSwitch("自动播放回复", "", draft.autoSpeak, enabled) { vm.editSettings(draft.copy(autoSpeak = it)) }
                }
            }
            item {
                SettingsSection("服务连接") {
                    Text(state.serviceStatus, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = vm::checkService, enabled = enabled) { Text("检查连接") }
                        TextButton(onClick = { pairingVisible = true }, enabled = enabled && state.busy == null && !state.voiceActive) { Text("重新连接") }
                    }
                }
            }
            item {
                SettingsSection("体验模式") {
                    SettingSwitch("示例体验", "使用本地示例，不进行真实转写或评分。", draft.demoMode, enabled && state.busy == null && !state.voiceActive) {
                        vm.editSettings(draft.copy(demoMode = it))
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 18.dp, bottom = 20.dp), color = Line)
                Text("录音、对话与积累保存在本机。真实 AI 会通过应用服务处理文字和语音；卸载会清除本地记录。", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Text("SpeakPen ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (pairingVisible) AlertDialog(
        onDismissRequest = { pairingVisible = false; pairingText = "" }, title = { Text("重新连接电脑服务") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("复制电脑新生成的完整服务入口，无需填写模型或 API Key。", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = pairingText, onValueChange = { pairingText = it }, label = { Text("完整服务连接入口") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextButton(onClick = { pairingText = clipboard.getText()?.text.orEmpty() }) { Text("从剪贴板粘贴") }
        } },
        confirmButton = { TextButton(onClick = { vm.connect(pairingText); pairingText = ""; pairingVisible = false },
            enabled = enabled && pairingText.isNotBlank() && state.busy == null && !state.voiceActive) { Text("连接") } },
        dismissButton = { TextButton(onClick = { pairingText = ""; pairingVisible = false }) { Text("取消") } },
    )
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(top = 18.dp, bottom = 18.dp), color = Line)
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
