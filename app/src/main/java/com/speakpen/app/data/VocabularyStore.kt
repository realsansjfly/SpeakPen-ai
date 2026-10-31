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
        builtIns() + imported
    } catch (_: Exception) {
        writable = false; warning = "词库读取失败，已暂停词库写入并保留原文件；对话和积累不受影响。"
        emptyList()
    }
    fun save(books: List<VocabularyBook>) {
        check(writable) { "词库原文件异常，请先备份 vocabularies.json；未覆盖。" }
        val json = JSONObject().put("version", 1).put("books", JSONArray().apply {
            books.filterNot { it.builtIn && it.version.startsWith("ecdict-") }.forEach { put(VocabularyJson.book(it)) }
        }).toString()
        val stream = file.startWrite()
        try { stream.write(json.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun builtIns(): List<VocabularyBook> {
        val manifest = JSONObject(context.assets.open("vocabulary/manifest.json").bufferedReader().use { it.readText() })
        val commit = manifest.getString("commit")
        val notice = context.assets.open("vocabulary/ECDICT-LICENSE.txt").bufferedReader().use { it.readText() }
        return listOf("cet4" to "四级", "cet6" to "六级", "ielts" to "雅思").map { (tag, name) ->
            val raw = context.assets.open("vocabulary/$tag.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(vocabularyHash(raw) == manifest.getJSONObject(tag).getString("sha256")) { "内置词表校验失败。" }
            val entries = VocabularyImport.parse(raw, VocabularyFormat.TXT).entries
            check(entries.size == manifest.getJSONObject(tag).getInt("entries"))
            VocabularyBook(id = "ecdict-$tag", version = "ecdict-$commit", name = "$name · 社区词表",
                source = "https://github.com/skywind3000/ECDICT/tree/$commit · $tag 标签",
                license = "MIT · 仅词形与考试标签；非官方完整大纲", entries = entries, builtIn = true,
                createdAt = 0, attribution = notice)
        }
    }
}
