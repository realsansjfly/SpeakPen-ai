package com.speakpen.app.ui

import android.animation.ValueAnimator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun LearningSurface(title: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回对话") }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    actions()
                }
                HorizontalDivider(color = Line)
                content()
            }
        }
    }
}

@Composable
fun LearningOverlays(state: PracticeState, vm: PracticeViewModel, onRecord: () -> Unit) {
    if (state.recapVisible) RecapReader(state, vm)
    state.session.speakingPractices.firstOrNull { it.id == state.speakingPracticeId }?.let { practice ->
        LearningSurface("重说练习", onClose = { vm.showSpeakingPractice(null) }) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("原句", color = Muted, style = MaterialTheme.typography.titleSmall)
                SelectionContainer { Text(practice.original, style = MaterialTheme.typography.bodyLarge) }
                Text("参考表达", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(practice.reference, style = MaterialTheme.typography.bodyLarge) }
                TextButton(onClick = { vm.speak(practice.reference) }, enabled = !state.voiceActive && state.busy == null) {
                    Text(if (state.playingId == "manual") "停止朗读" else "听参考句")
                }
                HorizontalDivider(color = Line)
                Text("练习录音", style = MaterialTheme.typography.titleMedium)
                if (practice.audioFileName.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.playPractice(practice.id) }, enabled = !state.voiceActive) {
                        Text(if (state.playingId == practice.id) "停止回听" else "回听录音")
                    }
                    Text("${VoiceMessages.displaySeconds(practice.audioDurationMs)} 秒", color = Muted)
                }
                if (practice.sentMessageId.isBlank()) {
                    HoldToTalk(enabled = state.busy == null && !state.voiceActive, recording = state.recording, chinese = false,
                        onStart = onRecord, onEnd = vm::stopRecording, onCancel = vm::cancelRecording,
                        onTap = { if (state.recording) vm.stopRecording() else onRecord() }, practice = true)
                    if (state.recording) Waveform(Modifier.fillMaxWidth().height(24.dp), active = true, level = state.recordingLevel, color = MaterialTheme.colorScheme.primary)
                    Text("录音保存在练习中，发送后才进入对话。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(practice.transcript, { vm.practiceTranscript(practice.id, it) }, label = { Text("练习转写（可修改）") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = state.busy == null && !state.voiceActive)
                    if (practice.transcriptState == TranscriptState.FAILED) TextButton(onClick = { vm.transcribePractice(practice.id) }, enabled = state.busy == null) { Text("重试转写") }
                } else Text("这段练习已发送到对话。", color = Muted)
                if (state.error != null) InlineNotice(state.error, true, vm::clearFeedback)
                if (state.busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.busy, Modifier.weight(1f), color = Muted)
                    TextButton(onClick = vm::cancelOperation) { Text("取消等待") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.showSpeakingPractice(null) }, enabled = state.busy == null && !state.voiceActive) { Text("完成练习") }
                Button(onClick = { vm.sendPractice(practice.id) }, enabled = practice.transcript.isNotBlank() && practice.sentMessageId.isBlank() &&
                    !state.scenarioInputBlocked && !state.needsReply && state.pendingVoice == null && state.busy == null && !state.voiceActive) { Text("发送到对话") }
            }
        }
    }
    if (state.scenarioReviewPromptId == state.currentSessionId && state.rewardPracticeId == null && !state.recapVisible) {
        AlertDialog(onDismissRequest = vm::dismissScenarioReviewPrompt, title = { Text("要生成这次场景的复盘吗？") },
            text = { Text("只使用场景结束前的对话。也可以稍后从复盘页生成。") },
            confirmButton = { TextButton(onClick = { vm.dismissScenarioReviewPrompt(); vm.showRecaps(true, RecapKind.SCENARIO); vm.generateRecap(RecapKind.SCENARIO) }, enabled = state.busy == null) { Text("生成复盘") } },
            dismissButton = { TextButton(onClick = vm::dismissScenarioReviewPrompt) { Text("稍后") } })
    }
}

