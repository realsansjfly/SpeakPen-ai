package com.speakpen.app

import com.speakpen.app.data.*
import com.speakpen.app.network.ScenarioProtocol
import org.junit.Assert.*
import org.junit.Test

class ScenarioStateTest {
    private fun template(max: Int? = 2) = ScenarioTemplate(
        id = "test:cafe", name = "Cafe", aiRole = "barista", userRole = "customer",
        goals = listOf(ScenarioGoal("g1", "order a drink")), opening = "Welcome!", endCondition = "the order is confirmed", maxTurns = max,
    )
    private fun active() = Scenarios.start(template(), ConversationConfig()).let { session ->
        val user = ChatMessage(id = "u1", role = "user", text = "I'd like a latte")
        val reply = ChatMessage(id = "a1", role = "assistant", text = "Sure, one latte.", replyToMessageId = user.id)
        Scenarios.recordTurn(session.copy(messages = session.messages + user + reply), user.id, reply.id)
    }

    @Test fun openingIsShownAndTemplateIsSnapshotted() {
        val session = Scenarios.start(template(), ConversationConfig())
        assertEquals(ConversationMode.SCENARIO, session.mode)
        assertEquals("Welcome!", session.messages.single().text)
        assertEquals("test:cafe", session.scenario?.template?.id)
        assertNull(session.scenario?.ending)
    }

    @Test fun successfulAssessmentEndsOnceAndPersistsExactBoundary() {
        val session = active()
        val assessment = ScenarioAssessment(
            listOf(ScenarioGoalProgress("g1", true, "用户点单", listOf(ScenarioEvidence("u1", "I'd like a latte")))),
            true, "订单已确认", listOf(ScenarioEvidence("u1", "I'd like a latte")),
        )
        val ended = Scenarios.assess(session, assessment, 99L)
        assertEquals(ScenarioPhase.ENDED, ended.scenario?.phase)
        assertTrue(ended.scenario?.ending?.succeeded == true)
        assertEquals("a1", ended.scenario?.ending?.boundaryMessageId)
        assertEquals(1, ended.scenario?.ending?.completedTurns)
        val data = Scenarios.settle(SavedData(sessions = listOf(ended)), ended)
        val again = Scenarios.settle(data, ended)
        assertEquals(1, again.scenarioSuccesses.size)
    }

    @Test fun turnLimitWithoutGoalsEndsWithoutSuccessAndContinueReleasesLimits() {
        val session = active()
        val assessment = ScenarioAssessment(listOf(ScenarioGoalProgress("g1", false, "无证据")), false, "未完成", emptyList())
        val ended = Scenarios.assess(session, assessment, 100L)
        assertEquals(ScenarioPhase.ACTIVE, ended.scenario?.phase)
        val second = ended.copy(messages = ended.messages + ChatMessage(id = "u2", role = "user", text = "Thanks") + ChatMessage(id = "a2", role = "assistant", text = "Bye"))
        val withTurn = Scenarios.recordTurn(second, "u2", "a2")
        val limited = Scenarios.assess(withTurn, assessment, 101L)
        assertEquals(ScenarioEndReason.TURN_LIMIT, limited.scenario?.ending?.reason)
        assertFalse(limited.scenario?.ending?.succeeded == true)
        val continued = Scenarios.continueFreely(limited)
        assertEquals(ConversationMode.FREE, continued.mode)
        assertEquals(ScenarioPhase.CONTINUED, continued.scenario?.phase)
        assertTrue(continued.scenario?.ending?.succeeded == false)
    }

    @Test fun goalAndTurnLimitOnSameReplyCountsAsSuccess() {
        val oneTurn = active().copy(scenario = active().scenario!!.copy(template = template(max = 1)))
        val assessment = ScenarioAssessment(listOf(ScenarioGoalProgress("g1", true, "已点单", listOf(ScenarioEvidence("u1", "I'd like a latte")))), true, "已确认", listOf(ScenarioEvidence("u1", "I'd like a latte")))
        val ended = Scenarios.assess(oneTurn, assessment, 77L)
        assertEquals(ScenarioEndReason.GOALS_REACHED, ended.scenario?.ending?.reason)
        assertTrue(ended.scenario?.ending?.succeeded == true)
    }

    @Test fun invalidEvidenceAndUnknownGoalFailClosed() {
        val session = active()
        val raw = "{\"goals\":[{\"goalId\":\"g1\",\"achieved\":true,\"reason\":\"x\",\"evidence\":[{\"messageId\":\"u1\",\"quote\":\"invented\"}]}],\"conditionMet\":true,\"conditionReason\":\"x\",\"conditionEvidence\":[]}" 
        assertThrows(IllegalStateException::class.java) { ScenarioProtocol.parse(raw, session) }
    }

    @Test fun dataRoundTripKeepsScenarioBoundaryAndLegacyData() {
        val session = active()
        val assessment = ScenarioAssessment(listOf(ScenarioGoalProgress("g1", true, "ok", listOf(ScenarioEvidence("u1", "I'd like a latte")))), true, "ok", listOf(ScenarioEvidence("u1", "I'd like a latte")))
        val ended = Scenarios.assess(session, assessment, 12L)
        val original = SavedData(sessions = listOf(ended), scenarioSuccesses = listOf(ScenarioSuccess("p", "t", ended.id, 1L)))
        val roundTrip = DataJson.read(DataJson.write(original))
        assertEquals(ScenarioPhase.ENDED, roundTrip.sessions.single().scenario?.phase)
        assertEquals("a1", roundTrip.sessions.single().scenario?.ending?.boundaryMessageId)
        assertEquals("g1", roundTrip.sessions.single().scenario?.template?.goals?.single()?.id)
        assertEquals(1, roundTrip.scenarioSuccesses.size)
    }
}
