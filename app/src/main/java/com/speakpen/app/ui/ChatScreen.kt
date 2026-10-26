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
fun ChatScreen(state: PracticeState, vm: PracticeViewModel, onRecord: () -> Unit) {
    val session = state.session
    val locked = state.busy != null || state.voiceActive
    var typing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val displayMessages = remember(session.messages) { LearningMessages.display(session.messages) }
    LaunchedEffect(session.id, session.messages.size, state.busy, state.scrollToMessageId) {
        val anchor = state.scrollToMessageId ?: state.operationAnchorId
        val anchoredIndex = displayMessages.indexOfFirst { it.id == anchor }
        val count = displayMessages.size + if (state.busy != null || state.streamingText.isNotBlank()) 1 else 0
        if (anchoredIndex >= 0) listState.animateScrollToItem(anchoredIndex)
        else if (count > 0) listState.animateScrollToItem(count - 1)
        state.scrollToMessageId?.let(vm::scrollConsumed)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("开口聊聊", style = MaterialTheme.typography.titleLarge)
                Text(when { state.settings.demoMode -> "示例体验"; !state.serviceConfigured -> "AI 服务准备中"; !state.serviceOnline -> "正在检查连接 · 可到设置重新连接"; else -> "已连接 · 你的英语练习伙伴" }, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { vm.showHistory(true) }, enabled = !locked) { Icon(Icons.Outlined.History, "练习记录") }
            IconButton(onClick = { vm.newSession(session.mode, session.targetIds) }, enabled = !locked) { Icon(Icons.Outlined.AddComment, "新对话") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = session.mode == ConversationMode.FREE, onClick = { if (session.mode != ConversationMode.FREE) vm.newSession(ConversationMode.FREE) }, label = { Text("自由聊") }, enabled = !locked,
                leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp)) })
            FilterChip(selected = session.mode == ConversationMode.REVIEW, onClick = { vm.showTargets(true) }, label = { Text("练收藏") }, enabled = !locked,
                leadingIcon = { Icon(Icons.Outlined.AutoStories, null, Modifier.size(16.dp)) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.preferences(state.settings.copy(voiceReplies = !state.settings.voiceReplies)) }) {
                Icon(if (state.settings.voiceReplies) Icons.Outlined.GraphicEq else Icons.AutoMirrored.Outlined.Subject, "切换 AI 语音块与原文")
            }
        }
        if (session.mode == ConversationMode.REVIEW) Text("目标 · " + state.library.filter { it.id in session.targetIds }.joinToString(" · ") { it.content },
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2)
        Text("表达难度 · ${session.config.difficulty.label}", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        HorizontalDivider(color = Line)
        if (session.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(74.dp).background(SoftGreen, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.WavingHand, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(22.dp))
                Text("A little talk.\nA little progress.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                Text("不必说得完美，先说一句。\n卡住了，也可以用中文问我。", color = Muted, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(onClick = { vm.startConversation("my day") }, label = { Text("聊聊今天") }, enabled = !locked)
                    SuggestionChip(onClick = { vm.startConversation("travel") }, label = { Text("旅行计划") }, enabled = !locked)
                }
                if (!state.serviceConfigured && !state.settings.demoMode) TextButton(onClick = { vm.preferences(state.settings.copy(demoMode = true)); vm.startConversation() }) { Text("先体验示例对话") }
                else OutlinedButton(onClick = { vm.startConversation() }, enabled = !locked) { Text("让 AI 先开口") }
            }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = vm::redo, enabled = !locked && session.messages.any { it.role == "user" }) { Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("重说") }
            TextButton(onClick = vm::topicExpressions, enabled = !locked && session.messages.any { it.isConversation && it.text.isNotBlank() }) { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("话题语料") }
            TextButton(onClick = vm::summarize, enabled = !locked) { Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("收藏好表达") }
        }
        Surface(color = Color.White, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.inputKind(if (state.inputKind == InputKind.HELP) InputKind.ENGLISH else InputKind.HELP) }, enabled = !locked,
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.inputKind == InputKind.HELP) MaterialTheme.colorScheme.secondary else Muted)) {
                        Icon(Icons.Outlined.Translate, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(if (state.inputKind == InputKind.HELP) "中文求助中 · 切回英文" else "卡住了？用中文问")
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.playingId != null || state.preparingVoiceId != null) IconButton(onClick = vm::stopSpeaking) { Icon(Icons.Outlined.StopCircle, "停止播放") }
                    IconButton(onClick = { typing = !typing }, enabled = !locked) { Icon(if (typing) Icons.Outlined.Mic else Icons.Outlined.Keyboard, if (typing) "语音输入" else "文字输入") }
                }
                if (typing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.draft, onValueChange = vm::draft, enabled = !locked, modifier = Modifier.weight(1f),
                        placeholder = { Text(if (state.inputKind == InputKind.HELP) "这句话怎么表达？" else "Say something…") }, maxLines = 3, shape = RoundedCornerShape(18.dp))
                    FilledIconButton(onClick = vm::send, enabled = !locked && state.draft.isNotBlank() && !state.needsReply && state.pendingVoice == null, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ArrowUpward, "发送") }
                } else HoldToTalk(enabled = !locked && !state.needsReply && state.pendingVoice == null, recording = state.recording,
                    chinese = state.inputKind == InputKind.HELP, onStart = onRecord, onEnd = vm::stopRecording,
                    onCancel = vm::cancelRecording, onTap = { if (state.recording) vm.stopRecording() else onRecord() })
                if (state.recording) {
                    Waveform(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 80.dp, vertical = 3.dp), active = true, level = state.recordingLevel, color = MaterialTheme.colorScheme.primary)
                } else Text(if (typing) "文字也能练习，自在表达就好" else "松开发送 · 上滑取消 · 点击语音块重听", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp))
            }
        }
    }
    actionMessage?.let { selected ->
        val usable = selected.text.isNotBlank()
        AlertDialog(onDismissRequest = { actionMessage = null }, title = { Text(if (selected.isHint) "提示操作" else "消息操作") },
            text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (selected.isHint) "提示不进入对话上下文" else "仅针对选中的这一块", color = Muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { actionMessage = null; vm.identifyExpressions(selected.id) }, enabled = usable && !locked, modifier = Modifier.fillMaxWidth()) { Text("识别好表达") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(selected.text)); actionMessage = null }, enabled = usable, modifier = Modifier.fillMaxWidth()) { Text("复制") }
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
                        Text("提示 · ${message.hintKind?.label ?: "学习辅助"}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        IconButton(onClick = { vm.hintCollapsed(message.id, !message.hintCollapsed) }, modifier = Modifier.size(40.dp)) {
                            Icon(if (message.hintCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, if (message.hintCollapsed) "展开提示" else "收起提示")
                        }
                    }
                    if (!message.hintCollapsed) Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    Text(if (message.hintCollapsed) "已折叠 · 点击展开，长按可操作" else "仅供参考，不进入对话上下文 · 点击收起，长按可操作", style = MaterialTheme.typography.labelSmall, color = Muted)
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
        }
        if (user) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(32.dp).background(SoftGreen, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(20.dp), tint = Muted) } }
    }
}
