package com.lifeskill.app

import com.lifeskill.app.model.*
import org.junit.Assert.*
import org.junit.Test

class TeachingInteractionsTest {
    private val legacyClassification = TeachingClassification("classification", "观察还是解释",
        "为每条陈述选择更准确的类别。",
        listOf(ClassificationCategory("observation", "数据观察"), ClassificationCategory("explanation", "原因解释")),
        listOf(
            ClassificationItem("april", "四月借阅 52 次，高于其他月份。", "observation",
                "52 次可以直接从数据中读出，不需要补充背景。"),
            ClassificationItem("festival", "读书节可能提高了四月借阅量。", "explanation",
                "借阅增加的原因需要额外证据，因此属于解释或假设。"),
            ClassificationItem("march", "三月比二月少 8 次。", "observation",
                "两个数相减即可得到差异，属于直接观察。"),
            ClassificationItem("hours", "开放时间延长导致借阅增加。", "explanation",
                "这是对变化的因果解释，需要检查开放时间与借阅量的实际关系。"),
        ), "先问自己：这句话是否能直接从已有数据中读出？",
        "可直接读取的数值和差异是观察；说明“为什么”的因果陈述是解释，需要额外证据。")
    private val legacyTrace = TeachingStepTrace("trace", "百分比变化为什么不能直接相加",
        "某商品价格先由 100 元降到 80 元，再回到 100 元。先预测每一步的变化率。",
        listOf(
            TeachingTraceStep("down", "第一次下降",
                TeachingQuestion("从 100 元降到 80 元，以原价 100 元为基数，下降了百分之几？",
                    listOf("20%", "25%"), 0,
                    "20 ÷ 100 = 20%，第一次变化的基数是 100 元。", "用变化量除以这次变化前的数。"),
                "第一次下降 20%，价格变为 80 元。"),
            TeachingTraceStep("up", "第二次上涨",
                TeachingQuestion("从 80 元涨回 100 元，以 80 元为基数，上涨了百分之几？",
                    listOf("20%", "25%"), 1,
                    "20 ÷ 80 = 25%，第二次变化的基数已经变成 80 元。", "注意这次计算从哪个数开始。"),
                "第二次上涨 25%，价格重新回到 100 元。"),
            TeachingTraceStep("combine", "能否直接相加",
                TeachingQuestion("−20% 与 +25% 能直接相加当作最终变化吗？",
                    listOf("能，合计 +5%", "不能，两次基数不同"), 1,
                    "两次百分比使用了不同基数，最终净值回到 0，不能把百分数当作同一单位直接相加。",
                    "比较两次百分比的基数是否相同。"),
                "最终价格仍是 100 元，净变化为 0；百分比变化必须结合基数和先后顺序解释。"),
        ), "价格最终没有变化。−20% 和 +25% 不能相加，因为第二次的 25% 以 80 元为基数。")

    private val chart get() = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingChart>()
        .first { it.mode == TeachingChartMode.LINEAR_DEMAND }

    @Test fun everyRegisteredTeachingTypeHasAnOfflineShowcaseLesson() {
        // 密封类型自动覆盖未来新增组件；停用类型仅保留旧记录兼容，不要求出现在当前演示目录。
        assertEquals(TeachingChartStyle.entries.toSet(), BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingChart>().map { it.style }.toSet())
        assertEquals(setOf(ComparisonPalette.BLUE_AMBER), BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingComparison>().map { it.palette }.toSet())
        val required = checkNotNull(TeachingBlock::class.java.permittedSubclasses).toSet() -
            setOf(TeachingClassification::class.java, TeachingStepTrace::class.java)
        assertFalse(legacyClassification.isAvailable)
        assertFalse(legacyTrace.isAvailable)
        assertTrue(BuiltInTeaching.lessons.flatMap { it.blocks }.all { it.isAvailable })
        val showcased = BuiltInTeaching.lessons.flatMap { it.blocks }.map { it.javaClass }.toSet()
        assertEquals(required, showcased)
        assertEquals(TeachingChartMode.entries.toSet(), BuiltInTeaching.lessons.flatMap { it.blocks }
            .filterIsInstance<TeachingChart>().map { it.mode }.toSet())
    }

