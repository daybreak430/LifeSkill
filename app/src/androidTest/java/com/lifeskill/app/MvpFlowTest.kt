package com.lifeskill.app

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MvpFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val fixture get() = ui.activity.application as FixtureApplication

    @Test fun manualLibraryGenerationMapAndTutorWorkThroughTheirScreens() {
        await("本职工作")
        ui.onNodeWithContentDescription("模型设置").performClick()
        await("deepseek-flash")
        ui.onNodeWithText("MiniMax").performClick()
        await("MiniMax-M3.1-Flash")
        fill("API Key", "fixture-key")
        fixture.ai.reply("连接成功")
        ui.onNodeWithText("测试连接").performClick()
        await("连接成功")
        ui.onNodeWithText("保存").performClick()
        await("已保存为当前模型")
        pressBack()
        await("本职工作")
        ui.onNodeWithText("新增方向").performClick()
        fill("名称", "模拟器验证")
        ui.onNodeWithText("保存").performClick()
        await("模拟器验证")
        ui.onNodeWithText("模拟器验证").performClick()
        ui.onNodeWithText("新增技能树").performClick()
        fill("名称", "手动技能树")
        ui.onNodeWithText("保存").performClick()
        await("手动技能树")
        ui.onNodeWithText("手动技能树").performClick()
        ui.onNodeWithText("新增技能点").performClick()
        fill("名称", "手动技能点")
        fill("学习目标", "能解释一个基本概念")
        ui.onNodeWithText("保存").performClick()
        await("手动技能点")
        ui.onNodeWithContentDescription("手动技能点").performTouchInput { click() }
        ui.onNodeWithText("手动点亮").performClick()
        await("已点亮 · 手动")
        pressBack()
        await("1 / 1 已点亮")
        pressBack()
        await("新增技能树")
        pressBack()
        await("AI 生成技能树")

        fixture.ai.enqueue { throw RuleViolation("模型服务请求过于频繁，请稍后重试。") }
        fixture.ai.reply("""{"kind":"message","reply":"你想用什么主题？"}""")
        fixture.ai.reply(RepositoryTest.CREATED)
        ui.onNodeWithText("AI 生成技能树").performClick()
        ui.onNodeWithTag("chat-input").performClick()
        ui.waitUntil(8_000) { imeVisible() }
        ui.onNodeWithContentDescription("发送").assertIsDisplayed()
        pressBack()
        ui.waitUntil(8_000) { !imeVisible() }
        ui.onNodeWithText("AI 生成").assertIsDisplayed()
        send("我想搭一个新技能树")
        await("模型服务请求过于频繁，请稍后重试。")
        ui.onNodeWithText("重试").performClick()
        await("你想用什么主题？")
        send("请生成一个数据质量方向的技能树")
        await("打开技能树 · 数据质量")
        ui.onNodeWithText("打开技能树 · 数据质量").performClick()
        await("技能地图")

        val map = ui.onNodeWithTag("skill-map")
        val root = ui.onNodeWithContentDescription("基础")
        val before = root.fetchSemanticsNode().boundsInRoot
        map.performTouchInput {
            swipe(Offset(width * .22f, height * .62f), Offset(width * .40f, height * .72f))
        }
        val moved = root.fetchSemanticsNode().boundsInRoot
        assertTrue(moved.center.x > before.center.x + 10)
        assertTrue(moved.center.y > before.center.y + 10)
        ui.onNodeWithContentDescription("重置地图视图").performClick()
        map.performTouchInput {
            pinch(
                Offset(width * .42f, height * .17f), Offset(width * .26f, height * .17f),
                Offset(width * .58f, height * .17f), Offset(width * .74f, height * .17f),
            )
        }
        assertTrue(root.fetchSemanticsNode().boundsInRoot.width > before.width)
        ui.onNodeWithContentDescription("重置地图视图").performClick()
        ui.onNodeWithContentDescription("实践").performTouchInput { click() }

        fixture.ai.reply("""{"kind":"exercise","reply":"怎样识别异常记录？"}""")
        ui.onNodeWithText("与 AI 老师学习").performClick()
        ui.onNodeWithText("检验掌握").performClick()
        await("怎样识别异常记录？")
        val exerciseId = runBlocking {
            val practice = fixture.container.library.snapshot().entries.single { it.kind == EntryKind.SKILL && it.name == "实践" }
            fixture.container.conversations.watch(ChatScope(ChatMode.TUTOR, practice.id)).first().last().id
        }
        fixture.ai.reply(RepositoryTest.verdict("passed", "你给出了可核验的标准。", exerciseId))
        send("先核对必填字段和合理范围。")
        await("已点亮技能点", substring = true)
        ui.activityRule.scenario.recreate()
        await("已点亮技能点", substring = true)
        pressBack()
        await("已点亮 · AI 检验通过")
        pressBack()
        await("1 / 2 已点亮")
    }

    private fun send(text: String) {
        ui.onNodeWithTag("chat-input").performTextInput(text)
        ui.onNodeWithContentDescription("发送").performClick()
    }

    private fun fill(label: String, value: String) {
        ui.onNode(hasSetTextAction() and hasText(label)).performTextInput(value)
    }

    private fun await(text: String, substring: Boolean = false) {
        ui.waitUntil(8_000) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun imeVisible(): Boolean = ui.runOnUiThread {
        ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }
}
