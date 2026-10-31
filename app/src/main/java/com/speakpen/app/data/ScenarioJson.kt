package com.speakpen.app.data

import org.json.JSONArray
import org.json.JSONObject

object ScenarioJson {
    private fun <T> array(items: List<T>, write: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(write(it)) } }
    fun template(value: ScenarioTemplate): JSONObject = JSONObject().apply {
        put("id", value.id); put("revision", value.revision); put("name", value.name); put("aiRole", value.aiRole); put("userRole", value.userRole)
        put("goals", array(value.goals) { JSONObject().put("id", it.id).put("text", it.text) })
        put("opening", value.opening); put("endCondition", value.endCondition); value.maxTurns?.let { put("maxTurns", it) }
    }
    fun readTemplate(json: JSONObject) = ScenarioTemplate(
        id = json.getString("id"), revision = json.optInt("revision", 1), name = json.getString("name"),
        aiRole = json.getString("aiRole"), userRole = json.getString("userRole"),
        goals = DataJson.objects(json.getJSONArray("goals")).map { ScenarioGoal(it.getString("id"), it.getString("text")) },
        opening = json.optString("opening"), endCondition = json.getString("endCondition"),
        maxTurns = if (json.has("maxTurns") && !json.isNull("maxTurns")) json.getInt("maxTurns") else null,
    )
    private fun evidence(value: ScenarioEvidence) = JSONObject().put("messageId", value.messageId).put("quote", value.quote)
    private fun readEvidence(json: JSONObject) = ScenarioEvidence(json.getString("messageId"), json.getString("quote"))
    fun assessment(value: ScenarioAssessment): JSONObject = JSONObject().apply {
        put("goals", array(value.goals) { goal -> JSONObject().put("goalId", goal.goalId).put("achieved", goal.achieved)
            .put("reason", goal.reason).put("evidence", array(goal.evidence, ::evidence)) })
        put("conditionMet", value.conditionMet); put("conditionReason", value.conditionReason); put("conditionEvidence", array(value.conditionEvidence, ::evidence))
    }
    fun readAssessment(json: JSONObject) = ScenarioAssessment(
        goals = DataJson.objects(json.getJSONArray("goals")).map { ScenarioGoalProgress(it.getString("goalId"), it.getBoolean("achieved"),
            it.getString("reason"), DataJson.objects(it.getJSONArray("evidence")).map(::readEvidence)) },
        conditionMet = json.getBoolean("conditionMet"), conditionReason = json.getString("conditionReason"),
        conditionEvidence = DataJson.objects(json.getJSONArray("conditionEvidence")).map(::readEvidence),
    )
    private fun correction(value: ScenarioCorrection) = JSONObject().put("goalId", value.goalId).put("achieved", value.achieved).put("note", value.note).put("at", value.at)
    private fun readCorrection(json: JSONObject) = ScenarioCorrection(json.getString("goalId"), json.getBoolean("achieved"), json.getString("note"), json.getLong("at"))
    fun practice(value: ScenarioPractice): JSONObject = JSONObject().apply {
        put("template", template(value.template)); put("practiceId", value.practiceId); put("phase", value.phase.name); put("revision", value.revision)
        put("turns", array(value.turns) { JSONObject().put("userMessageId", it.userMessageId).put("replyMessageId", it.replyMessageId) })
        value.assessment?.let { put("assessment", assessment(it)) }; put("corrections", array(value.corrections, ::correction))
        put("checkStatus", value.checkStatus.name); put("checkError", value.checkError); put("goalsCollapsed", value.goalsCollapsed)
        value.ending?.let { end -> put("ending", JSONObject().apply {
            put("reason", end.reason.name); put("succeeded", end.succeeded); put("at", end.at); put("boundaryMessageId", end.boundaryMessageId)
            put("messages", array(end.messages, DataJson::messageToJson)); end.assessment?.let { put("assessment", assessment(it)) }
            put("corrections", array(end.corrections, ::correction)); put("completedTurns", end.completedTurns)
        }) }
    }
    fun readPractice(json: JSONObject): ScenarioPractice {
        val end = json.optJSONObject("ending")?.let { ScenarioEnding(
            ScenarioEndReason.valueOf(it.getString("reason")), it.getBoolean("succeeded"), it.getLong("at"), it.getString("boundaryMessageId"),
            DataJson.objects(it.getJSONArray("messages")).map(DataJson::messageFromJson), it.optJSONObject("assessment")?.let(::readAssessment),
            DataJson.objects(it.getJSONArray("corrections")).map(::readCorrection), it.getInt("completedTurns")) }
        return ScenarioPractice(
            template = readTemplate(json.getJSONObject("template")), practiceId = json.getString("practiceId"), phase = ScenarioPhase.valueOf(json.getString("phase")),
            revision = json.getInt("revision"), turns = DataJson.objects(json.getJSONArray("turns")).map { ScenarioTurn(it.getString("userMessageId"), it.getString("replyMessageId")) },
            assessment = json.optJSONObject("assessment")?.let(::readAssessment), corrections = DataJson.objects(json.getJSONArray("corrections")).map(::readCorrection),
            checkStatus = ScenarioCheckStatus.valueOf(json.getString("checkStatus")), checkError = json.optString("checkError"),
            goalsCollapsed = json.optBoolean("goalsCollapsed"), ending = end,
        )
    }
    fun success(value: ScenarioSuccess) = JSONObject().put("practiceId", value.practiceId).put("templateId", value.templateId).put("sessionId", value.sessionId).put("at", value.at)
    fun readSuccess(json: JSONObject) = ScenarioSuccess(json.getString("practiceId"), json.getString("templateId"), json.getString("sessionId"), json.getLong("at"))
}
