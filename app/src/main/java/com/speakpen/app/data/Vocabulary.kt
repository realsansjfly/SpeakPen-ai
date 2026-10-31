package com.speakpen.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class VocabularySuggestion(val bookId: String, val version: String, val entryId: String,
    val meaning: String, val example: String, val provenance: String)

data class VocabularyEntry(
    val id: String, val expression: String, val meaning: String = "", val example: String = "",
    val tags: String = "", val original: String = "", val line: Int = 0,
    val aiMeaning: String = "", val aiExample: String = "", val aiProvenance: String = "",
) {
    val effectiveMeaning: String get() = meaning.ifBlank { aiMeaning }
}

data class VocabularySelection(
    val id: String, val version: String, val name: String, val digest: String,
    val source: String, val license: String, val count: Int,
    val candidates: List<VocabularyEntry> = emptyList(), val selectorVersion: Int = 1,
)

data class VocabularyBook(
    val id: String = newId(), val version: String = "1", val name: String, val source: String,
    val license: String = "用户提供，许可未核查", val originalText: String = "", val fileName: String = "",
    val format: String = "TXT", val entries: List<VocabularyEntry>, val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val attribution: String = "",
) {
    val digest: String by lazy { vocabularyHash(JSONArray().apply { entries.forEach { put(VocabularyJson.entry(it)) } }.toString()) }
    fun selection() = VocabularySelection(id, version, name, digest, source, license, entries.size)
    fun supplement(entryId: String, meaning: String, example: String, provenance: String): VocabularyBook {
        require(meaning.isNotBlank() && meaning.length <= 500 && example.length <= 500)
        require(entries.any { it.id == entryId && it.meaning.isBlank() && it.aiMeaning.isBlank() }) { "该条目已有释义，原内容未覆盖。" }
        return copy(version = newId(), entries = entries.map { if (it.id == entryId) it.copy(
            aiMeaning = meaning, aiExample = example, aiProvenance = provenance) else it }, createdAt = System.currentTimeMillis())
    }
}

fun vocabularyHash(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    return buildString(bytes.size * 2) { bytes.forEach { byte ->
        val n = byte.toInt() and 255; append(digits[n ushr 4]); append(digits[n and 15])
    } }
}
fun vocabularyKey(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

object VocabularyJson {
    fun entry(e: VocabularyEntry) = JSONObject().apply {
        put("id", e.id); put("expression", e.expression); put("meaning", e.meaning); put("example", e.example)
        put("tags", e.tags); put("original", e.original); put("line", e.line)
        put("aiMeaning", e.aiMeaning); put("aiExample", e.aiExample); put("aiProvenance", e.aiProvenance)
    }
    fun readEntry(j: JSONObject) = VocabularyEntry(j.getString("id"), j.getString("expression"), j.optString("meaning"),
        j.optString("example"), j.optString("tags"), j.optString("original"), j.optInt("line"),
        j.optString("aiMeaning"), j.optString("aiExample"), j.optString("aiProvenance"))
    fun selection(s: VocabularySelection) = JSONObject().apply {
        put("id", s.id); put("version", s.version); put("name", s.name); put("digest", s.digest)
        put("source", s.source); put("license", s.license); put("count", s.count); put("selectorVersion", s.selectorVersion)
        put("candidates", JSONArray().apply { s.candidates.forEach { put(entry(it)) } })
    }
    fun readSelection(j: JSONObject) = VocabularySelection(j.getString("id"), j.getString("version"), j.getString("name"),
        j.getString("digest"), j.optString("source"), j.optString("license"), j.optInt("count"),
        DataJson.objects(j.optJSONArray("candidates")).map(::readEntry), j.optInt("selectorVersion", 1))
    fun book(b: VocabularyBook) = JSONObject().apply {
        put("selection", selection(b.selection())); put("originalText", b.originalText); put("fileName", b.fileName)
        put("format", b.format); put("builtIn", b.builtIn); put("createdAt", b.createdAt); put("attribution", b.attribution)
        put("entries", JSONArray().apply { b.entries.forEach { put(entry(it)) } })
    }
    fun readBook(j: JSONObject): VocabularyBook {
        val s = readSelection(j.getJSONObject("selection"))
        return VocabularyBook(s.id, s.version, s.name, s.source, s.license, j.optString("originalText"), j.optString("fileName"),
            j.optString("format", "TXT"), DataJson.objects(j.getJSONArray("entries")).map(::readEntry),
            j.optBoolean("builtIn"), j.optLong("createdAt"), j.optString("attribution")).also {
            check(it.digest == s.digest) { "词库校验失败，已保留原文件。" }
        }
    }
}
