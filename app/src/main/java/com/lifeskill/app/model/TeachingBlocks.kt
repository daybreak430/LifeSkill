package com.lifeskill.app.model

import kotlin.math.roundToInt

sealed interface TeachingBlock {
    val id: String
    val title: String
}

data class TeachingText(override val id: String, override val title: String, val text: String) : TeachingBlock
data class TimelineEvent(val id: String, val date: String, val title: String, val detail: String)
data class TeachingTimeline(
    override val id: String, override val title: String, val events: List<TimelineEvent>,
) : TeachingBlock
data class TeachingFlow(
    override val id: String, override val title: String, val diagram: String, val details: Map<String, String>,
) : TeachingBlock {
    internal val flow: LessonFlow? get() = LessonFlow.parse(diagram, mermaidFence = true)
}
data class ComparisonDimension(val id: String, val title: String, val values: List<String>)
data class TeachingComparison(
    override val id: String, override val title: String, val columns: List<String>, val dimensions: List<ComparisonDimension>,
) : TeachingBlock
enum class TeachingChartMode { DATA, LINEAR_DEMAND }
data class TeachingPoint(val label: String, val value: Double)
data class TeachingQuestion(
    val question: String, val options: List<String>, val answer: Int, val explanation: String, val hint: String,
)
data class TeachingChart(
    override val id: String, override val title: String, val xLabel: String, val yLabel: String,
    val points: List<TeachingPoint> = emptyList(), val mode: TeachingChartMode = TeachingChartMode.DATA,
    val prediction: TeachingQuestion? = null,
) : TeachingBlock {
    // 模板只允许应用实现并验证过的函数，不接收模型返回的表达式或可执行代码。
    fun demandAt(price: Double): Double = (120.0 - 10.0 * price.coerceIn(2.0, 10.0)).coerceAtLeast(0.0)
}
data class TeachingQuiz(
    override val id: String, override val title: String, val question: TeachingQuestion,
) : TeachingBlock

data class TeachingState(
    val selection: String? = null, val value: Double? = null, val submitted: Boolean = false,
    val attempts: Int = 0, val prediction: Int? = null, val revealed: Boolean = false,
)

sealed interface TeachingAction {
    data class Select(val key: String) : TeachingAction
    data class SetValue(val value: Double) : TeachingAction
    data class Predict(val index: Int) : TeachingAction
    data object Reveal : TeachingAction
    data object Submit : TeachingAction
    data object Reset : TeachingAction
}

internal object TeachingInteractions {
    fun apply(block: TeachingBlock, previous: TeachingState, action: TeachingAction): TeachingState = when (action) {
        TeachingAction.Reset -> TeachingState()
        is TeachingAction.Select -> {
            val valid = when (block) {
                is TeachingTimeline -> block.events.any { it.id == action.key }
                is TeachingFlow -> action.key in block.details
                is TeachingComparison -> block.dimensions.any { it.id == action.key }
                is TeachingChart -> block.mode == TeachingChartMode.DATA && action.key.toIntOrNull() in block.points.indices
                is TeachingQuiz -> action.key.toIntOrNull() in block.question.options.indices
                is TeachingText -> false
            }
            if (!valid) invalid()
            if (block is TeachingQuiz && previous.submitted && previous.selection?.toIntOrNull() == block.question.answer) previous
            else previous.copy(selection = action.key, submitted = false)
        }
        is TeachingAction.SetValue -> {
            if (block !is TeachingChart || block.mode != TeachingChartMode.LINEAR_DEMAND ||
                !action.value.isFinite() || action.value !in 2.0..10.0 || block.prediction != null && !previous.revealed
            ) invalid()
            previous.copy(value = action.value.roundToInt().toDouble())
        }
        is TeachingAction.Predict -> {
            if (block !is TeachingChart || block.prediction == null || action.index !in block.prediction.options.indices ||
                previous.revealed
            ) invalid()
            previous.copy(prediction = action.index)
        }
        TeachingAction.Reveal -> {
            if (block !is TeachingChart || block.prediction == null || previous.prediction !in block.prediction.options.indices) invalid()
            previous.copy(revealed = true)
        }
        TeachingAction.Submit -> {
            if (block !is TeachingQuiz || previous.selection?.toIntOrNull() !in block.question.options.indices) invalid()
            // 重复点击提交不增加次数；更换答案后才能进入下一次随堂尝试。
            if (previous.submitted) previous else previous.copy(submitted = true, attempts = (previous.attempts + 1).coerceAtMost(1_000))
        }
    }

    private fun invalid(): Nothing = throw RuleViolation("这个互动操作已失效，请重新打开课时。")
}

internal fun TeachingBlock.markdown(): String = buildString {
    appendLine("### $title")
    when (val block = this@markdown) {
        is TeachingText -> append(block.text)
        is TeachingTimeline -> block.events.forEach { appendLine("- ${it.date} · ${it.title}：${it.detail}") }
        is TeachingFlow -> {
            appendLine("```mermaid\n${block.diagram}\n```")
            block.details.forEach { (id, detail) -> appendLine("- ${block.flow?.labels?.get(id) ?: id}：$detail") }
        }
        is TeachingComparison -> block.dimensions.forEach { dimension ->
            appendLine("**${dimension.title}**")
            block.columns.zip(dimension.values).forEach { (column, value) -> appendLine("- $column：$value") }
        }
        is TeachingChart -> if (block.mode == TeachingChartMode.LINEAR_DEMAND) {
            appendLine("教学模型：Q = 120 − 10P，价格 P 为 2–10 元，其他条件不变。")
        } else block.points.forEach { appendLine("- ${it.label}：${it.value}（${block.yLabel}）") }
        is TeachingQuiz -> {
            appendLine(block.question.question)
            block.question.options.forEachIndexed { index, option -> appendLine("- ${choiceAnswer(index, option)}") }
        }
    }
}.trim()

internal fun TeachingBlock.interactionSummary(state: TeachingState): String = when (val block = this) {
    is TeachingTimeline -> block.events.find { it.id == state.selection }?.let { "查看 ${it.date} ${it.title}" }.orEmpty()
    is TeachingFlow -> state.selection?.let { "查看步骤 ${block.flow?.labels?.get(it) ?: it}" }.orEmpty()
    is TeachingComparison -> block.dimensions.find { it.id == state.selection }?.let { "比较维度：${it.title}" }.orEmpty()
    is TeachingChart -> if (block.mode == TeachingChartMode.LINEAR_DEMAND) {
        val price = state.value ?: 6.0
        val prediction = state.prediction?.let { block.prediction?.options?.getOrNull(it) }
        if (block.prediction != null && !state.revealed) "预测：${prediction ?: "尚未选择"}，尚未验证"
        else "预测：${prediction ?: "无"}；已观察价格 ${price.roundToInt()} 元，需求量 ${block.demandAt(price).roundToInt()} 杯"
    } else state.selection?.toIntOrNull()?.let { block.points.getOrNull(it) }?.let { "查看 ${it.label}：${it.value}" }.orEmpty()
    is TeachingQuiz -> state.selection?.toIntOrNull()?.let { index ->
        "随堂作答：${block.question.options.getOrNull(index).orEmpty()}；" +
            if (!state.submitted) "未提交" else "${if (index == block.question.answer) "答对" else "答错"}，尝试 ${state.attempts} 次（不计课时完成）"
    }.orEmpty()
    is TeachingText -> ""
}
