package com.speakpen.app.data

/** Only sessions with messages are history; keep at most one active empty workspace. */
object ConversationSessions {
    fun start(data: SavedData, mode: ConversationMode, targetIds: List<String> = emptyList()): SavedData {
        val normalized = normalize(data)
        val current = normalized.sessions.firstOrNull { it.id == normalized.currentSessionId }
        val title = if (mode == ConversationMode.REVIEW) "目标复习" else "新的练习"
        val targets = targetIds.take(5)
        if (current != null && current.messages.isEmpty()) {
            val reused = current.copy(mode = mode, targetIds = targets, title = title)
            return normalized.copy(sessions = normalized.sessions.map { if (it.id == current.id) reused else it })
        }
        val session = ChatSession(mode = mode, targetIds = targets, title = title)
        return normalized.copy(sessions = listOf(session) + normalized.sessions, currentSessionId = session.id)
    }
    fun normalize(data: SavedData): SavedData {
        val current = data.sessions.firstOrNull { it.id == data.currentSessionId }
            ?: data.sessions.firstOrNull { it.messages.isNotEmpty() }
            ?: data.sessions.firstOrNull()
            ?: return data.copy(currentSessionId = "")
        return data.copy(
            sessions = data.sessions.filter { it.messages.isNotEmpty() || it.id == current.id },
            currentSessionId = current.id,
        )
    }
    fun history(sessions: List<ChatSession>): List<ChatSession> = sessions.filter { it.messages.isNotEmpty() }.sortedByDescending { it.updatedAt }
}
