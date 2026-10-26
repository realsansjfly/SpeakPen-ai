package com.speakpen.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speakpen.app.data.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ConversationControls(config: ConversationConfig, advancedEnabled: Boolean = true, enabled: Boolean = true, onChange: (ConversationConfig) -> Unit) {
    var advancedVisible by remember { mutableStateOf(false) }
    var difficultyPicker by remember { mutableStateOf(false) }
    var lengthPicker by remember { mutableStateOf(false) }
    var temperature by remember(config.advanced.temperature) { mutableFloatStateOf(config.advanced.temperature.toFloat()) }
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { difficultyPicker = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("表达难度", style = MaterialTheme.typography.titleMedium)
            Text(config.difficulty.description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text(config.difficulty.label, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "选择表达难度", tint = Muted)
    }
    HorizontalDivider(color = Line)
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { advancedVisible = !advancedVisible }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("高级设置", style = MaterialTheme.typography.titleMedium)
            Text("温度 ${String.format(Locale.ROOT, "%.1f", config.advanced.temperature)} · 回复${config.advanced.replyLength.label}", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(if (advancedVisible) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (advancedVisible) "收起高级设置" else "展开高级设置", tint = Muted)
    }
    if (advancedVisible) {
        if (!advancedEnabled) Text("温度和回复长度需要更新本机服务后启用。", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("温度", style = MaterialTheme.typography.titleSmall)
            Text(String.format(Locale.ROOT, "%.1f", temperature), color = MaterialTheme.colorScheme.primary)
        }
        Text("较低时措辞更稳定，较高时表达更多样。", color = Muted, style = MaterialTheme.typography.bodySmall)
        Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..1f, steps = 9, enabled = enabled && advancedEnabled,
            onValueChangeFinished = { onChange(config.copy(advanced = config.advanced.copy(temperature = (temperature * 10).roundToInt() / 10.0))) })
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled && advancedEnabled) { lengthPicker = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("回复长度", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(config.advanced.replyLength.label, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "选择回复长度", tint = Muted)
        }
        TextButton(onClick = { onChange(config.copy(advanced = AdvancedSettings())) }, enabled = enabled) { Text("恢复高级设置默认值") }
    }
    if (difficultyPicker) SettingsChoiceDialog("表达难度", ExpressionDifficulty.entries.map { "${it.label} · ${it.description}" }, config.difficulty.ordinal,
        onSelect = { onChange(config.copy(difficulty = ExpressionDifficulty.entries[it])); difficultyPicker = false }, onDismiss = { difficultyPicker = false })
    if (lengthPicker) SettingsChoiceDialog("回复长度", ReplyLength.entries.map { it.label }, config.advanced.replyLength.ordinal,
        onSelect = { onChange(config.copy(advanced = config.advanced.copy(replyLength = ReplyLength.entries[it]))); lengthPicker = false }, onDismiss = { lengthPicker = false })
}

@Composable
private fun SettingsChoiceDialog(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = index == selected, onClick = { onSelect(index) })
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
