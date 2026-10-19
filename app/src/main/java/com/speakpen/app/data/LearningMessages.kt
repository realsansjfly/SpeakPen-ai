package com.speakpen.app.data

object LearningMessages {
    fun replace(session: ChatSession, id: String, text: String): ChatSession {
        require(text.isNotBlank()) { "生成结果为空，原消息已保留。" }
        require(session.messages.any { it.id == id && it.role != "user" }) { "不能重新生成用户原话。" }
        return session.copy(messages = session.messages.map {
            if (it.id == id) it.copy(text = text.trim(), revision = it.revision + 1) else it
        }, updatedAt = System.currentTimeMillis())
    }
}
