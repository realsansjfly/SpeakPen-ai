package com.speakpen.app.data

import org.json.JSONObject

/** Build-time APKG extraction. Card HTML is never evaluated on the device. */
object AnkiVocabulary {
    fun parse(raw: String, spec: JSONObject, notice: String): VocabularyBook {
        check(vocabularyHash(raw) == spec.getString("sha256")) { "Anki 词表校验失败。" }
        val entries = DataJson.objects(JSONObject(raw).getJSONArray("entries")).map(VocabularyJson::readEntry)
        check(entries.size == spec.getInt("entries") && entries.isNotEmpty()) { "Anki 词表条目数不匹配。" }
        check(entries.map { it.id }.toSet().size == entries.size) { "Anki 词表标识重复。" }
        return VocabularyBook(id = spec.getString("id"), version = spec.getString("version"),
            name = spec.getString("name"), source = "用户提供的 ${spec.getString("fileName")}（${spec.getInt("notes")} 条笔记）",
            license = "用户提供；卡片含第三方词典内容，未核实再分发许可", fileName = spec.getString("fileName"),
            format = "APKG", entries = entries, builtIn = true, createdAt = 0, attribution = notice)
    }
}
