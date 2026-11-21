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

        val classification = BuiltInTeaching.lessons.flatMap { it.blocks }
            .filterIsInstance<TeachingClassification>().single()
        var assigned = TeachingState()
        classification.items.forEach { item ->
            assigned = TeachingInteractions.apply(classification, assigned,
                TeachingAction.Classify(item.id, classification.categories.first().id))
        }
        val compared = TeachingInteractions.apply(classification, assigned, TeachingAction.Submit)
        assertFalse(compared.classificationComplete(classification))
        assertEquals(1, compared.attempts)
        assertTrue(TeachingInteractions.apply(classification, compared, TeachingAction.Reveal).revealed)
    }

    @Test fun hotspotAndStepTraceExposeAnswersOnlyThroughExplicitActions() {
        val hotspot = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingHotspot>().single()
        val selected = TeachingInteractions.apply(hotspot, TeachingState(), TeachingAction.Select("nucleus"))
        assertEquals("nucleus", selected.selection)
        assertTrue(TeachingInteractions.apply(hotspot, selected, TeachingAction.Reveal).revealed)

        val trace = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingStepTrace>().single()
        val first = trace.steps.first()
        val firstChoice = TeachingInteractions.apply(trace, TeachingState(),
            TeachingAction.Select(first.checkpoint.answer.toString()))
        val firstSubmitted = TeachingInteractions.apply(trace, firstChoice, TeachingAction.Submit)
        assertTrue(firstSubmitted.currentStepCorrect(trace))
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
