package com.speakpen.app.data

/**
 * 一次查词建议。与 [VocabularySuggestion] 同构：先出建议，由用户确认后才写回条目；
 * 手填释义永远优先，AI 结果只留在自己的字段里。
 */
data class MeaningSuggestion(
    val itemId: String, val expression: String, val meaning: String, val example: String,
    val provenance: String, val demo: Boolean = false,
)

object MeaningLookup {
    const val MAX_QUERY = 120
    const val DEMO_PROVENANCE = "示例内容，未接入服务"

    /** 单行化并校验：只有含拉丁字母、长度合理的表达才值得发一次模型请求。 */
    fun queryOf(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty() || cleaned.length > MAX_QUERY) return null
        if (cleaned.none { it in 'a'..'z' || it in 'A'..'Z' }) return null
        return cleaned
    }

    fun kindFor(query: String): ReviewKind = if (query.trim().contains(' ')) ReviewKind.PHRASE else ReviewKind.WORD

    /** 已有释义就不再请求：覆盖用户内容与重复消耗服务额度都不正当。 */
    fun needsLookup(item: ReviewItem): Boolean = !item.hasMeaning && queryOf(item.content) != null

    fun suggestion(item: ReviewItem, meaning: String, example: String, provenance: String, demo: Boolean = false) =
        MeaningSuggestion(item.id, queryOf(item.content) ?: item.content.trim(), meaning, example, provenance, demo)

    /** 示例模式不联网，给明确标注的示例释义，与本仓库既有 demo 分支（示例提取、示例候选）同一策略。 */
    fun demo(item: ReviewItem) = suggestion(
        item,
        "示例释义：这里会显示这条英文表达的中文意思。",
        "Could you give me a moment to think?",
        DEMO_PROVENANCE, demo = true,
    )

    /** 写回条目：手填优先，AI 结果不覆盖任何手写内容。 */
    fun apply(item: ReviewItem, suggestion: MeaningSuggestion): ReviewItem =
        if (item.hasMeaning || suggestion.meaning.isBlank()) item
        else item.copy(aiMeaning = suggestion.meaning, aiExample = suggestion.example, aiProvenance = suggestion.provenance)
}
