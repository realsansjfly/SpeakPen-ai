package com.speakpen.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(state: PracticeState, vm: PracticeViewModel) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ReviewKind?>(null) }
    var editItem by remember { mutableStateOf<ReviewItem?>(null) }
    var deletion by remember { mutableStateOf<ReviewItem?>(null) }
    val now = System.currentTimeMillis()
    val due = state.library.count { it.nextReviewAt <= now }
    val shown = state.library.filter { item -> (kind == null || item.kind == kind) && (item.content.contains(query, true) || item.meaning.contains(query, true)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text("${state.library.size} 项收藏，$due 项待复习", color = Muted, style = MaterialTheme.typography.bodyMedium) }
            FilledIconButton(onClick = { editItem = ReviewItem(content = "") }) { Icon(Icons.Outlined.Add, "手动添加") }
        }
        if (state.library.isNotEmpty()) Button(onClick = { vm.showTargets(true) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = state.busy == null && !state.voiceActive) {
            Icon(Icons.Outlined.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("选几个目标，开始复习对话")
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("搜索词汇、表达、中文释义") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("全部") })
            ReviewKind.entries.forEach { value -> FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value.label) }) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shown.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp)) {
                    Text(if (state.library.isEmpty()) "Your next words\nstart here." else "暂时没有匹配内容", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp)); Text("聊完后点击“收藏好表达”，也可以手动记下想练的表达。", color = Muted)
                }
            }
            items(shown, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (item.nextReviewAt <= now) "待复习" else "下次 ${dateLabel(item.nextReviewAt)}", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { editItem = item }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Edit, "编辑", Modifier.size(18.dp)) }
                            IconButton(onClick = { deletion = item }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.DeleteOutline, "删除", Modifier.size(18.dp)) }
                        }
                        Text(item.content, style = MaterialTheme.typography.titleMedium)
                        if (item.meaning.isNotBlank()) Text(item.meaning, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 6.dp))
                        if (item.example.isNotBlank()) Text(item.example, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                        if (item.sourceNote.isNotBlank()) Text(item.sourceNote, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 8.dp), maxLines = 3)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.reviewResult(item.id, false) }, modifier = Modifier.weight(1f)) { Text("需要再练") }
                            Button(onClick = { vm.reviewResult(item.id, true) }, modifier = Modifier.weight(1f)) { Text("这次会了") }
                        }
                    }
                }
            }
        }
    }
    editItem?.let { item -> ReviewEditor(item, onDismiss = { editItem = null }, onSave = { vm.upsert(it); editItem = null }) }
    deletion?.let { item -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("删除这项复习内容？") }, text = { Text(item.content) },
        confirmButton = { TextButton(onClick = { vm.deleteItem(item.id); deletion = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deletion = null }) { Text("保留") } }) }
}

fun dateLabel(time: Long): String = SimpleDateFormat("MM月dd日", Locale.CHINA).format(Date(time))


