package com.speakpen.app.data

object LearningMessages {
    fun append(session: ChatSession, hint: ChatMessage): ChatSession {
        require(hint.isHint) { "只能添加学习提示。" }
        if (hint.hintKind == HintKind.TOPIC) LearningOutput.topic(hint.text)
        val source = session.messages.indexOfFirst { it.id == hint.sourceMessageId && it.role == "user" }
        val index = if (hint.hintKind in setOf(HintKind.OPTIMIZE, HintKind.CORRECTION) && source >= 0) {
            source + 1 + session.messages.drop(source + 1).takeWhile {
                it.isHint && it.hintKind in setOf(HintKind.OPTIMIZE, HintKind.CORRECTION) && it.sourceMessageId == hint.sourceMessageId
            }.size
        } else session.messages.size
        val messages = session.messages.toMutableList().apply { add(index, hint) }
        return session.copy(messages = messages, updatedAt = System.currentTimeMillis())
    }

    /** Repositions old optimization hints for display without rewriting or dropping old data. */
    fun display(messages: List<ChatMessage>): List<ChatMessage> {
        val userIds = messages.filter { it.role == "user" }.map { it.id }.toSet()
        val attached = messages.filter { it.isHint && it.hintKind in setOf(HintKind.OPTIMIZE, HintKind.CORRECTION) && it.sourceMessageId in userIds }
            .groupBy { it.sourceMessageId }
        val attachedIds = attached.values.flatten().map { it.id }.toSet()
        return messages.filterNot { it.id in attachedIds }.flatMap { message ->
            listOf(message) + if (message.role == "user") attached[message.id].orEmpty() else emptyList()
        }
    }
    fun replace(session: ChatSession, id: String, text: String): ChatSession {
        require(text.isNotBlank()) { "生成结果为空，原消息已保留。" }
        require(session.messages.any { it.id == id && it.role != "user" }) { "不能重新生成用户原话。" }
        if (session.messages.first { it.id == id }.hintKind == HintKind.TOPIC) LearningOutput.topic(text)
        return session.copy(messages = session.messages.map {
            if (it.id == id) it.copy(text = text.trim(), revision = it.revision + 1) else it
        }, updatedAt = System.currentTimeMillis())
    }
}
