package com.speakpen.app.data

/**
 * 内置字典：词形 + 中文释义 + 音标 + 词性 + 柯林斯星级 + 词形变化。
 *
 * 资产是 `app/src/main/assets/vocabulary/dictionary.tsv`（7 列、UTF-8、按小写词序），由
 * `scripts/build-dictionary.py` 从 manifest 里钉住的 ECDICT commit 生成。三个 `*.txt` 词表保持
 * 不变，仍然只提供词形与考试标签；这里补的是当年被裁掉的中文一侧。
 *
 * 这些字段只活在内存里：`VocabularyJson.entry` 一个字节都没动，否则已持久化词库的 digest
 * 会全部失配（见 `VocabularyJson.readBook` 的校验）。
 */
object Dictionary {
    const val ASSET = "vocabulary/dictionary.tsv"
    const val MAX_QUERY = 48
    private const val COLUMNS = 7
    private const val SENSE_SEPARATOR = "\\n" // ECDICT 用一个反斜杠 + n 分隔多个义项
    private val HEADER = listOf("word", "phonetic", "pos", "collins", "translation", "exchange", "tags")
    private val SENSE_PREFIX = Regex("^([a-z]{1,5}\\.)\\s*", RegexOption.IGNORE_CASE)
    private val LEVEL_NAMES = mapOf("cet4" to "四级", "cet6" to "六级", "ielts" to "雅思")

    /** 用户输入 → 可查询的词；空、过长或一个英文字母都没有时返回 null。 */
    fun query(raw: String?): String? {
        val value = raw.orEmpty().trim().replace(Regex("\\s+"), " ")
        if (value.isEmpty() || value.length > MAX_QUERY) return null
        if (value.none { it in 'a'..'z' || it in 'A'..'Z' }) return null
        return value
    }

    /** 解析内置字典资产；格式不对就抛异常，由调用方决定回退策略。 */
    fun parse(raw: String): List<VocabularyEntry> {
        val lines = raw.split('\n')
        require(lines.size > 1) { "内置字典为空。" }
        require(lines.first().trim().split('\t') == HEADER) { "内置字典表头不匹配。" }
        val entries = ArrayList<VocabularyEntry>(lines.size - 1)
        lines.drop(1).forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) return@forEachIndexed
            val cells = line.split('\t')
            require(cells.size == COLUMNS) { "内置字典第 ${index + 2} 行列数异常。" }
            val word = cells[0].trim()
            require(word.isNotEmpty()) { "内置字典第 ${index + 2} 行缺少单词。" }
            entries += VocabularyEntry(
                id = dictionaryId(word), expression = word, meaning = cells[4].trim(),
                phonetic = cells[1].trim(), pos = cells[2].trim(), levels = cells[6].trim(),
                collins = cells[3].trim().toIntOrNull()?.coerceIn(0, 5) ?: 0, exchange = cells[5].trim(),
            )
        }
        require(entries.isNotEmpty()) { "内置字典没有有效条目。" }
        return entries
    }

    private fun dictionaryId(word: String): String = "w-" + vocabularyHash(vocabularyKey(word)).take(20)

    /** 列表里的一行：词性 + 两三个汉语义项。 */
    fun shortGloss(entry: VocabularyEntry, senses: Int = 3): String {
        val line = entry.meaning.split(SENSE_SEPARATOR).firstOrNull().orEmpty().trim()
        if (line.isEmpty()) return entry.effectiveMeaning.take(24)
        val prefix = SENSE_PREFIX.find(line)?.value?.trim().orEmpty()
        val body = line.removePrefix(prefix).trim().ifEmpty { line }
        val picked = body.split(',', '，', '、', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
            .take(senses.coerceIn(1, 5))
        if (picked.isEmpty()) return line.take(32)
        val text = listOf(prefix, picked.joinToString("、")).filter { it.isNotEmpty() }.joinToString(" ")
        return if (text.length > 34) text.take(33) + "…" else text
    }

    /** 弹窗里的完整释义，把义项分隔符还原成换行。 */
    fun fullGloss(entry: VocabularyEntry): String =
        entry.effectiveMeaning.split(SENSE_SEPARATOR).joinToString("\n") { it.trim() }.trim()

    fun levelsLabel(entry: VocabularyEntry): String =
        entry.levels.split(' ').mapNotNull { LEVEL_NAMES[it] }.joinToString(" · ")

    fun collinsStars(entry: VocabularyEntry): String =
        if (entry.collins <= 0) "" else "★".repeat(entry.collins) + "☆".repeat(5 - entry.collins)

    private val EXCHANGE_LABELS = mapOf(
        "p" to "过去式", "d" to "过去分词", "i" to "现在分词", "3" to "第三人称单数",
        "s" to "复数", "r" to "比较级", "t" to "最高级", "0" to "原形", "1" to "原形变化",
    )

    /** `d:gone/p:went` → `过去分词 gone · 过去式 went`。 */
    fun exchangeLines(entry: VocabularyEntry): List<String> = entry.exchange.split('/').mapNotNull { pair ->
        val code = pair.substringBefore(':', "").trim()
        val value = pair.substringAfter(':', "").trim()
        val label = EXCHANGE_LABELS[code]
        if (label == null || value.isEmpty()) null else "$label $value"
    }
}

/** 词条索引：内置字典 + 用户导入词库合并成一个可查表。 */
class DictionaryIndex(entries: List<VocabularyEntry>) {
    private val byKey: Map<String, VocabularyEntry> = entries.associateBy { vocabularyKey(it.expression) }
    val size: Int get() = byKey.size

    fun find(raw: String): VocabularyEntry? {
        val key = vocabularyKey(raw)
        if (key.isEmpty()) return null
        byKey[key]?.let { return it }
        for (variant in variants(key)) byKey[variant]?.let { return it }
        // 多词短语没有词形变化，查不到就直接返回 null，交给 AI 兜底。
        return null
    }

    /** 拼错时的近似词：编辑距离 ≤ 2，优先同首字母、同长度。 */
    fun suggest(raw: String): VocabularyEntry? {
        val key = vocabularyKey(raw)
        if (key.length < 3) return null
        var best: VocabularyEntry? = null
        var bestDistance = 3
        for ((candidate, entry) in byKey) {
            if (candidate.isEmpty() || kotlin.math.abs(candidate.length - key.length) > 2) continue
            val distance = editDistance(key, candidate, bestDistance - 1)
            if (distance < bestDistance) { best = entry; bestDistance = distance }
            if (bestDistance <= 1) break
        }
        return best
    }

    private fun variants(key: String): List<String> = buildList {
        if (key.endsWith("ies") && key.length > 4) add(key.dropLast(3) + "y")
        if (key.endsWith("ves") && key.length > 4) { add(key.dropLast(3) + "f"); add(key.dropLast(3) + "fe") }
        if (key.endsWith("es") && key.length > 3) add(key.dropLast(2))
        if (key.endsWith("s") && !key.endsWith("ss") && key.length > 2) add(key.dropLast(1))
        if (key.endsWith("ing") && key.length > 5) { add(key.dropLast(3)); add(key.dropLast(3) + "e") }
        if (key.endsWith("ed") && key.length > 4) { add(key.dropLast(2)); add(key.dropLast(1)); add(key.dropLast(2) + "e") }
    }

    private fun editDistance(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > limit) return limit + 1
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }
}
