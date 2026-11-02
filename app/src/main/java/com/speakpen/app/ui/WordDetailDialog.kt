package com.speakpen.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    Dialog(onDismissRequest = vm::closeWord) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(entry?.expression ?: query, style = MaterialTheme.typography.headlineMedium)
                        val phonetic = entry?.phonetic.orEmpty()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (phonetic.isNotBlank()) Text("/$phonetic/", color = Muted, style = MaterialTheme.typography.bodyMedium)
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
                    if (chips.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips.forEach { Pill(it) } }
                    Text(Dictionary.fullGloss(entry), style = MaterialTheme.typography.bodyLarge)
                    val exchange = Dictionary.exchangeLines(entry)
                    if (exchange.isNotEmpty()) {
                        Eyebrow("词形变化")
                        Text(exchange.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val single = query.trim().length <= 1
                    Text(
                        when {
                            single -> "「${query.trim()}」是单字母：内置词表只收考试词表里的词，冠词和单字母（a/an/the/I）不在其中。需要的话可以让 AI 解释一次。"
                            looking -> "本地没有收录「$query」，正在问 AI 服务…"
                            else -> "本地词库（四级/六级/雅思 ${'·'} 共 7638 词）没有收录「$query」。"
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
                Eyebrow("例句")
                val example = state.wordExample?.takeIf { state.wordExampleQuery == query }
                when {
                    example != null -> Text(example, style = MaterialTheme.typography.bodyMedium)
                    entry == null && suggestion?.example?.isNotBlank() == true -> Text(suggestion.example, style = MaterialTheme.typography.bodyMedium)
                    state.wordBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在生成…", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Text("本机不带例句，需要时再让 AI 生成一条。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (example == null && entry != null) TextButton(onClick = vm::requestWordExample, enabled = !state.wordBusy) { Text("生成例句") }
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
                    Spacer(Modifier.weight(1f))
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
