package com.speakpen.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.speakpen.app.PracticeState
import com.speakpen.app.PracticeViewModel
import com.speakpen.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A full-height task surface keeps the long import/preview flow out of small dialogs. */
@Composable
fun VocabularyScreen(state: PracticeState, vm: PracticeViewModel, selection: VocabularySelection?,
    onChoose: (VocabularySelection?) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("list") }
    var bookId by rememberSaveable { mutableStateOf("") }
    var bookVersion by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var metadata by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var definitionEntry by remember { mutableStateOf<VocabularyEntry?>(null) }
    var name by rememberSaveable { mutableStateOf(vm.vocabularyImportName) }
    var source by rememberSaveable { mutableStateOf(vm.vocabularyImportSource) }
    // Large input stays outside Android's saved-instance Bundle.
    var raw by remember { mutableStateOf(vm.vocabularyImportRaw) }
    var filename by rememberSaveable { mutableStateOf(vm.vocabularyImportFilename) }
    var format by rememberSaveable { mutableStateOf(vm.vocabularyImportFormat) }
    var preview by remember { mutableStateOf(vm.vocabularyImportPreview) }
    var importId by rememberSaveable { mutableStateOf(newId()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var parsing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val retainedRaw by rememberUpdatedState(raw)
    val retainedPreview by rememberUpdatedState(preview)
    val retainedName by rememberUpdatedState(name)
    val retainedSource by rememberUpdatedState(source)
    val retainedFilename by rememberUpdatedState(filename)
    val retainedFormat by rememberUpdatedState(format)
    DisposableEffect(vm) { onDispose {
        vm.vocabularyImportRaw = retainedRaw; vm.vocabularyImportPreview = retainedPreview
        vm.vocabularyImportName = retainedName; vm.vocabularyImportSource = retainedSource
        vm.vocabularyImportFilename = retainedFilename; vm.vocabularyImportFormat = retainedFormat
    } }
    LaunchedEffect(Unit) {
        if (mode == "preview" && preview == null) { mode = "import"; localError = "导入尚未保存，请重新选择文件或粘贴原文。" }
    }
    val context = LocalContext.current
    val locked = state.vocabularySaving || parsing
    val book = state.vocabularyBooks.firstOrNull { it.id == bookId && it.version == bookVersion }
    val latest = state.vocabularyBooks.groupBy { it.id }.map { (_, books) -> books.maxBy { it.createdAt } }
    fun inspect(value: VocabularyBook) { bookId = value.id; bookVersion = value.version; query = ""; mode = "detail" }
    fun back() {
        if (locked) return
        if (state.busy != null) { localError = "请先取消或完成当前请求。"; return }
        vm.dismissVocabularySuggestion(); localError = null
        when (mode) {
            "preview" -> { mode = "import"; preview = null }
            "import" -> if (raw.isNotBlank() || name.isNotBlank()) discard = true else mode = "list"
            "detail" -> { mode = "list"; query = "" }
            else -> onDismiss()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            parsing = true; localError = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val display = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }.orEmpty()
                    require(display.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("txt", "csv")) { "请选择 TXT 或 CSV 文件。" }
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesLimited() }
                    display to VocabularyImport.decode(bytes)
                }
                filename = result.first; raw = result.second
                format = if (filename.endsWith(".csv", true)) VocabularyFormat.CSV else VocabularyFormat.TXT
                if (name.isBlank()) name = filename.substringBeforeLast('.').take(80)
                if (source.isBlank()) source = filename.take(500)
                preview = null
            } catch (e: Exception) { localError = e.message ?: "文件读取失败，请重新选择。" }
            finally { parsing = false }
        }
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::back, enabled = !locked) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                Text(when (mode) { "import" -> "导入词库"; "preview" -> "导入预览"; "detail" -> book?.name ?: "词库"; else -> "词库增强" },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (locked) CircularProgressIndicator(Modifier.padding(16.dp).size(22.dp), strokeWidth = 2.dp)
            }
            val error = localError ?: state.error
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (state.busy != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.busy, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::cancelOperation) { Text("取消请求") }
            }
            when (mode) {
                "list" -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !locked) { onChoose(null); onDismiss() }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selection == null, onClick = { onChoose(null); onDismiss() }); Text("关闭")
                        }
                    }
                    items(latest, key = { it.id }) { value ->
                        HorizontalDivider(color = Line)
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selection?.id == value.id, onClick = { onChoose(value.selection()); onDismiss() }, enabled = !locked)
                            Column(Modifier.weight(1f).clickable { inspect(value) }.padding(vertical = 10.dp)) {
                                Text(value.name, style = MaterialTheme.typography.titleSmall)
                                Text("${value.entries.size} 条，${if (value.builtIn) "内置" else "导入"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { inspect(value) }) { Text("查看") }
                        }
                    }
                    item {
                        if (selection != null) Text("所选版本：${selection.name}（${selection.version.take(20)}）", style = MaterialTheme.typography.bodySmall, color = Muted)
                        Button(onClick = { mode = "import"; vm.clearFeedback() }, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("导入词库") }
                    }
                }
                "import" -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }, enabled = !locked) { Text("选择 TXT / CSV 文件") } }
                    item { OutlinedTextField(name, { name = it.take(80) }, label = { Text("词库名称") }, singleLine = true, enabled = !locked, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(source, { source = it.take(500) }, label = { Text("来源") }, enabled = !locked, modifier = Modifier.fillMaxWidth()) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VocabularyFormat.entries.forEach { option ->
                        FilterChip(selected = format == option, onClick = { format = option }, enabled = !locked, label = { Text(option.name) })
                    } } }
                    item { OutlinedTextField(raw, { if (it.toByteArray(Charsets.UTF_8).size <= VocabularyImport.MAX_BYTES) { raw = it; localError = null } else localError = "内容超过 2 MiB，请拆分。" },
                        label = { Text("单词或短语") }, placeholder = { Text(if (format == VocabularyFormat.TXT) "每行一项；可用制表符分隔释义和例句" else "expression,meaning,example,tags") },
                        minLines = 6, maxLines = 12, enabled = !locked, modifier = Modifier.fillMaxWidth()) }
                    item { Text("UTF-8 编码，最多 2 MiB、20,000 条。CSV 可含 expression、meaning、example、tags 表头；无表头时按此顺序。", style = MaterialTheme.typography.bodySmall, color = Muted) }
                    item { Button(onClick = { scope.launch {
                        parsing = true; localError = null
                        try { preview = withContext(Dispatchers.Default) { VocabularyImport.parse(raw, format) }; mode = "preview" }
                        finally { parsing = false }
                    } }, enabled = !locked && name.isNotBlank() && source.isNotBlank() && raw.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("解析预览") } }
                }
                "preview" -> preview?.let { parsed ->
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("有效 ${parsed.entries.size} 条，重复 ${parsed.duplicates} 条，空行 ${parsed.blankLines} 行", style = MaterialTheme.typography.titleSmall) }
                        parsed.fatal?.let { fatal -> item { Text(fatal, color = MaterialTheme.colorScheme.error) } }
                        items(parsed.issues.take(50)) { Text("第 ${it.line} 行：${it.reason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (parsed.issues.size > 50) item { Text("另有 ${parsed.issues.size - 50} 处错误，请返回修改。") }
                        item { Text("重复按词语、释义及例句去重，不同词义保留。原文与来源保存在本机；不会自动启用。", color = Muted, style = MaterialTheme.typography.bodySmall) }
                        item { Button(onClick = {
                            vm.saveVocabulary(VocabularyBook(id = importId, name = name.trim(), source = source.trim(), originalText = raw,
                                fileName = filename, format = format.name, entries = parsed.entries)) { saved ->
                                if (saved != null) { raw = ""; name = ""; source = ""; filename = ""; importId = newId(); preview = null; inspect(saved) }
                            }
                        }, enabled = parsed.canSave && !locked, modifier = Modifier.fillMaxWidth()) {
                            Text(if (parsed.issues.isEmpty()) "确认保存 ${parsed.entries.size} 条" else "仅保存有效的 ${parsed.entries.size} 条")
                        } }
                        items(parsed.entries, key = { it.id }) { VocabularyRow(it) }
                    }
                }
                "detail" -> book?.let { value ->
                    Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${value.entries.size} 条", Modifier.weight(1f), color = Muted)
                            TextButton(onClick = { metadata = true }) { Text("来源与版本") }
                        }
                        OutlinedTextField(query, { query = it }, label = { Text("搜索词语或释义") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val found = remember(value, query) { value.entries.filter {
                            it.expression.contains(query, true) || it.meaning.contains(query, true) || it.aiMeaning.contains(query, true)
                        } }
                        if (found.isEmpty()) Text("没有匹配的词语。", Modifier.padding(vertical = 20.dp), color = Muted)
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                            items(found, key = { it.id }) { entry ->
                                VocabularyRow(entry)
                                if (entry.meaning.isBlank() && entry.aiMeaning.isBlank()) TextButton(onClick = { definitionEntry = entry; vm.clearFeedback() },
                                    enabled = !locked && state.busy == null) { Text("补充释义") }
                            }
                        }
                        Button(onClick = { onChoose(value.selection()); onDismiss() }, enabled = !locked && state.busy == null,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text("使用此版本") }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃此次导入？") },
        text = { Text("尚未确认保存的内容会丢失。") }, confirmButton = { TextButton(onClick = { raw = ""; name = ""; source = ""; filename = ""; discard = false; mode = "list" }) { Text("放弃导入") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    if (metadata && book != null) AlertDialog(onDismissRequest = { metadata = false }, title = { Text("来源与版本") }, text = {
        LazyColumn { item { Text("${book.name}\n${book.source}\n\n${book.license}\n版本：${book.version}\nSHA-256：${book.digest}\n${book.fileName}", style = MaterialTheme.typography.bodySmall) }
            if (book.builtIn) item { Text("社区考试标签，非官方完整大纲；不含第三方词典释义。缺释义可按需请求 AI 补充。\n\n${book.attribution}", style = MaterialTheme.typography.bodySmall) }
            if (book.originalText.isNotBlank()) item { Text("原始内容\n${book.originalText.take(12000)}", style = MaterialTheme.typography.bodySmall) }
            if (book.originalText.length > 12000) item { Text("此处显示前 12,000 字符，完整原文已保存在本机。") }
        }
    }, confirmButton = { TextButton(onClick = { metadata = false }) { Text("关闭") } })
    definitionEntry?.let { entry -> AlertDialog(onDismissRequest = { definitionEntry = null }, title = { Text("补充「${entry.expression}」释义？") },
        text = { Text("将把这一条词语发送给 AI 服务，消耗一次模型请求。结果需确认后另存，原始内容保留。") },
        confirmButton = { TextButton(onClick = { if (book != null) vm.requestVocabularyMeaning(book, entry); definitionEntry = null }) { Text("请求补充") } },
        dismissButton = { TextButton(onClick = { definitionEntry = null }) { Text("取消") } }) }
    state.vocabularySuggestion?.takeIf { it.bookId == bookId && it.version == bookVersion }?.let { suggestion ->
        AlertDialog(onDismissRequest = { if (!locked) vm.dismissVocabularySuggestion() }, title = { Text("AI 补充预览") },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(suggestion.meaning) }; item { Text(suggestion.example) }
                item { Text("确认后保留原文，另存为新版本。所选词库版本由你另行切换。", color = Muted, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(onClick = { vm.confirmVocabularyMeaning { saved -> if (saved != null) inspect(saved) } }, enabled = !locked) { Text("确认采用") } },
            dismissButton = { TextButton(onClick = vm::dismissVocabularySuggestion, enabled = !locked) { Text("取消") } })
    }
}

private fun java.io.InputStream.readBytesLimited(): ByteArray {
    val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192)
    while (true) { val read = read(chunk); if (read < 0) break
        require(buffer.size() + read <= VocabularyImport.MAX_BYTES) { "文件超过 2 MiB，请拆分。" }; buffer.write(chunk, 0, read) }
    return buffer.toByteArray()
}

@Composable
private fun VocabularyRow(entry: VocabularyEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(entry.expression, style = MaterialTheme.typography.titleSmall)
        if (entry.meaning.isNotBlank()) Text(entry.meaning, style = MaterialTheme.typography.bodyMedium)
        if (entry.example.isNotBlank()) Text(entry.example, color = Muted, style = MaterialTheme.typography.bodySmall)
        if (entry.aiMeaning.isNotBlank()) { Text("AI 补充：${entry.aiMeaning}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            if (entry.aiExample.isNotBlank()) Text(entry.aiExample, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = Line)
    }
}
