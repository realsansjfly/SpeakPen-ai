package com.speakpen.app

import com.speakpen.app.data.Dictionary
import com.speakpen.app.data.DictionaryIndex
import com.speakpen.app.data.VocabularyEntry
import com.speakpen.app.data.vocabularyHash
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 内置字典（ECDICT 中文侧）的解析、检索、近似词与资产一致性。 */
class DictionaryTest {
    private val header = "word\tphonetic\tpos\tcollins\ttranslation\texchange\ttags"

    private fun row(vararg cells: String) = cells.joinToString("\t")

    private fun entry(meaning: String, exchange: String = "", levels: String = "cet4", collins: Int = 0) =
        VocabularyEntry(id = "e", expression = "x", meaning = meaning,
            phonetic = "", pos = "", levels = levels, collins = collins, exchange = exchange)

    @Test fun queryRejectsBlankChineseAndOverlongInput() {
        assertEquals("look up", Dictionary.query("  look   up  "))
        assertEquals("don't", Dictionary.query("don't"))
        assertNull(Dictionary.query(null))
        assertNull(Dictionary.query("   "))
        assertNull(Dictionary.query("苹果"))
        assertNull(Dictionary.query("a".repeat(Dictionary.MAX_QUERY + 1)))
    }

    @Test fun parseRequiresHeaderAndSevenColumns() {
        assertThrows(IllegalArgumentException::class.java) { Dictionary.parse(header) }
        assertThrows(IllegalArgumentException::class.java) { Dictionary.parse("word\tphonetic") }
        assertThrows(IllegalArgumentException::class.java) { Dictionary.parse("$header\n${row("apple", "ˈæpl", "n.", "3", "苹果", "")}") }
        assertThrows(IllegalArgumentException::class.java) { Dictionary.parse("$header\n${row("", "ˈæpl", "n.", "3", "苹果", "", "cet4")}") }
    }

    @Test fun parseMapsEveryColumnAndClampsCollins() {
        val parsed = Dictionary.parse(
            "$header\n${row("apple", "ˈæpl", "n.", "3", "n. 苹果", "s:apples", "cet4 cet6")}\n" +
                "${row("banana", "", "", "9", "n. 香蕉", "", "ielts")}"
        )
        assertEquals(2, parsed.size)
        val apple = parsed.first()
        assertEquals("apple", apple.expression)
        assertEquals("ˈæpl", apple.phonetic)
        assertEquals("n.", apple.pos)
        assertEquals(3, apple.collins)
        assertEquals("n. 苹果", apple.meaning)
        assertEquals("s:apples", apple.exchange)
        assertEquals("cet4 cet6", apple.levels)
        assertEquals(5, parsed.last().collins)
        assertTrue(parsed.map { it.id }.toSet().size == 2)
    }

    @Test fun shortGlossKeepsPosAndTwoOrThreeSenses() {
        assertEquals("n. 苹果、苹果树、苹果似的东西",
            Dictionary.shortGloss(entry("n. 苹果, 苹果树, 苹果似的东西, 傻瓜")))
        assertEquals("vt. 放弃", Dictionary.shortGloss(entry("vt. 放弃\\n遗弃、抛弃")))
        val long = Dictionary.shortGloss(entry("n. " + "很长的释义内容".repeat(6)), senses = 2)
        assertEquals(34, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun fullGlossTurnsSenseSeparatorIntoNewlines() {
        assertEquals("n. 放任\nvt. 放弃", Dictionary.fullGloss(entry("n. 放任\\nvt. 放弃")))
    }

    @Test fun levelsAndStarsAndExchangesAreReadable() {
        assertEquals("四级 · 六级 · 雅思", Dictionary.levelsLabel(entry("x", levels = "cet4 cet6 ielts")))
        assertEquals("", Dictionary.levelsLabel(entry("x", levels = "")))
        assertEquals("★★★☆☆", Dictionary.collinsStars(entry("x", collins = 3)))
        assertEquals("", Dictionary.collinsStars(entry("x", collins = 0)))
        assertEquals(listOf("过去分词 gone", "过去式 went", "现在分词 going", "第三人称单数 goes"),
            Dictionary.exchangeLines(entry("x", exchange = "d:gone/p:went/i:going/3:goes")))
        assertEquals(emptyList<String>(), Dictionary.exchangeLines(entry("x", exchange = "zz:bad")))
    }

    @Test fun indexFindsInflectedFormsAndForgetsUnknownPhrases() {
        val index = DictionaryIndex(listOf(
            VocabularyEntry(id = "1", expression = "go"), VocabularyEntry(id = "2", expression = "study"),
            VocabularyEntry(id = "3", expression = "apple")))
        assertEquals("study", index.find("studies")?.expression)
        assertEquals("study", index.find("studying")?.expression)
        assertEquals("go", index.find("goes")?.expression)
        assertEquals("apple", index.find("Apple")?.expression)
        assertNull(index.find("check in"))
        assertEquals(3, index.size)
    }

    @Test fun suggestOffersNearMissForTypos() {
        val index = DictionaryIndex(listOf(
            VocabularyEntry(id = "1", expression = "opponent"), VocabularyEntry(id = "2", expression = "opening")))
        assertEquals("opponent", index.suggest("apponent")?.expression)
        assertNull(index.suggest("op"))
    }

    @Test fun builtInDictionaryMatchesManifestAndIsSorted() {
        val dir = File("src/main/assets/vocabulary").takeIf { it.exists() }
            ?: File("app/src/main/assets/vocabulary")
        val manifest = JSONObject(File(dir, "manifest.json").readText()).getJSONObject("dictionary")
        val raw = File(dir, Dictionary.ASSET.substringAfterLast('/')).readText()
        // 资产按 LF 生成；工作副本若被换成 CRLF，先归一化再比对（与 VocabularyTest 同一条规则）。
        assertEquals(manifest.getString("sha256"), vocabularyHash(raw.replace("\r\n", "\n")))
        val parsed = Dictionary.parse(raw)
        assertEquals(manifest.getInt("rows"), parsed.size)
        assertEquals(parsed.map { it.expression.lowercase() }, parsed.map { it.expression.lowercase() }.sorted())
        for ((tag, count) in listOf("cet4" to 3849, "cet6" to 5407, "ielts" to 5040)) {
            assertEquals("$tag 词数", count, parsed.count { tag in it.levels.split(' ') })
        }
        val abandon = parsed.first { it.expression == "abandon" }
        assertNotNull(abandon.meaning.takeIf { Regex("[\\u4e00-\\u9fa5]").containsMatchIn(it) })
        assertTrue(abandon.phonetic.isNotBlank())
        assertTrue(abandon.exchange.contains("p:"))
    }
}
