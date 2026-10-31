package com.speakpen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*

@Composable
fun ChatScreen(state: PracticeState, vm: PracticeViewModel, onRecord: () -> Unit, onOpenDrawer: () -> Unit, visible: Boolean) {
    val session = state.session
    val locked = state.busy != null || state.voiceActive || state.scenarioInputBlocked
    var typing by rememberSaveable(session.id) { mutableStateOf(false) }
    LaunchedEffect(session.id, state.draft) { if (state.draft.isNotBlank()) typing = true }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var moreVisible by remember { mutableStateOf(false) }
    var practiceHistoryVisible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val displayMessages = remember(session.messages) { LearningMessages.display(session.messages) }
    // Side corrections should not pull the reader away from their current position.
    val scrollMessageCount = session.messages.count { it.hintKind != HintKind.CORRECTION }
    LaunchedEffect(session.id, scrollMessageCount, state.busy, state.scrollToMessageId) {
        val anchor = state.scrollToMessageId ?: state.operationAnchorId
        val anchoredIndex = displayMessages.indexOfFirst { it.id == anchor }
        val count = displayMessages.size + if (state.busy != null || state.streamingText.isNotBlank()) 1 else 0
        if (anchoredIndex >= 0) listState.animateScrollToItem(anchoredIndex)
        else if (count > 0) listState.animateScrollToItem(count - 1)
        state.scrollToMessageId?.let(vm::scrollConsumed)
    }
    Box(Modifier.fillMaxSize()) {
    ConversationBackdrop(active = visible && state.appForeground && !state.voiceActive && state.playingId == null &&
        !state.recapVisible && state.speakingPracticeId == null && !state.scenarioPickerVisible, modifier = Modifier.matchParentSize())
    Column(Modifier.fillMaxSize()) {
        ChatHeader(state, vm, onOpenDrawer, !state.voiceActive && !state.settingsSaving, state.busy == null && !state.voiceActive)
        if (session.mode == ConversationMode.REVIEW) Text("练习目标：" + state.library.filter { it.id in session.targetIds }.joinToString("、") { it.content },
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2)
        session.scenario?.let { practice -> ScenarioPanel(state, vm, practice) }
        HorizontalDivider(color = Line)
        if (session.messages.isEmpty() && (state.busy != null || session.mode == ConversationMode.SCENARIO)) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                if (state.busy != null) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(16.dp)); Text(state.busy.orEmpty(), color = Muted)
                    TextButton(onClick = vm::cancelOperation) { Text("取消等待") }
                } else {
                    Text("场景开场白尚未完成", color = Muted)
                    TextButton(onClick = vm::retryScenarioOpening) { Text("重试开场白") }
                }
            }
        } else if (session.messages.isEmpty()) {
            TopicSuggestions(session.id, visitVersion = state.topicVisitVersion, visitActive = visible && state.appForeground,
                nextBatch = vm::nextTopicBatch, onError = vm::error,
                active = visible && state.appForeground && !typing && !state.voiceActive && !state.scenarioPickerVisible && !state.targetPickerVisible,
                enabled = !locked, onSelect = { text -> vm.draft(text); typing = true }, onStart = { vm.startConversation() },
                demoAvailable = !state.serviceConfigured && !state.settings.demoMode,
                onDemo = { if (vm.preferences(state.settings.copy(demoMode = true))) vm.startConversation() },
                modifier = Modifier.weight(1f))
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(displayMessages, key = { it.id }) { message ->
                    MessageCard(message, state, vm, onEdit = { editing = message }, onLongPress = { actionMessage = message })
                }
                if (state.busy != null || state.streamingText.isNotBlank()) item(key = "reply") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        CoachAvatar(32)
                        Column(Modifier.weight(1f)) {
                            Surface(color = Color.White, shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                                if (state.streamingText.isNotBlank()) Text(state.streamingText, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                                else Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(state.busy ?: "正在回复…", color = Muted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (state.busy != null) TextButton(onClick = vm::cancelOperation) { Text("取消等待") }
                        }
                    }
                }
            }
        }
        if (state.needsReply && !locked) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("上一条消息还没有回复", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Muted)
            TextButton(onClick = vm::reply) { Text("重试") }
        }
        if (session.messages.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = vm::layeredHint, enabled = !locked) { Text("表达提示") }
            TextButton(onClick = vm::topicExpressions, enabled = !locked && session.messages.any { it.isConversation && it.text.isNotBlank() }) { Text("话题语料") }
            TextButton(onClick = { vm.showRecaps(true, RecapKind.LATEST) }, enabled = !state.voiceActive) { Text("复盘") }
            Box {
                IconButton(onClick = { moreVisible = true }) { Icon(Icons.Outlined.MoreHoriz, "更多学习操作") }
                DropdownMenu(expanded = moreVisible, onDismissRequest = { moreVisible = false }) {
                    DropdownMenuItem(text = { Text("回退上一轮") }, enabled = !locked && session.messages.any { it.role == "user" }, onClick = { moreVisible = false; vm.redo() })
                    DropdownMenuItem(text = { Text("收藏好表达") }, onClick = { moreVisible = false; vm.summarize() })
                    DropdownMenuItem(text = { Text("练习录音") }, enabled = session.speakingPractices.isNotEmpty(), onClick = { moreVisible = false; practiceHistoryVisible = true })
                }
            }
        }
        if (!state.scenarioReadOnly) Surface(color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { typing = !typing }, enabled = !locked) {
                        Icon(if (typing) Icons.Outlined.Mic else Icons.Outlined.Keyboard, if (typing) "切换语音输入" else "切换文字输入")
                    }
                    if (typing) {
                        OutlinedTextField(value = state.draft, onValueChange = vm::draft, enabled = !locked, modifier = Modifier.weight(1f),
                            placeholder = { Text(if (state.inputKind == InputKind.HELP) "这句话怎么表达？" else "说说你的想法…") }, maxLines = 3, shape = RoundedCornerShape(18.dp))
                        FilledIconButton(onClick = vm::send, enabled = !locked && state.draft.isNotBlank() && !state.needsReply && state.pendingVoice == null, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ArrowUpward, "发送") }
                    } else {
                        Box(Modifier.weight(1f)) {
                            HoldToTalk(enabled = !locked && !state.needsReply && state.pendingVoice == null, recording = state.recording,
                                chinese = state.inputKind == InputKind.HELP, onStart = onRecord, onEnd = vm::stopRecording,
                                onCancel = vm::cancelRecording, onTap = { if (state.recording) vm.stopRecording() else onRecord() })
                        }
                        TextButton(onClick = { vm.inputKind(if (state.inputKind == InputKind.HELP) InputKind.ENGLISH else InputKind.HELP) },
                            enabled = !locked, contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = if (state.inputKind == InputKind.HELP) MaterialTheme.colorScheme.secondary else Muted)) {
                            Text(if (state.inputKind == InputKind.HELP) "切回英文" else "中文求助", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (state.playingId != null || state.preparingVoiceId != null) TextButton(onClick = vm::stopSpeaking, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Outlined.StopCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("停止播放")
                }
                if (typing) TextButton(onClick = { vm.inputKind(if (state.inputKind == InputKind.HELP) InputKind.ENGLISH else InputKind.HELP) }, enabled = !locked) {
                    Text(if (state.inputKind == InputKind.HELP) "切回英文" else "中文求助")
                }
                if (state.recording) Waveform(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 80.dp, vertical = 3.dp), active = true, level = state.recordingLevel, color = MaterialTheme.colorScheme.primary)
                else if (!typing) Text("松开发送，上滑取消", style = MaterialTheme.typography.labelSmall, color = Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp))
            }
        }
    }
    if (visible && state.appForeground) ScenarioReward(state, vm)
    }
    LearningOverlays(state, vm, onRecord)
    if (practiceHistoryVisible) AlertDialog(onDismissRequest = { practiceHistoryVisible = false }, title = { Text("练习录音") }, text = {
        LazyColumn { items(session.speakingPractices.asReversed(), key = { it.id }) { p ->
            TextButton(onClick = { vm.showSpeakingPractice(p.id); practiceHistoryVisible = false }, enabled = state.busy == null && !state.voiceActive) {
                Column(Modifier.fillMaxWidth()) {
                    Text(p.reference, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (p.sentMessageId.isNotBlank()) "已发送" else if (p.audioFileName.isBlank()) "尚未录音" else "${VoiceMessages.displaySeconds(p.audioDurationMs)} 秒", color = Muted)
                }
            }
        } }
    }, confirmButton = { TextButton(onClick = { practiceHistoryVisible = false }) { Text("关闭") } })

    actionMessage?.let { selected ->
        val usable = selected.text.isNotBlank()
        AlertDialog(onDismissRequest = { actionMessage = null }, title = { Text(if (selected.isHint) "提示操作" else "消息操作") },
            text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { actionMessage = null; vm.identifyExpressions(selected.id) }, enabled = usable && !locked, modifier = Modifier.fillMaxWidth()) { Text("识别好表达") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(if (selected.hintLayers.isNotEmpty()) selected.hintLayers.take(selected.revealedLayers).joinToString("\n\n") else selected.text)); actionMessage = null }, enabled = usable, modifier = Modifier.fillMaxWidth()) { Text("复制") }
                TextButton(onClick = { actionMessage = null; if (selected.role == "user") vm.optimizeExpression(selected.id) else vm.regenerate(selected.id) }, enabled = usable && !locked, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected.role == "user") "优化表达" else "重新生成")
                }
                if (!usable) Text("语音还没有文字，请先完成转写或补充文字。", color = Muted, style = MaterialTheme.typography.bodyMedium)
            } }, confirmButton = { TextButton(onClick = { actionMessage = null }) { Text("关闭") } })
    }
    editing?.let { message ->
        var text by remember(message.id) { mutableStateOf(message.text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("补充或修改转写") }, text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("写下你刚才想表达的内容") }, minLines = 3, maxLines = 8)
        }, confirmButton = { TextButton(onClick = { vm.editTranscript(message.id, text); editing = null }, enabled = text.isNotBlank() && !locked) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
}

