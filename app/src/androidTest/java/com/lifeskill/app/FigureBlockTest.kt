package com.lifeskill.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifeskill.app.ai.TeachingBlockProtocol
import com.lifeskill.app.model.BuiltInTeaching
import com.lifeskill.app.model.FigureItem
import com.lifeskill.app.model.FigureRow
import com.lifeskill.app.model.FigureTone
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.TeachingFigure
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

/** 结构图的编解码边界与渲染：列数、节点数、语气色都必须在渲染前校验，坏数据不能拖垮整条消息。 */
@RunWith(AndroidJUnit4::class)
class FigureBlockTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val block = TeachingFigure(
        "fig", "两层结构",
        listOf(
            FigureRow.Nodes(listOf(FigureItem("整体", note = "一句限定", tone = FigureTone.AMBER))),
            FigureRow.Relation("由两部分组成"),
            FigureRow.Nodes(listOf(
                FigureItem("部分一", value = "435", note = "全部改选", tone = FigureTone.BLUE),
                FigureItem("部分二", value = "100", items = listOf("要点一", "要点二")),
            )),
        ),
        caption = "一句话说明关系。",
    )

    @Test fun roundTripKeepsColumnsValuesAndTones() {
        val decoded = TeachingBlockProtocol.decode(TeachingBlockProtocol.encode(listOf(block))).single() as TeachingFigure
        assertEquals(block, decoded)
        val third = decoded.rows[2] as FigureRow.Nodes
        assertEquals(2, third.items.size)
        assertEquals("435", third.items[0].value)
        assertEquals(FigureTone.BLUE, third.items[0].tone)
        assertEquals(listOf("要点一", "要点二"), third.items[1].items)
    }

    @Test fun rejectsTooManyColumnsMismatchedItemsAndUnknownTone() {
        val four = JSONArray("""[{"id":"x","type":"figure","title":"层","rows":[{"cols":3,"items":[
            {"title":"甲"},{"title":"乙"},{"title":"丙"},{"title":"丁"}]}]}]""")
        assertTrue(TeachingBlockProtocol.decode(four).isEmpty())
        val mismatch = JSONArray("""[{"id":"x","type":"figure","title":"层","rows":[{"cols":2,"items":[{"title":"甲"}]}]}]""")
        assertTrue(TeachingBlockProtocol.decode(mismatch).isEmpty())
        val badTone = JSONArray("""[{"id":"x","type":"figure","title":"层","rows":[{"cols":1,"items":[{"title":"甲","tone":"purple"}]}]}]""")
        assertTrue(TeachingBlockProtocol.decode(badTone).isEmpty())
    }

    @Test fun showcaseShipsAFigureInStructureCategory() {
        val item = BuiltInTeaching.lessonByKey("structure")!!.items.single { it.installMarker == 1_023 }
        val figure = item.block as TeachingFigure
        assertEquals(3, figure.rows.size)
        assertEquals(3, (figure.rows[2] as FigureRow.Nodes).items.size)
        assertEquals(4, BuiltInTeaching.lessons.size)
    }

    @Test fun threeColumnsAndCaptionRender() {
        ui.runOnUiThread {
            ui.activity.setContent {
                LifeSkillTheme(dark = false) {
                    TeachingContent(
                        Turn("figure-ui", Speaker.ASSISTANT, TurnKind.MESSAGE, "结构说明", teachingBlocks = listOf(block)),
                        true,
                    ) { _, _ -> }
                }
            }
        }
        ui.onNodeWithTag("figure-fig").assertIsDisplayed()
        ui.onNodeWithText("整体").assertIsDisplayed()
        ui.onNodeWithText("由两部分组成").assertIsDisplayed()
        ui.onNodeWithText("部分一").assertIsDisplayed()
        ui.onNodeWithText("435").assertIsDisplayed()
        ui.onNodeWithText("要点二", substring = true).assertIsDisplayed()
        ui.onNodeWithText("一句话说明关系。").assertIsDisplayed()
    }
}
