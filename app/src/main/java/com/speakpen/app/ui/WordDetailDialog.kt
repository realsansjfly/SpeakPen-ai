package com.speakpen.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.Dictionary
import com.speakpen.app.data.MeaningLookup
import com.speakpen.app.data.ReviewItem
import com.speakpen.app.data.ReviewKind
import com.speakpen.app.data.VocabularyEntry

/**
 * 抽屉查词、单词本词条、以及「本地没收录」的 AI 兜底，全部落到这一个小对话框里。
 * 本地字典命中时零网络请求：释义、音标、词性、柯林斯星级、词形变化都在 APK 里。
 */
@Composable
fun WordDetailDialog(state: PracticeState, vm: PracticeViewModel) {
    val query = state.wordQuery ?: return
    val entry: VocabularyEntry? = vm.wordEntry(query)
    // ReviewItem 的 id 默认由 newId() 生成：直接在组合里构造会让每次重组都换 id，
    // 于是回来找建议时永远对不上，按钮点了就像没反应。查询保持不变时 id 必须保持稳定。
    val fallback = remember(query) {
        ReviewItem(content = query, kind = if (query.contains(' ')) ReviewKind.PHRASE else ReviewKind.WORD)
    }
    val suggestion = state.meaningSuggestion?.takeIf {
        it.itemId == fallback.id || it.expression.equals(query.trim(), ignoreCase = true)
    }
    val looking = suggestion == null && state.busy != null
    val saved = vm.libraryItem(query)
    LearningSurface("词条详情", onClose = vm::closeWord) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(entry?.expression ?: query, style = MaterialTheme.typography.headlineMedium)
                        val phonetic = entry?.phonetic.orEmpty()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (phonetic.isNotBlank()) Text(phonetic, Modifier.weight(1f, fill = false), color = Muted, style = MaterialTheme.typography.bodyMedium)
                            val stars = entry?.let(Dictionary::collinsStars).orEmpty()
                            if (stars.isNotBlank()) Text(stars, color = Amber, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    IconButton(onClick = vm::closeWord) { Icon(Icons.Outlined.Close, "关闭查词") }
                }
                if (entry != null) {
                    val chips = buildList {
                        entry.pos.takeIf { it.isNotBlank() }?.let { add(it) }
                        Dictionary.levelsLabel(entry).takeIf { it.isNotBlank() }?.let { add(it) }
                        if (entry.collins > 0) add("柯林斯 ${entry.collins} 星")
                    }
                    if (chips.isNotEmpty()) Text(chips.joinToString(" · "), color = Muted, style = MaterialTheme.typography.labelLarge)
                    if (entry.effectiveMeaning.isNotBlank()) {
                        var meanings by remember(entry.id) { mutableStateOf(false) }
                        SelectionContainer { Text(if (meanings) Dictionary.fullGloss(entry) else entry.effectiveMeaning.lineSequence().first().split('；', ';').take(2).joinToString("；"), style = MaterialTheme.typography.titleLarge) }
                        TextButton(onClick = { meanings = !meanings }) { Text(if (meanings) "收起义项" else "全部义项") }
                    }
                    else Text("原卡片未填写中文释义。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    val exchange = Dictionary.exchangeLines(entry)
                    if (exchange.isNotEmpty()) {
                        var forms by remember(entry.id) { mutableStateOf(false) }
                        TextButton(onClick = { forms = !forms }) { Text(if (forms) "收起词形变化" else "词形变化") }
                        if (forms) Text(exchange.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val single = query.trim().length <= 1
                    Text(
                        when {
                            single -> "原词表没有收录「${query.trim()}」，可以按需查询 AI 释义。"
                            looking -> "本地没有收录「$query」，正在问 AI 服务…"
                            else -> "本地 Anki 词表没有收录「$query」。"
                        },
                        color = Muted, style = MaterialTheme.typography.bodyMedium
                    )
                    vm.wordSuggestion(query)?.let { near ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("是不是想查 ${near.expression}？", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.lookupWord(near.expression) }) { Text("改查这个") }
                        }
                    }
                    when {
                        suggestion != null -> Surface(shape = RoundedCornerShape(16.dp), color = SoftBlue) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Eyebrow(if (suggestion.demo) "示例释义（未接入服务）" else "AI 释义，未核对")
                                Text(suggestion.meaning, style = MaterialTheme.typography.bodyMedium)
                                if (suggestion.example.isNotBlank()) Text(suggestion.example, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(state.busy ?: "正在查…", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        else -> Text("释义不在本机，需要问一次 AI 服务（会消耗一次模型请求）。",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.error?.let { InlineNotice(it, isError = true, onClose = vm::clearFeedback) }
                entry?.example?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge) }
                entry?.details?.forEach { detail ->
                    var expanded by remember(entry.id, detail.title) { mutableStateOf(false) }
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(detail.title, Modifier.weight(1f)); Text(if (expanded) "收起" else "展开")
                            }
                        }
                    if (expanded) SelectionContainer {
                        Text(detail.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val localExamples = entry?.details?.any { it.title == "例句" } == true
                if (!localExamples && entry?.example.isNullOrBlank()) Text("例句", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                val example = state.wordExample?.takeIf { state.wordExampleQuery == query }
                when {
                    localExamples -> Unit
                    entry?.example?.isNotBlank() == true -> {
                        var expanded by remember(entry.id) { mutableStateOf(false) }
                        if (entry.example.lineSequence().count() > 1) {
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起其他例句" else "其他例句") }
                            if (expanded) Text(entry.example.lineSequence().drop(1).joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    example != null -> Text(example, style = MaterialTheme.typography.bodyMedium)
                    entry == null && suggestion?.example?.isNotBlank() == true -> Text(suggestion.example, style = MaterialTheme.typography.bodyMedium)
                    state.wordBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在生成…", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Text("原卡片没有例句，可按需生成。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!localExamples && entry?.example.isNullOrBlank() && example == null && entry != null) TextButton(onClick = vm::requestWordExample, enabled = !state.wordBusy) { Text("生成例句") }
                    if (entry == null && suggestion == null) TextButton(
                        onClick = { vm.requestMeaning(fallback) },
                        enabled = !looking && !state.wordBusy
                    ) {
                        if (looking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在查…")
                        } else Text("让 AI 查意思")
                    }
                    if (entry != null) {
                        if (saved != null) OutlinedButton(onClick = { vm.closeWord(); vm.page(1) }) { Text("已在积累库 · 去复习") }
                        else Button(onClick = { vm.addWordToLibrary(entry) }) { Text("加入积累库") }
                    } else if (suggestion != null) {
                        Button(onClick = {
                            vm.upsert(MeaningLookup.apply(fallback, suggestion)); vm.closeWord()
                        }) { Text("收下到积累库") }
                        TextButton(onClick = vm::dismissMeaning) { Text("丢弃") }
                    }
                }
                entry?.source?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = SoftGreen) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Ink, style = MaterialTheme.typography.labelSmall)
    }
}
