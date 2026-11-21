package com.lifeskill.app

import com.lifeskill.app.model.*
import org.junit.Assert.*
import org.junit.Test

class LeverExperimentTest {
    private val block = TeachingExperiment("lever", "杠杆", "调节右侧力臂")

    @Test fun directionAndBalanceFollowForceTimesHorizontalArm() {
        assertEquals(LeverBalance.LEFT_DOWN, LeverPhysics.observe(block.initial).balance)
        val balanced = block.initial.copy(rightArm = 4.0)
        val result = LeverPhysics.observe(balanced)
        assertEquals(8.0, result.leftMoment, 0.0)
        assertEquals(8.0, result.rightMoment, 0.0)
        assertEquals(LeverBalance.BALANCED, result.balance)
        assertEquals(0f, result.illustrationAngle, 0f)
        assertEquals(LeverBalance.RIGHT_DOWN, LeverPhysics.observe(balanced.copy(rightForce = 3.0)).balance)
        assertTrue(LeverPhysics.observe(block.initial).illustrationAngle < 0f)
        assertTrue(LeverPhysics.observe(balanced.copy(rightForce = 3.0)).illustrationAngle > 0f)
    }

    @Test fun fieldActionsMergeWithoutOverwritingOtherParametersAndResetToInitial() {
        val first = TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetLeverParameter(LeverParameter.LEFT_FORCE, 6.0))
        val second = TeachingInteractions.apply(block, first, TeachingAction.SetLeverParameter(LeverParameter.RIGHT_ARM, 4.0))
        assertEquals(6.0, second.lever!!.leftForce, 0.0)
        assertEquals(4.0, second.lever.rightArm, 0.0)
        assertEquals(TeachingState(), TeachingInteractions.apply(block, second, TeachingAction.Reset))
        assertFalse(second.submitted)
        assertEquals(0, second.attempts)
    }

    @Test fun invalidNumbersRangesAndStepsNeverEnterSavedState() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 11.0, 2.5).forEach { value ->
            assertThrows(RuleViolation::class.java) {
                TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetLeverParameter(LeverParameter.LEFT_FORCE, value))
            }
        }
        listOf(.5, 4.5, 1.1, Double.NEGATIVE_INFINITY).forEach { value ->
            assertThrows(RuleViolation::class.java) {
                TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetLeverParameter(LeverParameter.RIGHT_ARM, value))
            }
        }
        assertFalse(LeverPhysics.isValid(LeverParameters(leftForce = Double.NaN)))
    }

    @Test fun templateAcceptsBoundaryValuesAndSnapsPreviewToSupportedSteps() {
        assertTrue(LeverPhysics.isValid(LeverParameters(1.0, 10.0, 1.0, 4.0)))
        assertEquals(1.5, LeverPhysics.snap(LeverParameter.LEFT_ARM, 1.6), 0.0)
        assertEquals(4.0, LeverPhysics.snap(LeverParameter.LEFT_ARM, 100.0), 0.0)
        assertEquals(1.0, LeverPhysics.snap(LeverParameter.LEFT_FORCE, -10.0), 0.0)
    }

    @Test fun actionsAreBoundToExperimentTypeAndCannotSubmitAnAssessment() {
        val text = TeachingText("text", "文字", "说明")
        assertThrows(RuleViolation::class.java) {
            TeachingInteractions.apply(text, TeachingState(), TeachingAction.SetLeverParameter(LeverParameter.LEFT_FORCE, 4.0))
        }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(block, TeachingState(), TeachingAction.Submit) }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(block, TeachingState(), TeachingAction.Reveal) }
    }

    @Test fun markdownAndHistoryDescribeTheActualExperimentAndItsLimits() {
        assertTrue(block.markdown().contains("倾角仅作方向示意"))
        assertTrue(block.interactionSummary(TeachingState(lever = block.initial.copy(rightArm = 4.0))).contains("已平衡"))
        assertTrue(block.interactionSummary(TeachingState()).contains("左侧下沉"))
    }
}
