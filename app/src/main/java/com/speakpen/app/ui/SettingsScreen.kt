package com.speakpen.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
import com.speakpen.app.BuildConfig
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel

@Composable
fun SettingsScreen(state: PracticeState, vm: PracticeViewModel) {
    var pairingVisible by remember { mutableStateOf(false) }
    var pairingText by remember { mutableStateOf("") }
    var defaultsTab by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val draft = state.settingsDraft ?: state.settings
    val currentConfig = state.conversationDraft ?: state.session.config
    val editedSession = state.sessions.firstOrNull { it.id == state.settingsSessionId } ?: state.session
    val enabled = !state.settingsSaving
    BackHandler(enabled = state.settingsDirty || state.settingsSaving) { if (!state.settingsSaving) vm.page(0) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text("设置", style = MaterialTheme.typography.headlineMedium)
                Text("修改后点击底部保存。", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                PreferenceCard {
                    Text("对话设置", style = MaterialTheme.typography.titleLarge)
                    TabRow(selectedTabIndex = if (defaultsTab) 1 else 0, containerColor = Color.Transparent) {
                        Tab(selected = !defaultsTab, onClick = { defaultsTab = false }, enabled = enabled, text = { Text("当前对话") })
                        Tab(selected = defaultsTab, onClick = { defaultsTab = true }, enabled = enabled, text = { Text("新对话默认") })
                    }
                    Text(if (defaultsTab) "仅用于以后新建的对话，不改变当前或已有对话。"
                        else "正在编辑：${editedSession.title}\n仅影响本对话后续生成，历史消息和在途回复保留原设置。",
                        style = MaterialTheme.typography.bodyMedium, color = Muted)
                    key(defaultsTab, state.settingsSessionId) {
                        ConversationControls(if (defaultsTab) draft.conversationDefaults else currentConfig,
                            advancedEnabled = state.conversationControlsSupported || draft.demoMode, enabled = enabled) { config ->
                            if (defaultsTab) vm.editSettings(draft.copy(conversationDefaults = config)) else vm.editConversationSettings(config)
                        }
                    }
                    Text("难度是回复目标，不是用户能力评级。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            item {
                PreferenceCard {
                    Text("显示与播放 · 全局", style = MaterialTheme.typography.titleMedium)
                    Text("应用于所有对话。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    SettingSwitch("AI 默认显示英文原文", "关闭后以语音块显示，仍可逐条展开原文。", !draft.voiceReplies, enabled) {
                        vm.editSettings(draft.copy(voiceReplies = !it))
                    }
                    HorizontalDivider(color = Line)
                    SettingSwitch("自动播放 AI 回复", "关闭后可点语音块或播放按钮收听。", draft.autoSpeak, enabled) {
                        vm.editSettings(draft.copy(autoSpeak = it))
                    }
                }
            }
            item {
                PreferenceCard {
                    Text("服务连接", style = MaterialTheme.typography.titleMedium)
                    Text(state.serviceStatus, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::checkService, enabled = enabled) { Text("检查连接") }
                        OutlinedButton(onClick = { pairingVisible = true }, enabled = enabled && state.busy == null && !state.voiceActive) { Text("重新连接") }
                    }
                    Text("连接操作立即生效。电脑重建入口后，可粘贴新的完整配对链接。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                PreferenceCard {
                    Text("体验模式 · 全局", style = MaterialTheme.typography.titleMedium)
                    SettingSwitch("示例体验", "使用本地示例，不进行真实转写或学习评分。", draft.demoMode, enabled && state.busy == null && !state.voiceActive) {
                        vm.editSettings(draft.copy(demoMode = it))
                    }
                }
            }
            item {
                Text("录音、对话与积累保存在本机。使用真实 AI 时，文字和语音会交给应用服务处理；卸载会清除本地记录。", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("SpeakPen ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color = Color.White, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.settingsFeedback ?: if (state.settingsDirty) "有未保存的更改" else "设置已保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.settingsSaveError) MaterialTheme.colorScheme.error else Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::revertSettings, enabled = enabled && (state.settingsDirty || state.settingsSaveError)) { Text("撤销修改") }
                    Button(onClick = vm::saveSettings, enabled = enabled && (state.settingsDirty || state.settingsSaveError), modifier = Modifier.weight(1f)) {
                        if (state.settingsSaving) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(if (state.settingsSaving) "保存中…" else "保存更改")
                    }
                }
            }
        }
    }
    if (state.pendingSettingsPage != null) AlertDialog(
        onDismissRequest = { if (enabled) vm.stayInSettings() }, title = { Text("有未保存的设置") },
        text = { Text(if (state.settingsSaveError) state.settingsFeedback.orEmpty() else "保存更改后离开，或放弃本次修改。") },
        confirmButton = { TextButton(onClick = vm::saveSettings, enabled = enabled) { Text("保存并离开") } },
        dismissButton = { Row {
            TextButton(onClick = vm::discardSettingsAndLeave, enabled = enabled) { Text("放弃修改") }
            TextButton(onClick = vm::stayInSettings, enabled = enabled) { Text("继续编辑") }
        } },
    )
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun PreferenceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