@Composable
private fun RecapReader(state: PracticeState, vm: PracticeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportingId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportBusy by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val captured = state.sessions.flatMap { listOfNotNull(it.latestRecap, it.fixedRecap) }.firstOrNull { it.id == exportingId }
        exportingId = null; exportBusy = false
        if (uri != null && captured != null) scope.launch {
            exportBusy = true
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "文件无法写入。" }.use { it.write(captured.markdown.toByteArray(Charsets.UTF_8)) }
                }
                vm.recapExported()
            } catch (_: Exception) { vm.error("复盘导出失败，本机文档已保留，可重新选择位置。") }
            finally { exportBusy = false }
        }
    }
    val kind = state.recapKind
    val document = if (kind == RecapKind.LATEST) state.session.latestRecap else state.session.fixedRecap
    LearningSurface("复盘", onClose = { if (!exportBusy) vm.showRecaps(false) }, actions = {
        TextButton(onClick = {
            exportingId = document?.id; exportBusy = true
            export.launch("SpeakPen-${kind.label}-${document?.id?.take(8)}.md")
        }, enabled = document != null && !exportBusy) { Text(if (exportBusy) "导出中" else "导出") }
    }) {
        if (state.session.scenario != null) TabRow(selectedTabIndex = kind.ordinal, containerColor = Paper) {
            RecapKind.entries.forEach { tab -> Tab(selected = kind == tab, onClick = { vm.showRecaps(true, tab) }, text = { Text(tab.label) }) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (document != null) {
                Text("${dateLabel(document.createdAt)}，依据 ${document.sourceMessages.size} 条对话", color = Muted, style = MaterialTheme.typography.bodySmall)
                SelectionContainer { MarkdownDocument(document.markdown) }
            } else Text(if (kind == RecapKind.SCENARIO && state.session.scenario?.ending == null) "场景结束后可生成固定复盘。" else "还没有${kind.label}。", color = Muted)
            if (state.error != null) InlineNotice(state.error, true, vm::clearFeedback)
            if (state.notice != null) InlineNotice(state.notice, false, vm::clearFeedback)
            if (state.busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(state.busy, Modifier.weight(1f).padding(start = 10.dp), color = Muted)
                TextButton(onClick = vm::cancelOperation) { Text("取消") }
            }
            val canGenerate = kind == RecapKind.LATEST || state.session.scenario?.ending != null && document == null
            if (canGenerate) Button(onClick = { vm.generateRecap(kind) }, enabled = state.busy == null && !state.voiceActive && !exportBusy) {
                Text(if (document != null) "更新最新复盘" else "生成${kind.label}")
            }
            if (kind == RecapKind.LATEST && document != null) Text("更新成功后替换当前文档。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MarkdownDocument(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        markdown.lines().filter { it.isNotBlank() }.forEach { line ->
            val heading = line.startsWith("#")
            val text = line.removePrefix("## ").removePrefix("# ").replace(Regex("\\\\([\\\\`*_#<>\\[\\]])"), "$1")
            Text(text, Modifier.padding(top = if (line.startsWith("## ")) 14.dp else 0.dp),
                style = if (line.startsWith("## ")) MaterialTheme.typography.titleMedium else if (heading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ScenarioReward(state: PracticeState, vm: PracticeViewModel) {
    val id = state.rewardPracticeId ?: return
    val scale = remember(id) { Animatable(if (ValueAnimator.areAnimatorsEnabled()) .9f else 1f) }
    LaunchedEffect(id) {
        if (ValueAnimator.areAnimatorsEnabled()) scale.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(1300); vm.finishReward()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = SoftGreen, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(32.dp).graphicsLayer {
            scaleX = scale.value; scaleY = scale.value
        }) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("场景目标已达成", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
