package com.speakpen.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Separate from learning.json: dictionary failures cannot erase conversations or recordings. */
class VocabularyStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "vocabularies.json"))
    private var writable = true
    var warning: String? = null
        private set
    fun load(): List<VocabularyBook> = try {
        val imported = if (!file.baseFile.exists()) emptyList() else DataJson.objects(JSONObject(
            file.openRead().bufferedReader().use { it.readText() }).getJSONArray("books")).map(VocabularyJson::readBook)
        builtIns() + imported.map { if (it.builtIn && it.id.startsWith("ecdict-")) it.copy(archived = true) else it }
    } catch (_: Exception) {
        writable = false; warning = "词库读取失败，已暂停词库写入并保留原文件；对话和积累不受影响。"
        emptyList()
    }
    fun save(books: List<VocabularyBook>) {
        check(writable) { "词库原文件异常，请先备份 vocabularies.json；未覆盖。" }
        val json = JSONObject().put("version", 1).put("books", JSONArray().apply {
            books.filterNot { it.builtIn && (it.version.startsWith("ecdict-") || it.version.startsWith("anki-")) }.forEach { put(VocabularyJson.book(it)) }
        }).toString()
        val stream = file.startWrite()
        try { stream.write(json.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    /**
     * 带中文释义的内置字典（`vocabulary/dictionary.tsv`）。校验失败就返回空表，
     * 让内置词本退回只有词形的老路径，绝不因为一个可选的附加层把词库读崩。
     */
    val dictionary: List<VocabularyEntry> by lazy { runCatching { dictionaryEntries() }.getOrElse { emptyList() } }

    private fun dictionaryEntries(): List<VocabularyEntry> {
        val manifest = manifest()
        val spec = manifest.optJSONObject("dictionary") ?: return emptyList()
        val raw = context.assets.open(Dictionary.ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        check(vocabularyHash(raw) == spec.getString("sha256")) { "内置字典校验失败。" }
        val entries = Dictionary.parse(raw)
        check(entries.size == spec.getInt("rows")) { "内置字典条目数不匹配。" }
        return entries
    }

    private fun manifest() = JSONObject(context.assets.open("vocabulary/manifest.json")
        .bufferedReader().use { it.readText() })

    private fun builtIns(): List<VocabularyBook> {
        val ankiManifest = JSONObject(context.assets.open("vocabulary/anki-manifest.json").bufferedReader().use { it.readText() })
        val ankiNotice = context.assets.open("vocabulary/ANKI-NOTICE.txt").bufferedReader().use { it.readText() }
        val anki = DataJson.objects(ankiManifest.getJSONArray("books")).map { spec ->
            val raw = context.assets.open("vocabulary/${spec.getString("file")}").bufferedReader(Charsets.UTF_8).use { it.readText() }
            AnkiVocabulary.parse(raw, spec, ankiNotice)
        }
        // Retain hidden legacy snapshots so existing conversations keep their exact vocabulary digest.
        return anki + legacyBooks()
    }

    private fun legacyBooks(): List<VocabularyBook> {
        val manifest = manifest()
        val commit = manifest.getString("commit")
        val notice = context.assets.open("vocabulary/ECDICT-LICENSE.txt").bufferedReader().use { it.readText() }
        val dictionary = dictionary
        return listOf("cet4" to "四级", "cet6" to "六级", "ielts" to "雅思").flatMap { (tag, name) ->
            val entries = if (dictionary.isEmpty()) legacyEntries(tag, manifest)
                else dictionary.filter { tag in it.levels.split(' ') }
            check(entries.isNotEmpty()) { "内置词表为空。" }
            val book = VocabularyBook(id = "ecdict-$tag", version = "ecdict-$commit", name = "${name}社区词表（旧版）",
                source = "https://github.com/skywind3000/ECDICT/tree/$commit（$tag 标签）",
                license = "MIT（ECDICT）；含词典中文释义，非官方完整大纲", entries = entries, builtIn = true,
                createdAt = 0, attribution = notice, archived = true)
            val wordsOnly = legacyEntries(tag, manifest)
            listOf(book, book.copy(entries = wordsOnly)).distinctBy { it.digest }
        }
    }

    /** 只有一个词形的旧资产：dictionary.tsv 缺失或校验失败时的兜底。 */
    private fun legacyEntries(tag: String, manifest: JSONObject): List<VocabularyEntry> {
        val raw = context.assets.open("vocabulary/$tag.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        check(vocabularyHash(raw) == manifest.getJSONObject(tag).getString("sha256")) { "内置词表校验失败。" }
        val entries = VocabularyImport.parse(raw, VocabularyFormat.TXT).entries
        check(entries.size == manifest.getJSONObject(tag).getInt("entries"))
        return entries
    }
}
