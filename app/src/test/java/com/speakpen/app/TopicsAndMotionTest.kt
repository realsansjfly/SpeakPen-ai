package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.ui.AmbientNoise
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

class TopicsAndMotionTest {
    private fun catalog() = TopicCatalog.parse(File("src/main/assets/topics/catalog.json").readText())

    @Test fun packagedCorpusHasReviewedUniqueContentAndPreservedSources() {
        val file = File("src/main/assets/topics/catalog.json").readText()
        val topics = TopicCatalog.parse(file)
        assertEquals(440, topics.size)
        assertEquals(200, topics.count { it.text.any { c -> c in '\u4e00'..'\u9fff' } })
        val values = JSONObject(file).getJSONArray("topics")
        assertEquals(240, (0 until values.length()).count { values.getJSONObject(it).optInt("line") > 0 })
        assertTrue(File("src/main/assets/topics/CC0-LICENSE.txt").readText().contains("CC0"))
        assertTrue(File("src/main/assets/licenses/webgl-noise-MIT.txt").readText().contains("Ashima Arts"))
    }
    @Test fun repeatedBatchesHaveFreshContentAndIndependentStartPositions() {
        val sampler = TopicSampler(catalog(), Random(42))
        var previous = emptySet<String>()
        repeat(1000) { i ->
            val batch = sampler.next()
            val ids = batch.topics.map { it.id }.toSet()
            assertEquals(18, ids.size); assertTrue(ids.intersect(previous).isEmpty())
            assertEquals((i + 1).toLong(), batch.revision)
            assertEquals(3, batch.phases.size)
            assertTrue(batch.phases.all { it >= 0f && it < 1f })
            assertTrue(batch.phases.distinct().size > 1)
            previous = ids
        }
    }
    @Test fun wholeDeckIsReachableAndNotOnlyAnInitialSlice() {
        val topics = catalog(); val sampler = TopicSampler(topics, Random(17))
        val seen = mutableSetOf<String>()
        repeat(100) { seen.addAll(sampler.next().topics.map { it.id }) }
        assertEquals(topics.map { it.id }.toSet(), seen)
    }
    @Test fun minimalPoolRefillTerminatesWithoutDuplicateOrPreviousBatch() {
        val sampler = TopicSampler(catalog().take(36), Random(0))
        var previous = emptySet<String>()
        repeat(100) {
            val next = sampler.next().topics.map { it.id }.toSet()
            assertEquals(18, next.size); assertTrue(next.intersect(previous).isEmpty()); previous = next
        }
    }
    @Test fun differentInjectedEntropyDoesNotReuseAnOrderOrOffset() {
        val a = TopicSampler(catalog(), Random(1)).next()
        val b = TopicSampler(catalog(), Random(2)).next()
        assertNotEquals(a.topics, b.topics); assertNotEquals(a.phases, b.phases)
    }
    @Test fun invalidOrDuplicateResourcesAreRejected() {
        assertTrue(runCatching { TopicCatalog.parse("""{"topics":[]}""") }.isFailure)
        assertTrue(runCatching { TopicSampler(catalog().take(35)) }.isFailure)
        assertTrue(runCatching { TopicSampler(catalog(), Random(1)).next(300) }.isFailure)
    }
    @Test fun noiseIsContinuousBoundedAndNonStaticAcrossTime() {
        var change = 0.0
        repeat(1000) { i ->
            val x = i * .037; val y = i * .021
            val a = AmbientNoise.sample(x, y)
            assertTrue(a.isFinite() && a in -1f..1f)
            assertTrue(abs(a - AmbientNoise.sample(x + .0001, y)) < .005f)
            change += abs(a - AmbientNoise.sample(x + .48, y))
        }
        assertTrue(change > 100)
        assertEquals(0f, AmbientNoise.sample(0.0, 0.0), .00001f)
    }
}
