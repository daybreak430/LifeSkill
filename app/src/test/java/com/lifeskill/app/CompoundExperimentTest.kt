package com.lifeskill.app

import com.lifeskill.app.ai.TeachingBlockProtocol
import com.lifeskill.app.model.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class CompoundExperimentTest {
    private val block = TeachingExperiment(
        "growth", "复利实验台", "调整每月投入与年限，观察期末总额怎么变。", ExperimentModel.COMPOUND,
    )

    @Test fun defaultsProduceTotalInvestedAndGrowth() {
        val observation = ExperimentMath.observe(block.initial)
        assertEquals(3, observation.metrics.size)
        assertTrue(observation.headline.isNotBlank())
        assertNotNull(observation.investedShare)
        // 复利终值必然高于累计投入，收益为正。
        assertTrue(observation.investedShare!! in 0f..1f)
        assertTrue(observation.metrics.first { it.label == "收益" }.value.isNotBlank())
    }

    @Test fun higherRateAndLongerYearsIncreaseTheTotal() {
        val base = ExperimentMath.observe(block.initial).headline
        val richer = ExperimentMath.update(block.initial, "rate", 12.0)
        assertNotEquals(base, ExperimentMath.observe(richer).headline)
        val longer = ExperimentMath.update(block.initial, "years", 20.0)
        assertNotEquals(base, ExperimentMath.observe(longer).headline)
    }

    @Test fun actionsMergePerFieldAndRejectValuesOffTheStep() {
        val first = TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetExperimentValue("monthly", 3000.0))
        val second = TeachingInteractions.apply(block, first, TeachingAction.SetExperimentValue("years", 20.0))
        assertEquals(3000.0, second.experiment!!.value("monthly"), 0.0)
        assertEquals(20.0, second.experiment.value("years"), 0.0)
        assertEquals(block.model, second.experiment.model)
        listOf(Double.NaN, -1.0, 5_001.0, 2_350.0).forEach { value ->
            assertThrows(RuleViolation::class.java) {
                TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetExperimentValue("monthly", value))
            }
        }
    }
}
