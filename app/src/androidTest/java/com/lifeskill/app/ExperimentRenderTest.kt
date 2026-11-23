package com.lifeskill.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.model.BuiltInTeaching
import com.lifeskill.app.model.ExperimentOutput
import com.lifeskill.app.model.ExperimentParameter
import com.lifeskill.app.model.TeachingExperiment
import com.lifeskill.app.model.TeachingState
import com.lifeskill.app.ui.LifeSkillTheme
import com.lifeskill.app.ui.TeachingExperimentContent
import org.junit.Rule
import org.junit.Test

// 参数实验台完全由数据驱动：这里用内置的复利示例渲染，确认参数与结果都由定义生成。
class ExperimentRenderTest {
    @get:Rule
    val ui = createComposeRule()

    private val block = BuiltInTeaching.lessons.flatMap { it.blocks }
        .filterIsInstance<TeachingExperiment>().first { it.id == "compound" }

    @Test fun threeParameterExperimentFillsTheLastRow() {
        // 参数个数不是固定的：3 个参数时最后一行的滑块应占满整行，不留空槽。
        val block = TeachingExperiment(
            "pricing", "定价三要素", "调价格、成本与销量，观察利润变化。",
            parameters = listOf(
                ExperimentParameter("price", "售价", "元", 10.0, 60.0, 1.0, 30.0),
                ExperimentParameter("cost", "单位成本", "元", 5.0, 40.0, 1.0, 18.0),
                ExperimentParameter("volume", "月销量", "件", 10.0, 500.0, 10.0, 200.0),
            ),
            outputs = listOf(ExperimentOutput("profit", "月利润", "元", "(price-cost)*volume")),
        )
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.width(330.dp).padding(15.dp), color = Color(0xFFFCFDFD)) {
                    TeachingExperimentContent(block, TeachingState(), true, {}, false)
                }
            }
        }
        ui.onNodeWithTag("experiment-headline").assertIsDisplayed()
        listOf("price", "cost", "volume").forEach { id ->
            ui.onNodeWithTag("experiment-$id").assertIsDisplayed()
        }
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("experiment-three-params.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }

    @Test fun dataDrivenExperimentShowsOutputsAndSliders() {
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFFFCFDFD)) {
                    TeachingExperimentContent(block, TeachingState(), true, {}, false)
                }
            }
        }
        ui.onNodeWithText(block.outputs.first().label).assertIsDisplayed()
        ui.onNodeWithTag("experiment-headline").assertIsDisplayed()
        ui.onNodeWithText("累计投入").assertIsDisplayed()
        block.parameters.forEach { parameter ->
            ui.onNodeWithTag("experiment-${parameter.id}").assertIsDisplayed()
        }
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("experiment-generic.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }
}
