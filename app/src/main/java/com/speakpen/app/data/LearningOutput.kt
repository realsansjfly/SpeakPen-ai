package com.speakpen.app.data

object LearningOutput {
    /** Refuse an English-only phrase bank instead of saving it as a successful bilingual hint. */
    fun topic(text: String): String {
        require(text.isNotBlank()) { "话题语料为空，请重试。" }
        require(text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } &&
            text.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            "话题语料缺少中文解释或英文表达，请重试；已有语料已保留。"
        }
        return text.trim()
    }
}
