package com.lifeskill.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
