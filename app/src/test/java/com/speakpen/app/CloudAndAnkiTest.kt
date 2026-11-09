package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.ManagedService
import com.speakpen.app.network.VocabularyCandidates
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CloudAndAnkiTest {
    private val dir = File("src/main/assets/vocabulary").takeIf { it.exists() } ?: File("app/src/main/assets/vocabulary")
    private fun books(): List<VocabularyBook> {
        val manifest = JSONObject(File(dir, "anki-manifest.json").readText())
        return DataJson.objects(manifest.getJSONArray("books")).map {
            AnkiVocabulary.parse(File(dir, it.getString("file")).readText(), it, "test notice")
        }
    }

    @Test fun fixedCloudWinsOverOldTunnelAndPairingStillWorksInDevelopment() {
        val old = AppSettings(serviceUrl = "https://old-tunnel.example/v1", serviceAccessToken = "old-token")
        val cloud = ManagedService.resolve(old, "https://cloud.example/v1", "cloud-token", true)!!
        assertEquals("https://cloud.example/v1", cloud.baseUrl)
        assertEquals("cloud-token", cloud.apiKey)
        assertEquals(old.serviceUrl, ManagedService.resolve(old, "https://cloud.example/v1", "cloud-token", false)!!.baseUrl)
        assertNull(ManagedService.resolve(AppSettings(), "", "", false))
    }

    @Test fun assetsMatchHashesAndRetainDifferentKindsOfLearningContent() {
        val books = books()
        assertEquals(listOf(4027, 1652, 376), books.map { it.entries.size })
        val abandon = books[0].entries.first { it.expression == "abandon" }
        assertTrue(abandon.meaning.contains("放弃"))
        assertTrue(abandon.phonetic.contains("英"))
        assertTrue(abandon.example.contains("Those who abandon"))
        assertTrue(abandon.example.contains("自暴自弃"))
        assertTrue(abandon.details.any { it.title.contains("词典摘录") && it.text.contains("abandoned") })
        val abbreviation = books[1].entries.first { it.expression == "abbreviation" }
        assertTrue(abbreviation.details.any { it.title == "柯林斯解释" && it.text.contains("Kansas") })
        val resemble = books[2].entries.first { it.expression == "resemble" }
        assertTrue(resemble.details.any { it.title == "同义替换" && it.text.contains("similar to") })
        assertTrue(books.flatMap { it.entries }.none { it.meaning.contains("<div") || it.details.any { d -> d.text.contains("[sound:") } })
    }

    @Test fun richEntriesRoundTripAndOldEntryEnvelopeRemainsUnchanged() {
        val e = books()[0].entries.first()
        assertEquals(e, VocabularyJson.readEntry(VocabularyJson.entry(e)))
        val old = VocabularyEntry("id", "apple", "苹果", phonetic = "old in-memory field")
        assertFalse(VocabularyJson.entry(old).has("lexical"))
        val book = VocabularyBook(name = "test", source = "old", entries = listOf(old.copy(phonetic = "")))
        assertEquals(book.digest, VocabularyJson.readBook(VocabularyJson.book(book)).digest)
        val enriched = VocabularyBook(name = "new", source = "anki", entries = listOf(e))
        assertEquals(enriched.digest, VocabularyJson.readBook(VocabularyJson.book(enriched)).digest)
    }

    @Test fun duplicateWordsMergeLevelsAndAllSensesWithoutUploadingDictionaryExcerpts() {
        val a = VocabularyEntry("a", "journey", "旅程", levels = "cet4", source = "a.apkg",
            details = listOf(VocabularyDetail("例句", "A journey.")))
        val b = a.copy(id = "b", meaning = "旅行", levels = "ielts", source = "b.apkg",
            details = listOf(VocabularyDetail("同义替换", "trip")))
        val merged = DictionaryIndex(listOf(a, b)).find("journeys")!!
        assertTrue(merged.levels.contains("cet4") && merged.levels.contains("ielts"))
        assertTrue(merged.meaning.contains("旅程") && merged.meaning.contains("旅行"))
        assertEquals(2, merged.details.size)
        val candidate = VocabularyCandidates.select(VocabularyBook(name = "test", source = "local", entries = listOf(merged)), "travel journey").single()
        assertTrue(candidate.details.isEmpty())
        assertEquals("", candidate.source)
    }

    @Test fun corruptAssetsAreRejectedBeforeUse() {
        val manifest = JSONObject(File(dir, "anki-manifest.json").readText()).getJSONArray("books").getJSONObject(0)
        assertThrows(IllegalStateException::class.java) { AnkiVocabulary.parse("{}", manifest, "notice") }
    }
}
