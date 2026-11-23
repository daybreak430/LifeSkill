package com.lifeskill.app

import com.lifeskill.app.model.*
import org.junit.Assert.*
import org.junit.Test

class ExperimentProtocolTest {
    private val block = TeachingExperiment(
        "growth", "复利实验台", "调整参数，观察结果。",
        parameters = listOf(
            ExperimentParameter("principal", "本金", "元", 10_000.0, 200_000.0, 5_000.0, 50_000.0),
            ExperimentParameter("rate", "年化收益", "%", 1.0, 15.0, .5, 8.0, decimals = 1),
            ExperimentParameter("years", "年限", "年", 1.0, 30.0, 1.0, 10.0),
        ),
        outputs = listOf(
            ExperimentOutput("total", "期末总额", "元", "principal*pow(1+rate/100,years)"),
            ExperimentOutput("gain", "收益", "元", "total-principal"),
        ),
    )

    @Test fun expressionsRespectPrecedenceFunctionsAndPreviousOutputs() {
        val values = ExperimentValues(mapOf("principal" to 100_000.0, "rate" to 10.0, "years" to 2.0))
        val results = ExperimentMath.evaluate(block, values)
        assertEquals(121_000.0, results[0], 0.5)
        assertEquals(21_000.0, results[1], 0.5)
    }

    @Test fun expressionRejectsUnknownNamesFunctionsAndBadSyntax() {
        listOf(
            "principal*missing",
            "principal*foo(2)",
            "principal+",
            "(principal*2",
            "principal; system(1)",
            "principal/0",
        ).forEach { expression ->
            assertThrows(IllegalArgumentException::class.java) {
                ExperimentExpression.evaluate(expression, mapOf("principal" to 1.0))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExperimentExpression.evaluate("9".repeat(301), emptyMap())
        }
    }

    @Test fun interactionMergesOneFieldAndRejectsOffStepValues() {
        val first = TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetExperimentValue("years", 20.0))
        val second = TeachingInteractions.apply(block, first, TeachingAction.SetExperimentValue("principal", 80_000.0))
        assertEquals(20.0, second.experiment!!.value("years"), 0.0)
        assertEquals(80_000.0, second.experiment.value("principal"), 0.0)
        listOf(Double.NaN, 5_000.0, 250_000.0, 82_500.0).forEach { value ->
            assertThrows(RuleViolation::class.java) {
                TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetExperimentValue("principal", value))
            }
        }
        assertThrows(RuleViolation::class.java) {
            TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetExperimentValue("missing", 1.0))
        }
        assertEquals(TeachingState(), TeachingInteractions.apply(block, second, TeachingAction.Reset))
    }

    @Test fun singleParameterExperimentIsSupported() {
        // 只有一个可调量的主题也应该能用：滑块占满整行，结果照常计算。
        val block = TeachingExperiment(
            "rate-only", "利率与月供", "拖动利率，观察月供变化。",
            parameters = listOf(ExperimentParameter("rate", "年利率", "%", 3.0, 8.0, .1, 5.0, decimals = 1)),
            outputs = listOf(ExperimentOutput("pay", "月供", "元", "100000*rate/100/12+2000")),
        )
        assertEquals(2_416.67, ExperimentMath.evaluate(block, block.initial)[0], 0.5)
        assertEquals("2417 元", ExperimentMath.observe(block, block.initial).headline)
        val updated = TeachingInteractions.apply(block, TeachingState(), TeachingAction.SetExperimentValue("rate", 6.0))
        assertEquals(6.0, updated.experiment!!.value("rate"), 0.0)
    }

    @Test fun builtInExamplesCoverLeverAndCompound() {
        val examples = BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingExperiment>()
        assertEquals(2, examples.size)
        examples.forEach { example ->
            assertTrue(example.parameters.size in 2..4)
            assertTrue(example.outputs.size in 1..3)
            // 默认参数必须能算出全部结果，否则内置示例会在渲染时抛错。
            ExperimentMath.evaluate(example, example.initial)
            assertTrue(ExperimentMath.summary(example, example.initial).isNotBlank())
        }
        // 复利示例：本金 5 万 + 每月 2000、年化 8%、10 年，期末应约 47 万而不是 450 万。
        val compound = examples.first { it.id == "compound" }
        val compoundResult = ExperimentMath.evaluate(compound, compound.initial)
        assertEquals(473_800.0, compoundResult[0], 2_000.0)
        assertEquals(290_000.0, compoundResult[1], 0.0)
        assertEquals(183_800.0, compoundResult[2], 2_000.0)

        val lever = examples.first { it.id == "lever" }
        val balanced = ExperimentValues(mapOf("leftForce" to 4.0, "rightForce" to 2.0, "leftArm" to 2.0, "rightArm" to 4.0))
        assertEquals("0 N·m", ExperimentMath.observe(lever, balanced).headline)
    }
}