    @Test fun demandRequiresPredictionAndUsesOnlyTheFixedModel() {
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(chart, TeachingState(), TeachingAction.Reveal) }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(chart, TeachingState(), TeachingAction.SetValue(4.0)) }
        val predicted = TeachingInteractions.apply(chart, TeachingState(), TeachingAction.Predict(1))
        val revealed = TeachingInteractions.apply(chart, predicted, TeachingAction.Reveal)
        val adjusted = TeachingInteractions.apply(chart, revealed, TeachingAction.SetValue(4.0))
        val price = checkNotNull(adjusted.value)
        assertEquals(4.0, price, 0.0)
        assertEquals(80.0, chart.demandAt(price), 0.0)
        assertEquals(100.0, chart.demandAt(2.0), 0.0)
        assertEquals(20.0, chart.demandAt(10.0), 0.0)
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(chart, adjusted, TeachingAction.SetValue(Double.NaN)) }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(chart, adjusted, TeachingAction.SetValue(11.0)) }
    }

    @Test fun practiceRetriesDoNotDuplicateSubmissionAndCorrectAnswersStaySettled() {
        val block = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingQuiz>().first()
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(block, TeachingState(), TeachingAction.Submit) }
        val wrong = TeachingInteractions.apply(block, TeachingState(), TeachingAction.Select("0"))
        val submitted = TeachingInteractions.apply(block, wrong, TeachingAction.Submit)
        assertEquals(1, submitted.attempts)
        assertEquals(submitted, TeachingInteractions.apply(block, submitted, TeachingAction.Submit))
        val retry = TeachingInteractions.apply(block, submitted, TeachingAction.Select(block.question.answer.toString()))
        assertFalse(retry.submitted)
        val correct = TeachingInteractions.apply(block, retry, TeachingAction.Submit)
        assertEquals(2, correct.attempts)
        assertEquals(correct, TeachingInteractions.apply(block, correct, TeachingAction.Select("0")))
        assertEquals(TeachingState(), TeachingInteractions.apply(block, correct, TeachingAction.Reset))
    }

    @Test fun componentSelectionsCannotReferenceOtherBlocks() {
        val timeline = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingTimeline>().first()
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(timeline, TeachingState(), TeachingAction.Select("missing")) }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(timeline, TeachingState(), TeachingAction.Submit) }
        assertEquals("succession", TeachingInteractions.apply(timeline, TeachingState(), TeachingAction.Select("succession")).selection)
        assertEquals(BuiltInTeaching.lessons.size, BuiltInTeaching.lessons.map { it.installMarker }.distinct().size)
        assertEquals(4, BuiltInTeaching.lessons.size)
        val itemMarkers = BuiltInTeaching.lessons.flatMap { lesson -> lesson.items.map { it.installMarker } }
        assertEquals(itemMarkers.size, itemMarkers.distinct().size)
        assertTrue(itemMarkers.none { it == BuiltInTeaching.INSTALL_MARKER || it in BuiltInTeaching.lessons.map { lesson -> lesson.installMarker } })
    }

    @Test fun flipSortingAndClassificationPersistMeaningfulIntermediateState() {
        val flip = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingFlipCard>().single()
        val back = TeachingInteractions.apply(flip, TeachingState(), TeachingAction.Flip)
        assertEquals("back", back.selection)
        assertEquals(TeachingState(), TeachingInteractions.apply(flip, back, TeachingAction.Flip))

        val sorting = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingSorting>().single()
        val moved = TeachingInteractions.apply(sorting, TeachingState(), TeachingAction.Move("observe", 0))
        assertEquals(listOf("observe", "validate", "decide", "goal"), moved.order)
        assertThrows(RuleViolation::class.java) {
            TeachingInteractions.apply(sorting, TeachingState(), TeachingAction.Reveal)
        }
        val wrong = TeachingInteractions.apply(sorting, TeachingState(), TeachingAction.Submit)
        assertEquals(1, wrong.attempts)
        assertFalse(wrong.sortComplete(sorting))
        val revealed = TeachingInteractions.apply(sorting, wrong, TeachingAction.Reveal)
        assertTrue(revealed.revealed)
        assertThrows(RuleViolation::class.java) {
            TeachingInteractions.apply(sorting, revealed, TeachingAction.Move("observe", 0))
        }
        val sorted = TeachingInteractions.apply(sorting, TeachingState(order = sorting.correctOrder), TeachingAction.Submit)
        assertTrue(sorted.sortComplete(sorting))

        val classification = legacyClassification
        var assigned = TeachingState()
        classification.items.forEach { item ->
            assigned = TeachingInteractions.apply(classification, assigned,
                TeachingAction.Classify(item.id, classification.categories.first().id))
        }
        val compared = TeachingInteractions.apply(classification, assigned, TeachingAction.Submit)
        assertFalse(compared.classificationComplete(classification))
        assertEquals(1, compared.attempts)
        val classificationRevealed = TeachingInteractions.apply(classification, compared, TeachingAction.Reveal)
        assertTrue(classificationRevealed.revealed)
        val revealRetry = TeachingInteractions.apply(classification, classificationRevealed,
            TeachingAction.Classify(classification.items.first().id, classification.categories.last().id))
        assertFalse(revealRetry.revealed)
        assertFalse(revealRetry.submitted)

        var correctPlacements = TeachingState()
        classification.items.forEach { item ->
            correctPlacements = TeachingInteractions.apply(classification, correctPlacements,
                TeachingAction.Classify(item.id, item.categoryId))
        }
        val completed = TeachingInteractions.apply(classification, correctPlacements, TeachingAction.Submit)
        assertTrue(completed.classificationComplete(classification))
        val firstItem = classification.items.first()
        val changed = TeachingInteractions.apply(classification, completed,
            TeachingAction.Classify(firstItem.id,
                classification.categories.first { it.id != firstItem.categoryId }.id))
        assertFalse(changed.submitted)
        assertFalse(changed.classificationComplete(classification))
    }

    @Test fun hotspotAndStepTraceExposeAnswersOnlyThroughExplicitActions() {
        val hotspot = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingHotspot>().single()
        val selected = TeachingInteractions.apply(hotspot, TeachingState(), TeachingAction.Select("nucleus"))
        assertEquals("nucleus", selected.selection)
        assertTrue(TeachingInteractions.apply(hotspot, selected, TeachingAction.Reveal).revealed)

        val trace = legacyTrace
        val first = trace.steps.first()
        val firstChoice = TeachingInteractions.apply(trace, TeachingState(),
            TeachingAction.Select(first.checkpoint.answer.toString()))
        val firstSubmitted = TeachingInteractions.apply(trace, firstChoice, TeachingAction.Submit)
        assertTrue(firstSubmitted.currentStepCorrect(trace))
        val switched = TeachingInteractions.apply(trace, firstSubmitted, TeachingAction.Select(
            ((first.checkpoint.answer + 1) % first.checkpoint.options.size).toString(),
        ))
        assertFalse(switched.submitted)
        assertEquals(
            ((first.checkpoint.answer + 1) % first.checkpoint.options.size).toString(),
            switched.selection,
        )
        val advanced = TeachingInteractions.apply(trace, firstSubmitted, TeachingAction.Advance)
        assertEquals(1, advanced.step)
        val second = trace.steps[1]
        val wrongChoice = TeachingInteractions.apply(trace, advanced,
            TeachingAction.Select(((second.checkpoint.answer + 1) % second.checkpoint.options.size).toString()))
        val wrong = TeachingInteractions.apply(trace, wrongChoice, TeachingAction.Submit)
        assertFalse(wrong.currentStepCorrect(trace))
        assertThrows(RuleViolation::class.java) {
            TeachingInteractions.apply(trace, wrongChoice, TeachingAction.Reveal)
        }
        assertTrue(TeachingInteractions.apply(trace, wrong, TeachingAction.Reveal).revealed)
    }

    @Test fun practiceRequiresAWrongAttemptBeforeShowingTheFullAnswer() {
        val block = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingQuiz>().first()
        val wrongChoice = TeachingInteractions.apply(block, TeachingState(),
            TeachingAction.Select(((block.question.answer + 1) % block.question.options.size).toString()))
        assertThrows(RuleViolation::class.java) {
            TeachingInteractions.apply(block, wrongChoice, TeachingAction.Reveal)
        }
        val submitted = TeachingInteractions.apply(block, wrongChoice, TeachingAction.Submit)
        assertTrue(TeachingInteractions.apply(block, submitted, TeachingAction.Reveal).revealed)
    }
}
