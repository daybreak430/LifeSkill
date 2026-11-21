package com.lifeskill.app.model

import kotlin.math.roundToInt

sealed interface TeachingBlock {
    val id: String
    val title: String
}

// 暂停的组件仍保留旧快照编解码，避免升级改写用户记录；展示、模型输入输出和交互统一使用此门槛。
internal val TeachingBlock.isAvailable: Boolean
    get() = this !is TeachingClassification && this !is TeachingStepTrace

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
enum class ComparisonPalette { BLUE_AMBER, VIOLET_TEAL, ROSE_SAGE }

data class TeachingComparison(
    override val id: String, override val title: String, val columns: List<String>, val dimensions: List<ComparisonDimension>,
    val palette: ComparisonPalette = ComparisonPalette.BLUE_AMBER,
) : TeachingBlock
enum class TeachingChartStyle { LINE, BAR, AREA }

enum class TeachingChartMode { DATA, LINEAR_DEMAND }
data class TeachingPoint(val label: String, val value: Double)
data class TeachingQuestion(
    val question: String, val options: List<String>, val answer: Int, val explanation: String, val hint: String,
)
data class TeachingChart(
    override val id: String, override val title: String, val xLabel: String, val yLabel: String,
    val points: List<TeachingPoint> = emptyList(), val mode: TeachingChartMode = TeachingChartMode.DATA,
    val prediction: TeachingQuestion? = null,
    val quantityUnit: String = "单位",
    val style: TeachingChartStyle = TeachingChartStyle.LINE,
) : TeachingBlock {
    // 模板只允许应用实现并验证过的函数，不接收模型返回的表达式或可执行代码。
    fun demandAt(price: Double): Double = (120.0 - 10.0 * price.coerceIn(2.0, 10.0)).coerceAtLeast(0.0)
}
data class TeachingQuiz(
    override val id: String, override val title: String, val question: TeachingQuestion,
) : TeachingBlock

data class TeachingFlipCard(
    override val id: String, override val title: String, val prompt: String, val answer: String, val hint: String,
) : TeachingBlock

data class TeachingSortItem(val id: String, val text: String)
data class TeachingSorting(
    override val id: String, override val title: String, val prompt: String, val items: List<TeachingSortItem>,
    val correctOrder: List<String>, val hint: String, val explanation: String,
) : TeachingBlock

data class ClassificationCategory(val id: String, val title: String)
data class ClassificationItem(
    val id: String, val text: String, val categoryId: String, val explanation: String,
)
data class TeachingClassification(
    override val id: String, override val title: String, val prompt: String,
    val categories: List<ClassificationCategory>, val items: List<ClassificationItem>,
    val hint: String, val explanation: String,
) : TeachingBlock

enum class TeachingHotspotImage { PLANT_CELL }
data class ImageHotspot(
    val id: String, val label: String, val horizontalPercent: Double, val verticalPercent: Double, val detail: String,
)
data class TeachingHotspot(
    override val id: String, override val title: String, val prompt: String,
    val image: TeachingHotspotImage, val hotspots: List<ImageHotspot>, val hint: String,
) : TeachingBlock

data class TeachingTraceStep(
    val id: String, val title: String, val checkpoint: TeachingQuestion, val result: String,
)
data class TeachingStepTrace(
    override val id: String, override val title: String, val introduction: String,
    val steps: List<TeachingTraceStep>, val summary: String,
) : TeachingBlock

data class TeachingState(
    val selection: String? = null, val value: Double? = null, val submitted: Boolean = false,
    val attempts: Int = 0, val prediction: Int? = null, val revealed: Boolean = false,
    val order: List<String> = emptyList(), val placements: Map<String, String> = emptyMap(), val step: Int = 0,
    val lever: LeverParameters? = null,
    val gameOpened: Boolean = false,
)

sealed interface TeachingAction {
    data class Select(val key: String) : TeachingAction
    data class SetValue(val value: Double) : TeachingAction
    data class SetLeverParameter(val parameter: LeverParameter, val value: Double) : TeachingAction
    data class Predict(val index: Int) : TeachingAction
    data class Move(val key: String, val toIndex: Int) : TeachingAction
    data class Classify(val key: String, val category: String) : TeachingAction
    data object OpenGame : TeachingAction
    data object Flip : TeachingAction
    data object Advance : TeachingAction
    data object Reveal : TeachingAction
    data object Submit : TeachingAction
    data object Reset : TeachingAction
}

