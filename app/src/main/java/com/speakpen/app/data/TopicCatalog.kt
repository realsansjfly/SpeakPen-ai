package com.speakpen.app.data

import org.json.JSONObject
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

data class ConversationTopic(val id: String, val text: String)
data class TopicBatch(val topics: List<ConversationTopic>, val phases: List<Float>, val revision: Long)

object TopicCatalog {
    fun parse(raw: String): List<ConversationTopic> {
        val values = JSONObject(raw).getJSONArray("topics")
        val topics = (0 until values.length()).map { i ->
            val entry = values.getJSONObject(i)
            ConversationTopic(entry.getString("id"), entry.getString("text").trim())
        }
        require(topics.size >= 36 && topics.all { it.id.isNotBlank() && it.text.length in 2..130 })
        require(topics.distinctBy { it.id }.size == topics.size && topics.distinctBy { it.text.lowercase() }.size == topics.size)
        return topics
    }
}

/** Process-local deck: no session-id seed, no network and no changes to the user's draft. */
class TopicSampler(private val topics: List<ConversationTopic>, private val random: Random = SecureRandom().asKotlinRandom()) {
    private val deck = ArrayDeque<ConversationTopic>()
    private var previous = emptySet<String>()
    private var revision = 0L
    init { require(topics.size >= 36 && topics.distinctBy { it.id }.size == topics.size) }

    @Synchronized fun next(size: Int = 18): TopicBatch {
        require(size in 1..topics.size / 2)
        val selected = mutableListOf<ConversationTopic>()
        while (selected.size < size) {
            var index = deck.indexOfFirst { it.id !in previous && selected.none { s -> s.id == it.id } }
            if (index < 0) {
                deck.clear()
                deck.addAll(topics.filter { it.id !in previous && selected.none { s -> s.id == it.id } }.shuffled(random))
                index = 0
            }
            selected += deck.removeAt(index)
        }
        previous = selected.map { it.id }.toSet()
        return TopicBatch(selected, List(3) { random.nextFloat() }, ++revision)
    }
}
