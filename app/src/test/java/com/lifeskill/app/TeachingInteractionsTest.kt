package com.lifeskill.app

import com.lifeskill.app.model.*
import org.junit.Assert.*
import org.junit.Test

class TeachingInteractionsTest {
    private val chart get() = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingChart>()
        .first { it.mode == TeachingChartMode.LINEAR_DEMAND }

    @Test fun everyRegisteredTeachingTypeHasAnOfflineShowcaseLesson() {
        // JVM17 的密封类型列表自动包含未来新增类型，防止只实现组件却忘记补入同一系列课。
        assertEquals(TeachingChartStyle.entries.toSet(), BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingChart>().map { it.style }.toSet())
        assertEquals(ComparisonPalette.entries.toSet(), BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingComparison>().map { it.palette }.toSet())
        val required = checkNotNull(TeachingBlock::class.java.permittedSubclasses).toSet()
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
        val timeline = BuiltInTeaching.lessons.first().blocks.single()
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(timeline, TeachingState(), TeachingAction.Select("missing")) }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(timeline, TeachingState(), TeachingAction.Submit) }
        assertEquals("succession", TeachingInteractions.apply(timeline, TeachingState(), TeachingAction.Select("succession")).selection)
        assertEquals(BuiltInTeaching.lessons.size, BuiltInTeaching.lessons.map { it.installMarker }.distinct().size)
    }
}
