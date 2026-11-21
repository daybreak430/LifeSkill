package com.lifeskill.app

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifeskill.app.ai.TeachingBlockProtocol
import com.lifeskill.app.model.BuiltInTeaching
import com.lifeskill.app.model.QuadrantPoint
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.TeachingAction
import com.lifeskill.app.model.TeachingChart
import com.lifeskill.app.model.TeachingChartStyle
import com.lifeskill.app.model.TeachingInteractions
import com.lifeskill.app.model.TeachingPoint
import com.lifeskill.app.model.TeachingQuadrant
import com.lifeskill.app.model.TeachingState
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.ui.LifeSkillTheme
import com.lifeskill.app.ui.TeachingContent
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** 瀑布图的累计校验与四象限的坐标状态，包括拖动后落库的坐标边界。 */
@RunWith(AndroidJUnit4::class)
class WaterfallQuadrantTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val waterfall = TeachingChart(
        "wf", "月活变化", "项目", "万人",
        listOf(
            TeachingPoint("期初", 120.0, total = true),
            TeachingPoint("新客", 40.0),
            TeachingPoint("流失", -25.0),
            TeachingPoint("期末", 135.0, total = true),
        ), style = TeachingChartStyle.WATERFALL,
    )
    private val quadrant = TeachingQuadrant(
        "qd", "需求优先级", "用户价值", "实现成本",
        listOf("不做", "值得谈", "有空再说", "先做"),
        listOf(
            QuadrantPoint("login", "登录改版", 82.0, 30.0, "价值高、成本低"),
            QuadrantPoint("sync", "多端同步", 64.0, 76.0, "价值高，但要改架构"),
        ),
        "拖动点位即可改判。",
    )

    @Test fun waterfallAndQuadrantSurviveRoundTrip() {
        val decoded = TeachingBlockProtocol.decode(TeachingBlockProtocol.encode(listOf(waterfall, quadrant)))
        assertEquals(2, decoded.size)
        assertEquals(waterfall, decoded[0])
        assertEquals(quadrant, decoded[1])
    }

    @Test fun rejectsWaterfallWithoutTotalsAndBadQuadrants() {
        val noTotals = JSONArray(
            """[{"id":"x","type":"chart","title":"瀑布","style":"waterfall","xLabel":"项","yLabel":"值",
            "points":[{"label":"甲","value":10},{"label":"乙","value":5},{"label":"丙","value":8}]}]""",
        )
        assertTrue(TeachingBlockProtocol.decode(noTotals).isEmpty())
        val threeQuadrants = JSONArray(
            """[{"id":"x","type":"quadrant","title":"象限","xLabel":"x","yLabel":"y",
            "quadrants":["甲","乙","丙"],"points":[{"id":"a","label":"A","x":10,"y":20}]}]""",
        )
        assertTrue(TeachingBlockProtocol.decode(threeQuadrants).isEmpty())
        val duplicateIds = JSONArray(
            """[{"id":"x","type":"quadrant","title":"象限","xLabel":"x","yLabel":"y",
            "quadrants":["甲","乙","丙","丁"],"points":[{"id":"a","label":"A","x":10,"y":20},{"id":"a","label":"B","x":30,"y":40}]}]""",
        )
        assertTrue(TeachingBlockProtocol.decode(duplicateIds).isEmpty())
        val outOfRange = JSONArray(
            """[{"id":"x","type":"quadrant","title":"象限","xLabel":"x","yLabel":"y",
            "quadrants":["甲","乙","丙","丁"],"points":[{"id":"a","label":"A","x":120,"y":20}]}]""",
        )
        assertTrue(TeachingBlockProtocol.decode(outOfRange).isEmpty())
    }

    @Test fun placeActionStoresCoordinatesAndRejectsOutOfRange() {
        val moved = TeachingInteractions.apply(quadrant, TeachingState(), TeachingAction.Place("login", 41.0, 62.0))
        assertEquals(listOf(41.0, 62.0), moved.positions.getValue("login"))
        assertThrows(Exception::class.java) {
            TeachingInteractions.apply(quadrant, moved, TeachingAction.Place("login", 101.0, 50.0))
        }
        assertThrows(Exception::class.java) {
            TeachingInteractions.apply(quadrant, moved, TeachingAction.Place("missing", 10.0, 10.0))
        }
        val encoded = TeachingBlockProtocol.encodeStates(mapOf("qd" to moved))
        assertEquals(listOf(41.0, 62.0), TeachingBlockProtocol.decodeStates(encoded).getValue("qd").positions.getValue("login"))
    }

    @Test fun bothBlocksRenderAndDraggingSubmitsCoordinates() {
        val captured = AtomicReference<Pair<String, TeachingAction>?>(null)
        ui.runOnUiThread {
            ui.activity.setContent {
                LifeSkillTheme(dark = false) {
                    TeachingContent(
                        Turn(
                            "wf-qd", Speaker.ASSISTANT, TurnKind.MESSAGE, "两种图形",
                            teachingBlocks = listOf(waterfall, quadrant),
                        ),
                        true,
                    ) { id, action -> captured.set(id to action) }
                }
            }
        }
        ui.onNodeWithTag("waterfall-wf").assertExists()
        // 命中 (82,30) 的数据点：与渲染一致，左 34dp、右 12dp、上 14dp、下 26dp。
        ui.onNodeWithTag("quadrant-qd").performTouchInput {
            val left = 34f * density
            val right = 12f * density
            val top = 14f * density
            val bottom = 26f * density
            val startX = left + (width - left - right) * .82f
            val startY = height - bottom - (height - top - bottom) * .30f
            down(Offset(startX, startY))
            advanceEventTime(16)
            moveTo(Offset(startX - 20f, startY + 30f))
            advanceEventTime(16)
            moveTo(Offset(startX - 80f, startY + 120f))
            advanceEventTime(16)
            up()
        }
        ui.waitForIdle()
        val action = captured.get()?.second
        assertTrue("应提交坐标", action is TeachingAction.Place)
        val place = action as TeachingAction.Place
        assertTrue(place.x in 0.0..100.0 && place.y in 0.0..100.0)
    }

    @Test fun showcaseShipsBothNewVisuals() {
        val data = BuiltInTeaching.lessonByKey("data")!!
        assertTrue(data.items.any { it.installMarker == 1_029 })
        val structure = BuiltInTeaching.lessonByKey("structure")!!
        assertTrue(structure.items.any { it.installMarker == 1_030 })
    }
}