@Composable
private fun ScenarioPanel(state: PracticeState, vm: PracticeViewModel, practice: ScenarioPractice) {
    var correction by remember { mutableStateOf<ScenarioGoal?>(null) }
    Surface(color = SoftBlue, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.animateContentSize().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(practice.template.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${practice.template.goals.count { practice.achieved(it.id) }}/${practice.template.goals.size}", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = vm::toggleScenarioGoals) { Text(if (practice.goalsCollapsed) "展开" else "收起") }
            }
            AnimatedVisibility(visible = !practice.goalsCollapsed) { Column { practice.template.goals.forEach { goal ->
                val achieved = practice.achieved(goal.id)
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (achieved) "✓" else "○", color = if (achieved) MaterialTheme.colorScheme.primary else Muted)
                    Column(Modifier.weight(1f)) {
                        Text(goal.text)
                        val result = practice.assessment?.goals?.firstOrNull { it.goalId == goal.id }
                        if (result != null) {
                            Text("依据：${result.reason}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            result.evidence.firstOrNull()?.let { Text("消息证据：${it.quote}", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                        }
                    }
                    if (practice.phase == ScenarioPhase.ACTIVE) TextButton(onClick = { correction = goal }) { Text("更正") }
                }
            }
            } }
            when (practice.phase) {
                ScenarioPhase.ACTIVE -> if (!practice.goalsCollapsed) Text("已完成 ${practice.turns.size}${practice.template.maxTurns?.let { "/$it" } ?: ""} 轮", color = Muted, style = MaterialTheme.typography.bodySmall)
                ScenarioPhase.ENDED -> {
                    Text(if (practice.ending?.succeeded == true) "场景目标已达成" else "场景已结束，未计入成功", color = if (practice.ending?.succeeded == true) MaterialTheme.colorScheme.primary else Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = vm::continueScenario, enabled = state.busy == null) { Text("继续对话") }
                        TextButton(onClick = { vm.showRecaps(true, RecapKind.SCENARIO) }) { Text("场景复盘") }
                    }
                }
                ScenarioPhase.CONTINUED -> if (!practice.goalsCollapsed) TextButton(onClick = { vm.showRecaps(true, RecapKind.SCENARIO) }) { Text("查看场景复盘") }
            }
            if (practice.checkStatus == ScenarioCheckStatus.FAILED) {
                Text(practice.checkError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::retryScenarioCheck, enabled = state.busy == null) { Text("重试判定") }
            }
        }
    }
    correction?.let { goal ->
        var note by remember(goal.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { correction = null }, title = { Text("更正目标进度") },
            text = { OutlinedTextField(note, { note = it }, label = { Text("请写下依据") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { vm.correctScenarioGoal(goal.id, true, note); correction = null }, enabled = note.isNotBlank()) { Text("标为已完成") } },
            dismissButton = { TextButton(onClick = { vm.correctScenarioGoal(goal.id, false, note); correction = null }, enabled = note.isNotBlank()) { Text("标为未完成") } })
    }
}

