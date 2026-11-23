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
import com.lifeskill.app.model.ExperimentModel
import com.lifeskill.app.model.TeachingExperiment
import com.lifeskill.app.model.TeachingState
import com.lifeskill.app.ui.LifeSkillTheme
import com.lifeskill.app.ui.TeachingExperimentContent
import org.junit.Rule
import org.junit.Test

// 参数实验台的第二个模型：拖动参数后看到期末总额与收益占比变化。
class CompoundExperimentRenderTest {
    @get:Rule
    val ui = createComposeRule()

    private val block = TeachingExperiment(
        "growth", "复利实验台",
        "调整本金、每月投入、年化收益和年限，观察期末总额与收益占比怎么变。",
        ExperimentModel.COMPOUND,
    )

    @Test fun compoundModelShowsHeadlineMetricsAndFourSliders() {
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFFFCFDFD)) {
                    TeachingExperimentContent(block, TeachingState(), true, {}, false)
                }
            }
        }
        ui.onNodeWithText("期末总额").assertIsDisplayed()
        ui.onNodeWithTag("experiment-headline").assertIsDisplayed()
        ui.onNodeWithText("累计投入").assertIsDisplayed()
        listOf("principal", "monthly", "rate", "years").forEach { id ->
            ui.onNodeWithTag("experiment-$id").assertIsDisplayed()
        }
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("compound-experiment.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }
}
