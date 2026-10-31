package com.speakpen.app.data

data class ScenarioGoal(val id: String = newId(), val text: String)

/** Editable templates and immutable per-practice snapshots have separate lifetimes. */
data class ScenarioTemplate(
    val id: String = newId(), val revision: Int = 1,
    val name: String = "", val aiRole: String = "", val userRole: String = "",
    val goals: List<ScenarioGoal> = emptyList(), val opening: String = "",
    val endCondition: String = "", val maxTurns: Int? = null,
) {
    fun validationError(): String? = when {
        name.isBlank() || name.length > 160 -> "请填写场景名称／情境（最多 160 字）。"
        aiRole.isBlank() || userRole.isBlank() || aiRole.length > 160 || userRole.length > 160 -> "请填写双方角色（各最多 160 字）。"
        goals.size !in 1..8 || goals.any { it.text.isBlank() || it.text.length > 240 } -> "请填写 1–8 项目标，每项最多 240 字。"
        goals.map { it.id }.distinct().size != goals.size -> "目标 ID 重复，请重新编辑。"
        opening.length > 1600 -> "开场白最多 1600 字。"
        endCondition.isBlank() || endCondition.length > 1000 -> "请填写自然语言结束条件（最多 1000 字）。"
        maxTurns != null && maxTurns !in 1..100 -> "轮数上限须为 1–100 的整数，或留空。"
        else -> null
    }
}

enum class ScenarioPhase { ACTIVE, ENDED, CONTINUED }
enum class ScenarioCheckStatus { NONE, PENDING, VERIFIED, FAILED }
enum class ScenarioEndReason { GOALS_REACHED, CONDITION_MET, TURN_LIMIT }
data class ScenarioEvidence(val messageId: String, val quote: String)
data class ScenarioGoalProgress(val goalId: String, val achieved: Boolean, val reason: String, val evidence: List<ScenarioEvidence> = emptyList())
data class ScenarioCorrection(val goalId: String, val achieved: Boolean, val note: String, val at: Long = System.currentTimeMillis())
data class ScenarioTurn(val userMessageId: String, val replyMessageId: String)
data class ScenarioAssessment(
    val goals: List<ScenarioGoalProgress>, val conditionMet: Boolean,
    val conditionReason: String, val conditionEvidence: List<ScenarioEvidence>,
)
data class ScenarioEnding(
    val reason: ScenarioEndReason, val succeeded: Boolean, val at: Long,
    val boundaryMessageId: String, val messages: List<ChatMessage>,
    val assessment: ScenarioAssessment?, val corrections: List<ScenarioCorrection>, val completedTurns: Int,
)
data class ScenarioPractice(
    val template: ScenarioTemplate, val practiceId: String = newId(),
    val phase: ScenarioPhase = ScenarioPhase.ACTIVE, val revision: Int = 0,
    val turns: List<ScenarioTurn> = emptyList(), val assessment: ScenarioAssessment? = null,
    val corrections: List<ScenarioCorrection> = emptyList(),
    val checkStatus: ScenarioCheckStatus = ScenarioCheckStatus.NONE, val checkError: String = "",
    val goalsCollapsed: Boolean = true, val ending: ScenarioEnding? = null,
    val completionPresented: Boolean = false,
) {
    val needsCheck: Boolean get() = phase == ScenarioPhase.ACTIVE && checkStatus in setOf(ScenarioCheckStatus.PENDING, ScenarioCheckStatus.FAILED)
    fun achieved(goalId: String): Boolean = corrections.lastOrNull { it.goalId == goalId }?.achieved
        ?: assessment?.goals?.firstOrNull { it.goalId == goalId }?.achieved ?: false
}
data class ScenarioSuccess(val practiceId: String, val templateId: String, val sessionId: String, val at: Long)

/** Pure transitions: only a successful user/reply pair counts; settlement is never a UI side effect. */
object Scenarios {
    fun rewind(session: ChatSession, index: Int): ChatSession {
        val practice = session.scenario ?: return session.copy(messages = session.messages.take(index), updatedAt = System.currentTimeMillis())
        val kept = session.messages.take(index)
        val turns = practice.turns.filter { turn -> kept.any { it.id == turn.userMessageId } && kept.any { it.id == turn.replyMessageId } }
        return session.copy(messages = kept, scenario = practice.copy(turns = turns, assessment = null, checkStatus = ScenarioCheckStatus.NONE,
            ending = null, phase = ScenarioPhase.ACTIVE, revision = practice.revision + 1), updatedAt = System.currentTimeMillis())
    }
    fun invalidateAssessment(session: ChatSession): ChatSession {
        val practice = session.scenario ?: return session
        return if (practice.phase == ScenarioPhase.ACTIVE) session.copy(scenario = practice.copy(assessment = null, checkStatus = if (practice.turns.isEmpty()) ScenarioCheckStatus.NONE else ScenarioCheckStatus.PENDING, revision = practice.revision + 1)) else session
    }
    fun start(template: ScenarioTemplate, config: ConversationConfig): ChatSession {
        require(template.validationError() == null) { template.validationError().orEmpty() }
        val opening = template.opening.takeIf { it.isNotBlank() }?.let {
            ChatMessage(role = "assistant", text = it, generationConfig = config, scenarioOpening = true)
        }
        return ChatSession(title = template.name, mode = ConversationMode.SCENARIO, config = config,
            scenario = ScenarioPractice(template = template.copy(goals = template.goals.toList())), messages = listOfNotNull(opening))
    }

