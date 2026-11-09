package com.speakpen.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*

@Composable
internal fun ScenarioBrief(template: ScenarioTemplate, vm: PracticeViewModel) {
    val material = remember(template.id) { vm.material(template) }
    LearningSurface("任务简报", { vm.showScenarioBrief(null) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(template.name, style = MaterialTheme.typography.headlineLarge)
            Surface(color = Ink, contentColor = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("你：${template.userRole}", style = MaterialTheme.typography.titleLarge)
                    Text("AI：${template.aiRole}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("你的目标", style = MaterialTheme.typography.titleLarge)
            template.goals.forEachIndexed { i, goal -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                Text(goal.text, style = MaterialTheme.typography.bodyLarge)
            } }
            HorizontalDivider(color = Line)
            Text("对话预览", style = MaterialTheme.typography.titleLarge)
            material.chunks.first().forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(material.source, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (!template.id.startsWith("builtin:")) TextButton(onClick = { vm.showScenarioBrief(null); vm.editScenario(template) }) { Text("编辑场景") }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.directScenario(template) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("开始对话"); Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
            OutlinedButton(onClick = { vm.learnScenario(template) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.VolumeUp, null); Spacer(Modifier.width(10.dp)); Text("查看完整示范")
            }
        }
    }
}

@Composable
internal fun ScenarioLesson(state: PracticeState, vm: PracticeViewModel) {
    val p = state.session.lesson ?: return
    val material = remember(p.template.id) { vm.material(p.template) }
    val enabled = state.busy == null && !state.voiceActive
    val playing = state.playingId?.startsWith("lesson-") == true || state.preparingVoiceId?.startsWith("lesson-") == true
    LearningSurface("示范对话", { vm.lessonVisible(false) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(p.template.name, style = MaterialTheme.typography.headlineLarge)
            FilledTonalButton(onClick = { if (playing) vm.stopSpeaking() else vm.listenLesson() }, enabled = !state.voiceActive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(if (playing) Icons.Outlined.Stop else Icons.Outlined.VolumeUp, null)
                Spacer(Modifier.width(10.dp))
                Text(if (state.preparingVoiceId != null) "正在准备 · 点击停止" else if (playing) "停止播放" else "播放完整对话")
            }
            material.dialogue.forEachIndexed { index, line ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.substringBefore(": ", ""), Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { vm.listenLesson(index) }, enabled = !state.voiceActive) {
                            Icon(Icons.Outlined.VolumeUp, "播放第 ${index + 1} 句")
                        }
                    }
                    SelectionContainer { Text(line.substringAfter(": "), style = MaterialTheme.typography.bodyLarge) }
                }
                if (index < material.dialogue.lastIndex) HorizontalDivider(color = Line)
            }
            state.error?.let { InlineNotice(it, isError = true, onClose = vm::clearFeedback) }
            Text(material.source, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = vm::beginLearnedScenario, enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp)) { Text("开始对话") }
    }
}

@Composable
internal fun TopicOptionsPanel(state: PracticeState, vm: PracticeViewModel) {
    var options by remember { mutableStateOf(state.topicOptions) }
    val boundaryError = runCatching { TopicRound.latest(state.session.messages) }.exceptionOrNull()?.message
    LearningSurface("话题语料", { vm.showTopicOptions(false) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("仅使用最新一轮你的消息与 AI 回答", color = Muted)
            boundaryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("用途", style = MaterialTheme.typography.titleLarge)
            TopicPurpose.entries.forEach { entry -> FilterChip(options.purpose == entry, { options = options.copy(purpose = entry) }, label = { Text(entry.label) }) }
            Text("数量 ${options.count}", style = MaterialTheme.typography.titleLarge)
            Slider(options.count.toFloat(), { options = options.copy(count = it.toInt().coerceIn(4, 10)) }, valueRange = 4f..10f, steps = 5)
            Text("难度", style = MaterialTheme.typography.titleLarge)
            TopicDifficulty.entries.forEach { entry -> FilterChip(options.difficulty == entry, { options = options.copy(difficulty = entry) }, label = { Text(entry.label) }) }
        }
        Button(onClick = { vm.topicExpressions(options) }, enabled = boundaryError == null && state.busy == null,
            modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("生成 ${options.count} 条语料") }
    }
}

@Composable
internal fun VoiceQueuePanel(state: PracticeState, vm: PracticeViewModel) {
    val queue = state.session.voiceQueue
    if (queue.isEmpty()) return
    val enabled = state.busy == null && !state.voiceActive
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${queue.size} 段 · ${queue.sumOf { it.durationMs } / 1000} 秒 / 180 秒", style = MaterialTheme.typography.titleSmall)
        Text("保存在当前会话，离开或关闭功能后仍可回来提交。", color = Muted, style = MaterialTheme.typography.bodySmall)
        queue.forEachIndexed { i, segment ->
            Column(Modifier.fillMaxWidth()) {
                Text("${i + 1}. ${segment.durationMs / 1000} 秒${if (segment.failed) " 转写失败" else if (segment.text.isNotBlank()) " 已转写" else ""}", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { vm.playSegment(segment) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("试听") }
                    if (state.settings.multiSegmentVoice) TextButton(onClick = { vm.rerecordSegment(segment.id) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("重录") }
                    TextButton(onClick = { vm.deleteSegment(segment.id) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("删除") }
                }
            }
        }
        Button(onClick = vm::submitSegments, enabled = enabled && !state.needsReply, modifier = Modifier.fillMaxWidth()) {
            Text(if (queue.any { it.failed }) "重试并提交 ${queue.size} 段" else "提交 ${queue.size} 段")
        }
    }
}
