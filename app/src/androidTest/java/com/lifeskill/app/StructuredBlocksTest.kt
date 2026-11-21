package com.lifeskill.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifeskill.app.ai.TeachingBlockProtocol
import com.lifeskill.app.model.BuiltInTeaching
import com.lifeskill.app.model.FunnelMode
import com.lifeskill.app.model.FunnelStage
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.TeachingChart
import com.lifeskill.app.model.TeachingChartStyle
import com.lifeskill.app.model.TeachingFormula
import com.lifeskill.app.model.TeachingFunnel
import com.lifeskill.app.model.TeachingMatrix
import com.lifeskill.app.model.TeachingPoint
import com.lifeskill.app.model.TeachingRange
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.ui.LifeSkillTheme
import com.lifeskill.app.ui.TeachingContent
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 四种结构化图形的编解码边界与渲染，坏数据必须整块丢弃而不是画出错图。 */
@RunWith(AndroidJUnit4::class)
class StructuredBlocksTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val matrix = TeachingMatrix(
        "mx", "方案评分", listOf("自建", "采购"), listOf("成本", "风险", "可维护"),
        listOf(listOf(5.0, 3.0, 4.0), listOf(3.0, 5.0, 3.0)), "1–5 分，依据见讲解。",
    )
    private val range = TeachingRange("rg", "上线时间", "预计 3–5 天", 0.0, 10.0, 3.0, 5.0, 4.2, "天", "来自历史耗时。")
    private val funnel = TeachingFunnel(
        "fn", "从投递到录用", FunnelMode.FUNNEL,
        listOf(FunnelStage("投递", "240", 100), FunnelStage("初筛", "96", 78), FunnelStage("录用", "8", 32)),
        "每层相对上一层。",
    )
    private val formula = TeachingFormula("fm", "求根公式", listOf("x = frac{-b ± sqrt{b^{2}-4ac}}{2a}"), "判别式决定实根个数。")
    private val waterfall = TeachingChart(
        "wf", "月活变化", "项目", "万人",
        listOf(
            TeachingPoint("期初", 120.0, total = true),
            TeachingPoint("新客", 40.0),
            TeachingPoint("流失", -25.0),
            TeachingPoint("期末", 135.0, total = true),
        ),
        style = TeachingChartStyle.WATERFALL,
    )
    private val scatter = TeachingChart(
        "sc", "时长与得分", "每周时长（小时）", "得分",
        listOf(TeachingPoint("1班", 52.0, 1.0), TeachingPoint("2班", 61.0, 3.0), TeachingPoint("3班", 70.0, 5.0)),
        style = TeachingChartStyle.SCATTER,
    )

    @Test fun roundTripKeepsAllFourStructures() {
        val decoded = TeachingBlockProtocol.decode(
            TeachingBlockProtocol.encode(listOf(matrix, range, funnel, formula, scatter, waterfall)),
        )
        assertEquals(6, decoded.size)
        assertEquals(matrix, decoded[0])
        assertEquals(range, decoded[1])
        assertEquals(funnel, decoded[2])
        assertEquals(formula, decoded[3])
        assertEquals(scatter, decoded[4])
        assertEquals(waterfall, decoded[5])
    }

    @Test fun rejectsWrongShapesAndDirections() {
        val ragged = JSONArray("""[{"id":"x","type":"matrix","title":"矩阵","rows":["甲","乙"],"columns":["一","二"],"values":[[5,3],[3]]}]""")
        assertTrue(TeachingBlockProtocol.decode(ragged).isEmpty())
        val invertedRange = JSONArray("""[{"id":"x","type":"range","title":"区间","label":"范围","min":0,"max":10,"low":8,"high":3}]""")
        assertTrue(TeachingBlockProtocol.decode(invertedRange).isEmpty())
        val upwardFunnel = JSONArray("""[{"id":"x","type":"funnel","title":"漏斗","mode":"funnel","stages":[{"label":"甲","ratio":40},{"label":"乙","ratio":70}]}]""")
        assertTrue(TeachingBlockProtocol.decode(upwardFunnel).isEmpty())
        val missingX = JSONArray("""[{"id":"x","type":"chart","title":"散点","style":"scatter","xLabel":"x","yLabel":"y","points":[{"label":"甲","value":3},{"label":"乙","value":5}]}]""")
        assertTrue(TeachingBlockProtocol.decode(missingX).isEmpty())
    }

    @Test fun showcaseShipsTheNewExamples() {
        val structure = BuiltInTeaching.lessonByKey("structure")!!
        assertTrue(structure.items.any { it.installMarker == 1_027 })
        val data = BuiltInTeaching.lessonByKey("data")!!
        listOf(1_024, 1_025, 1_026).forEach { marker ->
            assertTrue(data.items.any { it.installMarker == marker })
        }
        assertEquals(4, BuiltInTeaching.lessons.size)
    }

    @Test fun allStructuresRender() {
        ui.runOnUiThread {
            ui.activity.setContent {
                LifeSkillTheme(dark = false) {
                    TeachingContent(
                        Turn(
                            "structured-ui", Speaker.ASSISTANT, TurnKind.MESSAGE, "四种图形",
                            teachingBlocks = listOf(matrix, range, funnel, formula, scatter),
                        ),
                        true,
                    ) { _, _ -> }
                }
            }
        }
        ui.onNodeWithTag("matrix-mx").assertIsDisplayed()
        ui.onNodeWithText("可维护").assertExists()
        ui.onNodeWithTag("range-rg").assertExists()
        ui.onNodeWithText("预计 3–5 天").assertExists()
        ui.onNodeWithTag("funnel-fn").assertExists()
        ui.onNodeWithText("投递").assertExists()
        ui.onNodeWithTag("formula-fm").assertExists()
        ui.onNodeWithTag("teaching-chart-plot").assertExists()
    }
}