    fun recordTurn(session: ChatSession, userId: String, replyId: String): ChatSession {
        val practice = session.scenario ?: return session
        if (practice.phase != ScenarioPhase.ACTIVE || practice.turns.any { it.userMessageId == userId || it.replyMessageId == replyId }) return session
        val userIndex = session.messages.indexOfFirst { it.id == userId && it.role == "user" && it.text.isNotBlank() }
        val replyIndex = session.messages.indexOfFirst { it.id == replyId && it.role == "assistant" && !it.scenarioOpening && it.text.isNotBlank() }
        if (userIndex < 0 || replyIndex <= userIndex || session.messages.subList(userIndex + 1, replyIndex).any { it.isConversation }) return session
        return session.copy(scenario = practice.copy(turns = practice.turns + ScenarioTurn(userId, replyId),
            revision = practice.revision + 1, checkStatus = ScenarioCheckStatus.PENDING, checkError = ""))
    }

    fun assess(session: ChatSession, assessment: ScenarioAssessment, now: Long = System.currentTimeMillis()): ChatSession {
        val practice = session.scenario ?: return session
        if (practice.phase != ScenarioPhase.ACTIVE || !practice.needsCheck || practice.turns.isEmpty()) return session
        val checked = session.copy(scenario = practice.copy(assessment = assessment, checkStatus = ScenarioCheckStatus.VERIFIED,
            checkError = "", revision = practice.revision + 1))
        val successful = assessment.conditionMet && practice.template.goals.all { checked.scenario!!.achieved(it.id) }
        return when {
            successful -> finish(checked, ScenarioEndReason.GOALS_REACHED, true, now)
            assessment.conditionMet -> finish(checked, ScenarioEndReason.CONDITION_MET, false, now)
            atLimit(practice) -> finish(checked, ScenarioEndReason.TURN_LIMIT, false, now)
            else -> checked
        }
    }

    fun checkFailed(session: ChatSession, now: Long = System.currentTimeMillis()): ChatSession {
        val practice = session.scenario ?: return session
        if (!practice.needsCheck) return session
        val failed = session.copy(scenario = practice.copy(checkStatus = ScenarioCheckStatus.FAILED,
            checkError = "目标判定未完成，未记录成功。", revision = practice.revision + 1))
        return if (atLimit(practice)) finish(failed, ScenarioEndReason.TURN_LIMIT, false, now) else failed
    }

    fun correct(session: ChatSession, goalId: String, achieved: Boolean, note: String): ChatSession {
        val practice = session.scenario ?: return session
        if (practice.phase != ScenarioPhase.ACTIVE || practice.template.goals.none { it.id == goalId } || note.isBlank() || note.length > 500) return session
        return session.copy(scenario = practice.copy(corrections = practice.corrections + ScenarioCorrection(goalId, achieved, note.trim()),
            revision = practice.revision + 1, checkStatus = if (practice.turns.isEmpty()) ScenarioCheckStatus.NONE else ScenarioCheckStatus.PENDING))
    }

    fun continueFreely(session: ChatSession): ChatSession {
        val practice = session.scenario ?: return session
        if (practice.phase != ScenarioPhase.ENDED || practice.ending == null) return session
        return session.copy(mode = ConversationMode.FREE, targetIds = emptyList(),
            scenario = practice.copy(phase = ScenarioPhase.CONTINUED, revision = practice.revision + 1))
    }

    /** Freeze exact text/revisions as well as the boundary ID, so future recap never includes continuation. */
    private fun finish(session: ChatSession, reason: ScenarioEndReason, succeeded: Boolean, now: Long): ChatSession {
        val practice = requireNotNull(session.scenario)
        if (practice.ending != null || practice.phase != ScenarioPhase.ACTIVE) return session
        val boundary = practice.turns.last().replyMessageId
        return session.copy(scenario = practice.copy(phase = ScenarioPhase.ENDED, revision = practice.revision + 1,
            ending = ScenarioEnding(reason, succeeded, now, boundary, session.messages.toList(), practice.assessment, practice.corrections.toList(), practice.turns.size)))
    }

    private fun atLimit(practice: ScenarioPractice): Boolean = practice.template.maxTurns?.let { practice.turns.size >= it } == true
    fun frozen(session: ChatSession, messageId: String): Boolean = session.scenario?.ending?.messages?.any { it.id == messageId } == true
    fun settle(data: SavedData, session: ChatSession): SavedData {
        val practice = session.scenario
        val ending = practice?.ending
        val success = if (ending?.succeeded == true && data.scenarioSuccesses.none { it.practiceId == practice.practiceId })
            listOf(ScenarioSuccess(practice.practiceId, practice.template.id, session.id, ending.at)) else emptyList()
        return data.copy(sessions = data.sessions.map { if (it.id == session.id) session else it },
            scenarioSuccesses = data.scenarioSuccesses + success)
    }
}

/** Assessment replies cannot apply after edits, corrections, mode switches or another request. */
data class ScenarioCheckSnapshot(val session: ChatSession) {
    fun matches(current: ChatSession): Boolean = current.id == session.id &&
        current.scenario?.practiceId == session.scenario?.practiceId && current.scenario?.revision == session.scenario?.revision &&
        current.scenario?.phase == ScenarioPhase.ACTIVE && LearningFeatures.contextDigest(current.messages) == LearningFeatures.contextDigest(session.messages)
}
