package com.speakpen.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*

@Composable
fun ReviewEditor(item: ReviewItem, onDismiss: () -> Unit, onSave: (ReviewItem) -> Unit,
                 state: PracticeState? = null, vm: PracticeViewModel? = null, autoLookup: Boolean = false) {
    var kind by remember(item.id) { mutableStateOf(item.kind) }
    var content by remember(item.id) { mutableStateOf(item.content) }
    var meaning by remember(item.id) { mutableStateOf(item.meaning) }
    var example by remember(item.id) { mutableStateOf(item.example) }
    val suggestion = state?.meaningSuggestion?.takeIf { it.itemId == item.id }
    fun composed() = item.copy(kind = kind, content = content.trim(), meaning = meaning.trim(), example = example.trim())
    val close = { vm?.dismissMeaning(); onDismiss() }
    LaunchedEffect(item.id, autoLookup) { if (autoLookup && suggestion == null) vm?.requestMeaning(composed()) }
    AlertDialog(onDismissRequest = close, title = { Text(if (item.content.isBlank()) "积累好表达" else "编辑复习目标") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ReviewKind.entries.forEach { value -> FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value.label) }) } }
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(if (kind == ReviewKind.TOPIC) "想练的话题" else "英文词汇或表达") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = meaning, onValueChange = { meaning = it }, label = { Text("中文释义 / 练习目标") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                state?.error?.let { message -> Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (vm != null && meaning.isBlank() && item.aiMeaning.isBlank() && MeaningLookup.queryOf(content) != null) {
                    TextButton(onClick = { vm?.requestMeaning(composed()) }, enabled = state?.busy == null) {
                        Text(if (state?.busy != null) "正在查释义…" else "不知道意思？让 AI 查一下")
                    }
                }
                suggestion?.let { value ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (value.demo) "示例释义（未接入服务）" else "AI 释义，未核对", style = MaterialTheme.typography.labelLarge)
                            Text(value.meaning, style = MaterialTheme.typography.bodyMedium)
                            if (value.example.isNotBlank()) Text(value.example, color = Muted, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vm?.dismissMeaning(); onSave(MeaningLookup.apply(composed(), value)) }) { Text("收下") }
                                TextButton(onClick = { if (meaning.isBlank()) meaning = value.meaning; if (example.isBlank()) example = value.example; vm?.dismissMeaning() }) { Text("填入输入框") }
                                TextButton(onClick = { vm?.dismissMeaning() }) { Text("丢弃") }
                            }
                        }
                    }
                }
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("例句（可选）") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            }
        }, confirmButton = { TextButton(onClick = { vm?.dismissMeaning(); onSave(composed()) }, enabled = content.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
fun PracticeDialogs(state: PracticeState, vm: PracticeViewModel) {
    if (state.scenarioPickerVisible) ScenarioPicker(state, vm)
    state.scenarioEditor?.let { ScenarioEditor(it, vm::editScenario, vm::saveScenario) }
    state.scenarioBrief?.let { ScenarioBrief(it, vm) }
    state.accumulationEditor?.let { item -> ReviewEditor(item, onDismiss = vm::dismissAccumulation, onSave = vm::saveAccumulation, state = state, vm = vm) }
    if (state.historyVisible) AlertDialog(onDismissRequest = { vm.showHistory(false) }, title = { Text("练习记录") },
        text = { LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ConversationSessions.history(state.sessions).isEmpty()) item { Text("还没有练习记录。发出第一条消息后，对话会出现在这里。", color = Muted) }
            items(ConversationSessions.history(state.sessions), key = { it.id }) { session ->
                Column(Modifier.fillMaxWidth().clickable { vm.selectSession(session.id) }.padding(vertical = 12.dp)) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium)
                    Text("${session.mode.label}，${session.messages.count { it.role == "user" }} 次表达，${dateLabel(session.updatedAt)}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                HorizontalDivider()
            }
        } }, confirmButton = { TextButton(onClick = { vm.showHistory(false) }) { Text("关闭") } })
    if (state.targetPickerVisible) {
        var selected by remember(state.targetPickerVisible) { mutableStateOf(state.session.targetIds.filter { id -> state.library.any { it.id == id } }.toSet()) }
        AlertDialog(onDismissRequest = { vm.showTargets(false) }, title = { Text("这次想练什么？") },
            text = { Column {
                Text(if (state.library.isEmpty()) "复习库还是空的。先添加词汇、表达或话题。" else "选择 1–5 项，AI 会围绕它们开展对话。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(state.library.sortedBy { it.nextReviewAt }, key = { it.id }) { item ->
                        val toggle = { if (item.id in selected) selected = selected - item.id else if (selected.size < 5) selected = selected + item.id }
                        Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.id in selected, onCheckedChange = { toggle() })
                            Column { Text(item.content, style = MaterialTheme.typography.titleMedium); Text(item.kind.label + if (item.effectiveMeaning.isBlank()) "" else "：${item.effectiveMeaning}", style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2) }
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { vm.newSession(ConversationMode.REVIEW, selected.toList()) }, enabled = selected.isNotEmpty() && state.busy == null) { Text("开始复习 (${selected.size})") } },
            dismissButton = { TextButton(onClick = { vm.showTargets(false); if (state.library.isEmpty()) vm.page(1) }) { Text(if (state.library.isEmpty()) "去添加" else "取消") } })
    }
    state.candidates?.let { candidates ->
        var editing by remember { mutableStateOf<ReviewItem?>(null) }
        AlertDialog(onDismissRequest = vm::dismissCandidates, title = { Text("带走几个好表达") },
            text = { Column {
                Text("请确认 AI 建议。这里只识别所选消息；可编辑、取消勾选后保存。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp)) {
                    if (candidates.isEmpty()) item { Text("本次没有候选，可在复习库手动添加。") }
                    items(candidates, key = { it.item.id }) { candidate ->
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(checked = candidate.selected, onCheckedChange = { vm.candidateSelected(candidate.item.id, it) })
                            Column(Modifier.weight(1f)) {
                                Text(candidate.item.content, style = MaterialTheme.typography.titleMedium)
                                Text(candidate.item.effectiveMeaning, color = Muted, style = MaterialTheme.typography.bodyMedium)
                                if (candidate.item.example.isNotBlank()) Text(candidate.item.example, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                                if (candidate.item.reason.isNotBlank()) Text(candidate.item.reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                                Text(candidate.item.sourceNote, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 3)
                                TextButton(onClick = { editing = candidate.item }) { Text("修改后再收藏") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } }, confirmButton = { TextButton(onClick = vm::saveCandidates, enabled = candidates.any { it.selected }) { Text("加入复习库 (${candidates.count { it.selected }})") } },
            dismissButton = { TextButton(onClick = vm::dismissCandidates) { Text("暂不收藏") } })
        editing?.let { item -> ReviewEditor(item, onDismiss = { editing = null }, onSave = { vm.editCandidate(it); editing = null }, state = state, vm = vm) }
    }
}

@Composable
private fun ScenarioPicker(state: PracticeState, vm: PracticeViewModel) {
    val templates = (ScenarioPresets.all + state.scenarioTemplates).distinctBy { it.id }
    LearningSurface("场景练习", { vm.showScenarios(false) }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("今天想练什么？", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 12.dp)) }
            items(templates, key = { it.id }) { template ->
                Surface(onClick = { vm.showScenarioBrief(template) }, color = SoftGreen, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(template.name, style = MaterialTheme.typography.titleLarge)
                        Text("${template.userRole} · ${template.aiRole}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        val count = state.scenarioSuccesses.count { it.templateId == template.id }
                        if (count > 0) Text("已完成 $count 次", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        OutlinedButton(onClick = { vm.editScenario(ScenarioTemplate()) }, modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("自建场景")
        }
    }
}

@Composable
private fun ScenarioEditor(initial: ScenarioTemplate, onDismiss: (ScenarioTemplate?) -> Unit, onSave: (ScenarioTemplate) -> Unit) {
    var value by remember(initial.id) { mutableStateOf(initial) }
    var goals by remember(initial.id) { mutableStateOf(initial.goals.joinToString("\n") { it.text }) }
    var leaving by remember { mutableStateOf(false) }
    var turns by androidx.compose.runtime.saveable.rememberSaveable(initial.id) { mutableStateOf(initial.maxTurns?.toString().orEmpty()) }
    var validation by remember { mutableStateOf<String?>(null) }
    fun close() { if (value != initial || goals != initial.goals.joinToString("\n") { it.text } || turns != initial.maxTurns?.toString().orEmpty()) leaving = true else onDismiss(null) }
    LearningSurface("自建场景", onClose = ::close, actions = {
        TextButton(onClick = {
            val result = value.copy(goals = goals.lines().map { ScenarioGoal(text = it.trim()) }.filter { it.text.isNotBlank() }, maxTurns = turns.toIntOrNull())
            validation = if (turns.isNotBlank() && turns.toIntOrNull() == null) "轮数上限须为 1–100 的整数。" else result.validationError()
            if (validation == null) onSave(result)
        }) { Text("保存") }
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("情境与角色", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value.name, { value = value.copy(name = it) }, label = { Text("场景名称／情境") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.aiRole, { value = value.copy(aiRole = it) }, label = { Text("AI 角色") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.userRole, { value = value.copy(userRole = it) }, label = { Text("我的角色") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("任务目标", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(goals, { goals = it }, label = { Text("目标清单（每行一项）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("对话安排", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value.opening, { value = value.copy(opening = it) }, label = { Text("开场白（留空由 AI 生成）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.endCondition, { value = value.copy(endCondition = it) }, label = { Text("自然语言结束条件") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(turns, { turns = it }, label = { Text("轮数上限（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("放弃未保存的场景？") },
        confirmButton = { TextButton(onClick = { onDismiss(null) }) { Text("放弃更改") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("继续编辑") } })
}


