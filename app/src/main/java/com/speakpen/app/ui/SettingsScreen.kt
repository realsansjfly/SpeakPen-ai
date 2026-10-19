package com.speakpen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.speakpen.app.BuildConfig
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel

@Composable
fun SettingsScreen(state: PracticeState, vm: PracticeViewModel) {
    var pairingVisible by remember { mutableStateOf(false) }
    var pairingText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Eyebrow("MAKE YOURSELF COMFORTABLE")
            Text("按你的习惯来", style = MaterialTheme.typography.headlineMedium)
            Text("少一点设置，多一点开口。", color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            PreferenceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(SoftBlue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.GraphicEq, null, tint = MaterialTheme.colorScheme.secondary) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("回复显示", style = MaterialTheme.typography.titleMedium); Text("随时可以展开英文原文", color = Muted, style = MaterialTheme.typography.bodyMedium) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.settings.voiceReplies, onClick = { vm.preferences(state.settings.copy(voiceReplies = true)) }, label = { Text("语音块") }, leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, Modifier.size(16.dp)) })
                    FilterChip(selected = !state.settings.voiceReplies, onClick = { vm.preferences(state.settings.copy(voiceReplies = false)) }, label = { Text("英文原文") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Subject, null, Modifier.size(16.dp)) })
                }
                HorizontalDivider(color = Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("自动播放回复", style = MaterialTheme.typography.titleMedium); Text("关闭后，点语音块再听", color = Muted, style = MaterialTheme.typography.bodyMedium) }
                    Switch(checked = state.settings.autoSpeak, onCheckedChange = { vm.preferences(state.settings.copy(autoSpeak = it)) })
                }
            }
        }
        item {
            PreferenceCard {
                Text("服务连接", style = MaterialTheme.typography.titleMedium)
                Text(state.serviceStatus, style = MaterialTheme.typography.bodyMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::checkService) { Text("检查连接") }
                    OutlinedButton(onClick = { pairingVisible = true }) { Text("重新连接") }
                }
                Text("电脑重建连接后，请扫描新的入口并点“连接 App”；浏览器未唤起 App 时，可在这里粘贴完整入口。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item {
            PreferenceCard {
                Text("语音怎么用", style = MaterialTheme.typography.titleMedium)
                Text("按住说话，松开发送，上滑取消。原录音保存在手机里，点击自己的语音块可以重听。", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Text("AI 回复优先使用千问云端语音，首次播放会短暂生成；播放失败时尝试手机英文朗读。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item {
            PreferenceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("示例体验", style = MaterialTheme.typography.titleMedium); Text("使用本地示例，了解操作", color = Muted, style = MaterialTheme.typography.bodyMedium) }
                    Switch(checked = state.settings.demoMode, onCheckedChange = { vm.preferences(state.settings.copy(demoMode = it)) }, enabled = state.busy == null && !state.voiceActive)
                }
                Text(if (state.serviceConfigured) "AI 服务已接入应用，无需选择模型。" else "本安装包的 AI 服务尚未开通，可先体验示例。示例不进行真实语音转写，也不做学习评分。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item {
            Text("录音、对话与复习记录保存在本机。使用真实 AI 时，语音和文字会交给应用服务处理；卸载会清除本地记录。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(Modifier.height(16.dp)); Eyebrow("SPEAKPEN · ${BuildConfig.VERSION_NAME}")
        }
    }
    if (pairingVisible) AlertDialog(
        onDismissRequest = { pairingVisible = false; pairingText = "" },
        title = { Text("重新连接电脑服务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("复制电脑新生成的服务入口，或扫码后复制链接，再粘贴到这里。无需填写模型或 API Key。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = pairingText, onValueChange = { pairingText = it },
                    label = { Text("完整服务连接入口") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { pairingText = clipboard.getText()?.text.orEmpty() }) { Text("从剪贴板粘贴") }
            }
        },
        confirmButton = { TextButton(onClick = { vm.connect(pairingText); pairingText = ""; pairingVisible = false }, enabled = pairingText.isNotBlank() && state.busy == null && !state.voiceActive) { Text("连接") } },
        dismissButton = { TextButton(onClick = { pairingText = ""; pairingVisible = false }) { Text("取消") } },
    )
}
@Composable private fun PreferenceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}




