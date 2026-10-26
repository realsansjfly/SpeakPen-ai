package com.speakpen.app.network

import com.speakpen.app.data.*

object ConversationPrompts {
    fun expression(config: ConversationConfig): String = when (config.difficulty) {
        ExpressionDifficulty.SIMPLE -> "Target A1–A2 expression: prefer common concrete words, short independent clauses and simple questions. Explain an unavoidable difficult word briefly in Chinese; keep surrounding syntax simple."
        ExpressionDifficulty.DAILY -> "Target B1 expression: use everyday vocabulary, common collocations and connected but clear sentences. Explain uncommon wording briefly when useful."
        ExpressionDifficulty.DEEP -> "Target B2 expression: allow nuanced opinions, natural collocations and varied clauses without obscure vocabulary or unnecessary length."
    } + " These are output targets, not a proficiency certification. Preserve the user's intent, names and any explicit review targets."

    fun replyLength(config: ConversationConfig): String = when (config.advanced.replyLength) {
        ReplyLength.SHORT -> "Reply in 1–2 short sentences, with at most one question."
        ReplyLength.STANDARD -> "Reply in 1–4 natural sentences and one question at a time (at most)."
        ReplyLength.EXTENDED -> "Use up to 5–6 sentences when useful, with at most one question. Do not pad the answer."
    }
}