internal object TeachingInteractions {
    fun apply(block: TeachingBlock, previous: TeachingState, action: TeachingAction): TeachingState = when (action) {
        TeachingAction.Reset -> TeachingState()
        TeachingAction.OpenGame -> {
            if (block !is TeachingGame) invalid()
            previous.copy(gameOpened = true)
        }
        is TeachingAction.Select -> {
            val quizCorrect = block is TeachingQuiz && previous.submitted &&
                previous.selection?.toIntOrNull() == block.question.answer
            val traceCorrect = block is TeachingStepTrace && previous.currentStepCorrect(block)
            val valid = when (block) {
                is TeachingTimeline -> block.events.any { it.id == action.key }
                is TeachingFlow -> action.key in block.details
                is TeachingComparison -> block.dimensions.any { it.id == action.key }
                is TeachingChart -> block.mode == TeachingChartMode.DATA && action.key.toIntOrNull() in block.points.indices
                is TeachingQuiz -> action.key.toIntOrNull() in block.question.options.indices && !previous.revealed
                is TeachingStepTrace -> {
                    val checkpoint = block.steps.getOrNull(previous.step)?.checkpoint
                    val index = action.key.toIntOrNull()
                    checkpoint != null && index != null && index in checkpoint.options.indices &&
                        !previous.revealed
                }
                is TeachingHotspot -> block.hotspots.any { it.id == action.key }
                is TeachingText, is TeachingFlipCard, is TeachingSorting, is TeachingClassification, is TeachingExperiment, is TeachingGame,
                -> false
            }
            if (!valid) invalid()
            when {
                quizCorrect -> previous
                traceCorrect && action.key == previous.selection -> previous
                else -> previous.copy(selection = action.key, submitted = false)
            }
        }
        is TeachingAction.SetValue -> {
            if (block !is TeachingChart || block.mode != TeachingChartMode.LINEAR_DEMAND ||
                !action.value.isFinite() || action.value !in 2.0..10.0 || block.prediction != null && !previous.revealed
            ) invalid()
            previous.copy(value = action.value.roundToInt().toDouble())
        }
        is TeachingAction.SetLeverParameter -> {
            if (block !is TeachingExperiment) invalid()
            // 单个参数在仓库事务内合并，避免快速调整不同滑块时旧快照覆盖刚保存的其他参数。
            previous.copy(lever = LeverPhysics.update(previous.lever ?: block.initial, action.parameter, action.value))
        }
        is TeachingAction.Predict -> {
            if (block !is TeachingChart || block.prediction == null || action.index !in block.prediction.options.indices ||
                previous.revealed
            ) invalid()
            previous.copy(prediction = action.index)
        }
        is TeachingAction.Move -> {
            if (block !is TeachingSorting || previous.revealed || previous.sortComplete(block)) invalid()
            val order = previous.sortOrder(block)
            val from = order.indexOf(action.key)
            if (from < 0 || action.toIndex !in order.indices) invalid()
            previous.copy(order = order.toMutableList().apply {
                add(action.toIndex, removeAt(from))
            }, submitted = false)
        }
        is TeachingAction.Classify -> {
            if (block !is TeachingClassification || block.items.none { it.id == action.key } ||
                block.categories.none { it.id == action.category }
            ) invalid()
            if (previous.placements[action.key] == action.category) return previous
            // 改选会退出完整答案视图，并恢复本次分类提交能力。
            previous.copy(
                placements = previous.placements.filterValues { category ->
                    block.categories.any { it.id == category }
                } + (action.key to action.category),
                submitted = false,
                revealed = false,
            )
        }
        TeachingAction.Flip -> {
            if (block !is TeachingFlipCard) invalid()
            previous.copy(selection = if (previous.selection == "back") null else "back", submitted = false)
        }
        TeachingAction.Advance -> {
            if (block !is TeachingStepTrace || previous.revealed || previous.step !in block.steps.indices ||
                !previous.currentStepCorrect(block) || previous.step >= block.steps.lastIndex
            ) invalid()
            previous.copy(selection = null, submitted = false, step = previous.step + 1)
        }
        TeachingAction.Reveal -> {
            val valid = when (block) {
                is TeachingChart -> block.prediction != null && previous.prediction in block.prediction.options.indices
                is TeachingQuiz -> previous.submitted && previous.selection?.toIntOrNull() != block.question.answer
                is TeachingSorting -> previous.submitted && !previous.sortComplete(block)
                is TeachingClassification -> previous.submitted && !previous.classificationComplete(block)
                is TeachingStepTrace -> previous.submitted && !previous.currentStepCorrect(block)
                is TeachingHotspot -> true
                is TeachingText, is TeachingTimeline, is TeachingFlow, is TeachingComparison, is TeachingFlipCard, is TeachingExperiment, is TeachingGame -> false
            }
            if (!valid) invalid()
            previous.copy(revealed = true)
        }
        TeachingAction.Submit -> {
            val valid = when (block) {
                is TeachingQuiz -> previous.selection?.toIntOrNull() in block.question.options.indices && !previous.revealed
                is TeachingSorting -> !previous.revealed
                is TeachingClassification -> !previous.revealed && block.items.all { previous.placements[it.id] != null }
                is TeachingStepTrace -> {
                    val checkpoint = block.steps.getOrNull(previous.step)?.checkpoint
                    checkpoint != null && !previous.revealed &&
                        previous.selection?.toIntOrNull() in checkpoint.options.indices
                }
                else -> false
            }
            if (!valid) invalid()
            // 重复点击提交不增加次数；更换顺序、分类或答案后才进入下一次尝试。
            if (previous.submitted) previous else previous.copy(
                submitted = true, attempts = (previous.attempts + 1).coerceAtMost(1_000),
            )
        }
    }

