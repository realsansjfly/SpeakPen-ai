package com.speakpen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.Dictionary
import com.speakpen.app.data.VocabularyEntry
import com.speakpen.app.data.vocabularyKey
import com.speakpen.app.data.mergeDictionaryEntries

/**
 * 单词本：用户提供的 Anki 四级/六级/雅思词表，跨表合并义项后按字母浏览。
 * 点词开 [WordDetailDialog]；导入沿用设置里那套 TXT/CSV 导入。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DictionaryScreen(state: PracticeState, vm: PracticeViewModel) {
    if (state.vocabularyLoading) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Text("词库正在加载…", Modifier.padding(top = 12.dp), color = Muted)
        }
        return
    }
    var level by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var importing by rememberSaveable { mutableStateOf(false) }
    val entries = remember(state.vocabularyBooks) {
        mergeDictionaryEntries(state.vocabularyBooks.filter { it.builtIn && !it.archived }.flatMap { it.entries })
    }
    val filtered = remember(entries, level, query) {
        val needle = query.trim().lowercase()
        entries.asSequence()
            .filter { level == "all" || level in it.levels.split(' ') }
            .filter { needle.isEmpty() || it.expression.lowercase().contains(needle) || it.meaning.lowercase().contains(needle) }
            .sortedBy { it.expression.lowercase() }
            .toList()
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("搜英文单词或中文释义", color = Muted) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部", "cet4" to "四级", "cet6" to "六级", "ielts" to "雅思").forEach { (tag, label) ->
                    FilterChip(selected = level == tag, onClick = { level = tag }, label = { Text(label) })
                }
            }
            Text("${filtered.size} 个词 ${'·'} 释义存在本机，不联网", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (filtered.isEmpty()) {
            val typed = Dictionary.query(query)
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本机词库没有匹配「${query.trim()}」的词。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                if (typed != null) Button(onClick = { vm.lookupWord(typed) }) { Text("查一下「$typed」") }
            }
        } else {
            val groups = remember(filtered) {
                filtered.groupBy { entry ->
                    entry.expression.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
                }.toSortedMap()
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                groups.forEach { (letter, words) ->
                    item(key = "letter-$letter") {
                        Text(letter.toString(), Modifier.fillMaxWidth().background(Paper)
                            .padding(start = 20.dp, top = 12.dp, bottom = 6.dp), color = Leaf, style = MaterialTheme.typography.labelLarge)
                    }
                    items(words, key = { it.id }) { entry -> WordRow(entry) { vm.lookupWord(entry.expression) } }
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("用户提供的 Anki 词表", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { importing = true }) { Text("导入词库") }
        }
    }
    if (importing) VocabularyScreen(state, vm, selection = null, onChoose = {}, onDismiss = { importing = false })
}

@Composable
private fun WordRow(entry: VocabularyEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.expression, style = MaterialTheme.typography.headlineMedium)
                if (entry.phonetic.isNotBlank()) {
                    Text(entry.phonetic, color = Muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(entry.effectiveMeaning.lineSequence().firstOrNull().orEmpty().split('；', ';').take(2).joinToString("；"),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.example.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.let { example ->
                Text(example, Modifier.padding(top = 8.dp), color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
