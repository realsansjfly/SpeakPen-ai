package com.speakpen.app.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class VocabularyFormat { TXT, CSV }
data class VocabularyIssue(val line: Int, val reason: String)
data class VocabularyPreview(val entries: List<VocabularyEntry>, val duplicates: Int, val blankLines: Int,
    val issues: List<VocabularyIssue>, val fatal: String? = null) {
    val canSave: Boolean get() = fatal == null && entries.isNotEmpty()
}

object VocabularyImport {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_ENTRIES = 20_000
    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "文件超过 2 MiB，请拆分后导入。" }
        return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: Exception) { throw IllegalArgumentException("文件不是有效 UTF-8，请转换编码后重试。") }
    }
    private data class Row(val line: Int, val fields: List<String>, val raw: String)
    fun parse(raw: String, format: VocabularyFormat): VocabularyPreview {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return VocabularyPreview(emptyList(), 0, 0, emptyList(), "内容超过 2 MiB。")
        val text = raw.removePrefix("\uFEFF")
        val rows = try { if (format == VocabularyFormat.CSV) csv(text) else text.lineSequence().take(MAX_ENTRIES + 3).mapIndexed { i, s ->
            Row(i + 1, s.trimEnd('\r').split('\t'), s.trimEnd('\r')) }.toList() }
        catch (e: IllegalArgumentException) { return VocabularyPreview(emptyList(), 0, 0, emptyList(), e.message) }
        if (rows.size > MAX_ENTRIES + 2) return VocabularyPreview(emptyList(), 0, 0, emptyList(), "行数过多，请移除多余空行或拆分导入。")
        val entries = mutableListOf<VocabularyEntry>(); val issues = mutableListOf<VocabularyIssue>()
        val seen = mutableSetOf<List<String>>(); var duplicates = 0; var blanks = 0
        val header = if (format == VocabularyFormat.CSV) rows.firstOrNull()?.fields?.map(::vocabularyKey) else null
        val hasHeader = header?.any { it in setOf("expression", "word", "单词", "词语") } == true
        fun column(names: Set<String>, fallback: Int) = if (hasHeader) header!!.indexOfFirst { it in names } else fallback
        val expr = column(setOf("expression", "word", "单词", "词语"), 0)
        val meaning = column(setOf("meaning", "translation", "释义"), 1)
        val example = column(setOf("example", "例句"), 2)
        val tags = column(setOf("tags", "标签"), 3)
        if (hasHeader && header!!.distinct().size != header.size) return VocabularyPreview(emptyList(), 0, 0, emptyList(), "CSV 表头有重复字段。")
        for (row in rows.drop(if (hasHeader) 1 else 0)) {
            if (row.raw.isBlank()) { blanks++; continue }
            fun field(i: Int) = row.fields.getOrNull(i)?.trim().orEmpty()
            val expression = field(expr); val definition = field(meaning); val sentence = field(example)
            val error = when {
                row.fields.size > (if (hasHeader) header!!.size else 4) -> "字段过多；TXT 用制表符分隔，CSV 中的逗号请加双引号。"
                expression.isBlank() || !expression.any { it in 'a'..'z' || it in 'A'..'Z' } -> "缺少英文单词或短语。"
                expression.length > 120 -> "单词或短语超过 120 字符。"
                expression.startsWith('=') || expression.startsWith('+') || expression.startsWith('@') -> "不接受公式作为词语。"
                listOf(expression, definition, sentence, field(tags)).any { value -> value.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } } -> "含有不支持的控制字符。"
                definition.length > 1000 || sentence.length > 1000 || field(tags).length > 200 -> "释义、例句或标签过长。"
                else -> null
            }
            if (error != null) { issues += VocabularyIssue(row.line, error); continue }
            val key = listOf(vocabularyKey(expression), vocabularyKey(definition), vocabularyKey(sentence))
            if (!seen.add(key)) { duplicates++; continue }
            entries += VocabularyEntry(vocabularyHash(key.joinToString("\u0000")).take(24), expression, definition, sentence, field(tags), row.raw, row.line)
        }
        if (entries.size > MAX_ENTRIES) return VocabularyPreview(emptyList(), duplicates, blanks, issues, "超过 20,000 条，请拆分导入。")
        return VocabularyPreview(entries, duplicates, blanks, issues)
    }
    /** RFC 4180 quoting, embedded newlines, CRLF, and original physical line numbers. */
    private fun csv(text: String): List<Row> {
        val rows = mutableListOf<Row>(); val fields = mutableListOf<String>(); val field = StringBuilder()
        var quoted = false; var closed = false; var line = 1; var startLine = 1; var start = 0; var i = 0
        fun finish() { fields += field.toString(); field.clear(); closed = false }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' && quoted -> { quoted = false; closed = true }
                c == '"' && field.isEmpty() && !closed -> quoted = true
                c == '"' -> throw IllegalArgumentException("第 $line 行：双引号位置不正确。")
                !quoted && c == ',' -> finish()
                !quoted && (c == '\n' || c == '\r') -> {
                    finish(); rows += Row(startLine, fields.toList(), text.substring(start, i)); fields.clear()
                    require(rows.size <= MAX_ENTRIES + 2) { "行数过多，请移除多余空行或拆分导入。" }
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    line++; startLine = line; start = i + 1
                }
                closed -> throw IllegalArgumentException("第 $line 行：闭合引号后只能是逗号或换行。")
                else -> { field.append(c); if (c == '\n') line++ }
            }
            i++
        }
        require(!quoted) { "第 $startLine 行：双引号未闭合，请修正后重试。" }
        finish(); rows += Row(startLine, fields.toList(), text.substring(start))
        return rows
    }
}