    private fun invalid(): Nothing = throw RuleViolation("这个互动操作已失效，请重新打开课时。")
}

internal fun TeachingState.sortOrder(block: TeachingSorting): List<String> {
    val validIds = block.items.map(TeachingSortItem::id).toSet()
    return order.distinct().filter(validIds::contains) +
        block.items.map(TeachingSortItem::id).filterNot(order::contains)
}

internal fun TeachingState.sortComplete(block: TeachingSorting): Boolean =
    submitted && sortOrder(block) == block.correctOrder

internal fun TeachingState.classificationComplete(block: TeachingClassification): Boolean =
    submitted && block.items.all { placements[it.id] == it.categoryId }

internal fun TeachingState.currentStepCorrect(block: TeachingStepTrace): Boolean {
    val checkpoint = block.steps.getOrNull(step)?.checkpoint ?: return false
    return submitted && selection?.toIntOrNull() == checkpoint.answer
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
            appendLine("教学模型：Q = 120 − 10P，价格 P 为 2–10 元，需求量 Q 的单位为${block.quantityUnit}，其他条件不变。")
        } else block.points.forEach { appendLine("- ${it.label}：${it.value}（${block.yLabel}）") }
        is TeachingGame -> {
            appendLine(block.goal)
            appendLine("玩法：${block.instructions}")
            appendLine("放大后游玩；小游戏反馈不计正式评估。")
        }
        is TeachingExperiment -> {
            appendLine(block.prompt)
            appendLine("理想轻杆，两侧竖直受力；比较 F × 到支点的水平距离，平衡时两侧相等。倾角仅作方向示意。")
            appendLine("初始状态：${block.initial.summary()}")
        }
        is TeachingQuiz -> {
            appendLine(block.question.question)
            block.question.options.forEachIndexed { index, option -> appendLine("- ${choiceAnswer(index, option)}") }
        }
        is TeachingFlipCard -> appendLine(block.prompt)
        is TeachingSorting -> {
            appendLine(block.prompt)
            block.items.forEachIndexed { index, item -> appendLine("${index + 1}. ${item.text}") }
        }
        is TeachingClassification -> {
            appendLine(block.prompt)
            appendLine("分类：${block.categories.joinToString("、") { it.title }}")
            block.items.forEach { appendLine("- ${it.text}") }
        }
        is TeachingHotspot -> {
            appendLine(block.prompt)
            block.hotspots.forEach { appendLine("- ${it.label}") }
        }
        is TeachingStepTrace -> {
            appendLine(block.introduction)
            block.steps.forEach { appendLine("**${it.title}**：${it.checkpoint.question}") }
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
        else "预测：${prediction ?: "无"}；已观察价格 ${price.roundToInt()} 元，需求量 ${block.demandAt(price).roundToInt()} ${block.quantityUnit}"
    } else state.selection?.toIntOrNull()?.let { block.points.getOrNull(it) }?.let { "查看 ${it.label}：${it.value}" }.orEmpty()
    is TeachingQuiz -> state.selection?.toIntOrNull()?.let { index ->
        "随堂作答：${block.question.options.getOrNull(index).orEmpty()}；" + when {
            !state.submitted -> "未提交"
            state.revealed -> "已查看完整答案，正确选项为 ${choiceAnswer(block.question.answer, block.question.options[block.question.answer])}"
            index == block.question.answer -> "答对"
            else -> "答错，尝试 ${state.attempts} 次（不计课时完成）"
        }
    }.orEmpty()
    is TeachingFlipCard -> if (state.selection == "back") "已翻看答案" else "仍在回忆"
    is TeachingSorting -> when {
        state.revealed -> "已查看正确顺序"
        !state.submitted -> "已调整顺序，尚未提交"
        else -> "${if (state.sortComplete(block)) "顺序正确" else "顺序仍需调整"}，尝试 ${state.attempts} 次"
    }
    is TeachingClassification -> when {
        state.revealed -> "已查看完整分类答案"
        !state.submitted -> "已归类 ${state.placements.keys.count { key -> block.items.any { it.id == key } }} / ${block.items.size} 项"
        else -> "${if (state.classificationComplete(block)) "分类正确" else "分类仍需调整"}，尝试 ${state.attempts} 次"
    }
    is TeachingHotspot -> if (state.revealed) "已查看全部热点解释" else block.hotspots.find {
        it.id == state.selection
    }?.let { "查看热点：${it.label}" }.orEmpty()
    is TeachingStepTrace -> {
        val current = block.steps.getOrNull(state.step)
        if (state.revealed) "已查看完整推演与结论" else current?.let {
            "推演 ${state.step + 1}/${block.steps.size}：${it.title}；" + when {
                state.currentStepCorrect(block) -> "预测正确"
                state.submitted -> "预测错误，等待重试"
                else -> "尚未提交"
            }
        }.orEmpty()
    }
    is TeachingExperiment -> "杠杆实验：${(state.lever ?: block.initial).summary()}"
    is TeachingGame -> if (state.gameOpened) "已打开小游戏：${block.goal}；游戏反馈不计正式评估" else "尚未打开小游戏"
    is TeachingText -> ""
}