@Composable fun CoachAvatar(size: Int = 44) {
    Box(Modifier.size(size.dp).background(SoftBlue, RoundedCornerShape((size * 0.32).dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Forum, null, Modifier.size((size * 0.52).dp), tint = MaterialTheme.colorScheme.secondary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(message: ChatMessage, state: PracticeState, vm: PracticeViewModel, onEdit: () -> Unit, onLongPress: () -> Unit) {
    val user = message.role == "user"
    val hasAudio = message.audioFileName.isNotBlank()
    var expanded by remember(message.id, state.settings.voiceReplies) { mutableStateOf(!state.settings.voiceReplies) }
    val playing = state.playingId == message.id
    val preparing = state.preparingVoiceId == message.id
    if (message.isHint) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
            Surface(color = SoftBlue, border = androidx.compose.foundation.BorderStroke(1.dp, Sky.copy(alpha = 0.22f)), shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { vm.hintCollapsed(message.id, !message.hintCollapsed) }, onLongClick = onLongPress, onLongClickLabel = "提示操作")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Outlined.Lightbulb, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(message.hintKind?.label ?: "学习辅助", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        IconButton(onClick = { vm.hintCollapsed(message.id, !message.hintCollapsed) }, modifier = Modifier.size(40.dp)) {
                            Icon(if (message.hintCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, if (message.hintCollapsed) "展开提示" else "收起提示")
                        }
                    }
                    if (!message.hintCollapsed) {
                        val current = LearningFeatures.hintCurrent(state.session, message)
                        if (message.hintLayers.isNotEmpty()) {
                            message.hintLayers.take(message.revealedLayers).forEachIndexed { index, layer ->
                                if (index > 0) HorizontalDivider(color = Sky.copy(alpha = .15f))
                                Text(listOf("关键词", "句子骨架", "参考表达")[index], style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                                Text(layer, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (message.revealedLayers < message.hintLayers.size) TextButton(onClick = { vm.revealHint(message.id) }, enabled = current) {
                                Text(if (message.revealedLayers == 1) "查看句子骨架" else "查看参考表达")
                            }
                        } else Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        if (!current) Text("对话或原句已变化，请重新获取提示。", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        if (message.hintKind == HintKind.OPTIMIZE) TextButton(onClick = { vm.openSpeakingPractice(message.id) }, enabled = current && state.busy == null && !state.voiceActive) { Text("重说练习") }
                    }
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!user) { CoachAvatar(32); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 292.dp), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
            Surface(color = if (user) VoiceGreen else Color.White, border = if (user) null else androidx.compose.foundation.BorderStroke(1.dp, Line),
                shape = if (user) RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
                modifier = Modifier.combinedClickable(onClick = { if (hasAudio || !user) vm.playMessage(message) }, onLongClick = onLongPress, onLongClickLabel = "消息操作")) {
                Column {
                    if (hasAudio || !user && !expanded) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 172.dp).heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                            if (preparing) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                            else Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "停止播放" else "播放语音", Modifier.size(23.dp), tint = Ink)
                            Spacer(Modifier.width(12.dp))
                            Waveform(Modifier.width(70.dp).height(24.dp), active = playing, color = Ink)
                            Spacer(Modifier.width(12.dp))
                            Text(if (hasAudio) "${VoiceMessages.displaySeconds(message.audioDurationMs)}″" else "听回复", color = Ink)
                        }
                    }
                    if (!hasAudio && user || expanded && message.text.isNotBlank()) Text(message.text, Modifier.padding(horizontal = 15.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge, color = Ink)
                    if (hasAudio && message.transcriptState == TranscriptState.PENDING) Text("正在转文字…", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasAudio || !user) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (expanded) "收起原文" else if (user) "查看转写" else "英文原文", style = MaterialTheme.typography.labelSmall)
                }
                if (user) TextButton(onClick = { vm.markStuck(message.id) }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (message.markedStuck) "✓ 已标记卡壳" else "这里卡了", style = MaterialTheme.typography.labelSmall, color = if (message.markedStuck) MaterialTheme.colorScheme.primary else Muted)
                } else if (expanded) IconButton(onClick = { vm.playMessage(message) }, modifier = Modifier.size(40.dp)) { Icon(if (playing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, "朗读原文", Modifier.size(17.dp)) }
            }
            if (hasAudio && message.transcriptState == TranscriptState.FAILED) Row {
                TextButton(onClick = { vm.transcribe(message.id) }, enabled = state.busy == null) { Text("重试转写") }
                TextButton(onClick = onEdit, enabled = state.busy == null) { Text("补充文字") }
            }
            if (user && state.correctionPendingId == message.id) Text("正在检查表达…", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (user && state.correctionFailedId == message.id) TextButton(onClick = { vm.retryCorrection(message.id) }, enabled = state.busy == null) { Text("纠错未完成，重试") }
        }
        if (user) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(32.dp).background(SoftGreen, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(20.dp), tint = Muted) } }
    }
}
