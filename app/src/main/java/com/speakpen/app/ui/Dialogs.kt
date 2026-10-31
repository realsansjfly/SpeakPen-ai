package com.speakpen.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*

@Composable
fun ReviewEditor(item: ReviewItem, onDismiss: () -> Unit, onSave: (ReviewItem) -> Unit) {
    var kind by remember(item.id) { mutableStateOf(item.kind) }
    var content by remember(item.id) { mutableStateOf(item.content) }
    var meaning by remember(item.id) { mutableStateOf(item.meaning) }
    var example by remember(item.id) { mutableStateOf(item.example) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item.content.isBlank()) "积累好表达" else "编辑复习目标") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ReviewKind.entries.forEach { value -> FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value.label) }) } }
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(if (kind == ReviewKind.TOPIC) "想练的话题" else "英文词汇或表达") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = meaning, onValueChange = { meaning = it }, label = { Text("中文释义 / 练习目标") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("例句（可选）") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            }
        }, confirmButton = { TextButton(onClick = { onSave(item.copy(kind = kind, content = content.trim(), meaning = meaning.trim(), example = example.trim())) }, enabled = content.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun PracticeDialogs(state: PracticeState, vm: PracticeViewModel) {
    if (state.scenarioPickerVisible) ScenarioPicker(state, vm)
    state.scenarioEditor?.let { ScenarioEditor(it, vm::editScenario, vm::saveScenario) }
    state.accumulationEditor?.let { item -> ReviewEditor(item, onDismiss = vm::dismissAccumulation, onSave = vm::saveAccumulation) }
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
                            Column { Text(item.content, style = MaterialTheme.typography.titleMedium); Text(item.kind.label + if (item.meaning.isBlank()) "" else "：${item.meaning}", style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2) }
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
                                Text(candidate.item.meaning, color = Muted, style = MaterialTheme.typography.bodyMedium)
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
        editing?.let { item -> ReviewEditor(item, onDismiss = { editing = null }, onSave = { vm.editCandidate(it); editing = null }) }
    }
}

@Composable
private fun ScenarioPicker(state: PracticeState, vm: PracticeViewModel) {
    val templates = (ScenarioPresets.all + state.scenarioTemplates).distinctBy { it.id }
    AlertDialog(onDismissRequest = { vm.showScenarios(false) }, title = { Text("开始场景练习") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            templates.forEach { template ->
                OutlinedButton(onClick = { vm.startScenario(template) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text(template.name)
                        Text("AI：${template.aiRole}；我：${template.userRole}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        val count = state.scenarioSuccesses.count { it.templateId == template.id }
                        if (count > 0) Text("已完成 $count 次", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(onClick = { vm.editScenario(ScenarioTemplate()) }, modifier = Modifier.fillMaxWidth()) { Text("自建场景") }
        } }, confirmButton = { TextButton(onClick = { vm.showScenarios(false) }) { Text("取消") } })
}

@Composable
private fun ScenarioEditor(initial: ScenarioTemplate, onDismiss: (ScenarioTemplate?) -> Unit, onSave: (ScenarioTemplate) -> Unit) {
    var value by remember(initial.id) { mutableStateOf(initial) }
    var goals by remember(initial.id) { mutableStateOf(initial.goals.joinToString("\n") { it.text }) }
    AlertDialog(onDismissRequest = { onDismiss(null) }, title = { Text("自建场景") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value.name, { value = value.copy(name = it) }, label = { Text("场景名称／情境") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.aiRole, { value = value.copy(aiRole = it) }, label = { Text("AI 角色") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.userRole, { value = value.copy(userRole = it) }, label = { Text("我的角色") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(goals, { goals = it }, label = { Text("目标清单（每行一项）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.opening, { value = value.copy(opening = it) }, label = { Text("开场白（留空由 AI 生成）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.endCondition, { value = value.copy(endCondition = it) }, label = { Text("自然语言结束条件") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value.maxTurns?.toString().orEmpty(), { value = value.copy(maxTurns = it.toIntOrNull()) }, label = { Text("轮数上限（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onSave(value.copy(goals = goals.lines().map { ScenarioGoal(text = it.trim()) }.filter { it.text.isNotBlank() })) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { onDismiss(null) }) { Text("取消") } })
}


