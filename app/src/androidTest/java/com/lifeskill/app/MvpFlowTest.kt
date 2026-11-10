package com.lifeskill.app

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.AppearanceMode
import com.lifeskill.app.data.AppearanceSettings
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class MvpFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val fixture get() = ui.activity.application as FixtureApplication

    @Test fun appearanceSelectionUpdatesSystemBarsAndSavesLocally() {
        val settings = AppearanceSettings(ui.activity.applicationContext)
        val original = settings.load()
        try {
            await("本职工作")
            ui.onNodeWithContentDescription("设置").performClick()
            await("外观")
            ui.onNodeWithText("浅色").performClick().assertIsSelected()
            assertEquals(AppearanceMode.LIGHT, settings.load())
            assertTrue(lightStatusBar())
            ui.onNodeWithText("深色").performClick().assertIsSelected()
            assertEquals(AppearanceMode.DARK, settings.load())
            assertTrue(!lightStatusBar())
            ui.activityRule.scenario.recreate()
            await("外观")
            ui.onNodeWithText("深色").assertIsSelected()
            ui.onNodeWithText("自动").performClick().assertIsSelected()
            assertEquals(AppearanceMode.AUTO, settings.load())
        } finally {
            settings.save(original)
        }
    }

    @Test fun chatKeepsComposerVisibleWithKeyboardAndBackClosesItFirst() {
        await("AI 生成技能树")
        ui.onNodeWithText("AI 生成技能树").performClick()
        await("AI 生成")
        ui.onNodeWithTag("chat-input").performTouchInput { click() }
        ui.waitUntil(8_000) { imeVisible() }
        ui.onNodeWithContentDescription("发送").assertIsDisplayed()
        pressBack()
        ui.waitUntil(8_000) { !imeVisible() }
        ui.onNodeWithText("AI 生成").assertIsDisplayed()
    }

    @Test fun homeResumesLatestLessonAndTreeCardsStayUsableWhileScrolling() {
        await("本职工作")
        val library = fixture.container.library
        val direction = runBlocking { library.save(EntryEdit(null, null, EntryKind.DIRECTION, "研习方向")) }
        val fillers = runBlocking {
            List(16) { index -> library.save(EntryEdit(null, null, EntryKind.DIRECTION, "方向卡片 ${index + 1}")) }
        }
        try {
            val skill = runBlocking {
                val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, "续学技能树"))
                val skill = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "课程回顾", "掌握复习方法"))
                repeat(12) { index ->
                    library.save(EntryEdit(null, direction.id, EntryKind.TREE, "技能树卡片 ${index + 1}"))
                }
                fixture.ai.reply("""{"kind":"teach","reply":"先梳理已学的要点。"}""")
                fixture.container.conversations.send(ChatScope(ChatMode.TUTOR, skill.id), "开始复习")
                skill
            }
            await("继续学习")
            ui.onNodeWithText("继续学习").performTouchInput { click() }
            await("先梳理已学的要点。")
            ui.activityRule.scenario.recreate()
            await("先梳理已学的要点。")
            pressBack()
            await("继续学习")
            val grid = ui.onNodeWithTag("learning-directions")
            grid.performTouchInput {
                swipe(Offset(width * .86f, height * .61f), Offset(width * .13f, height * .61f))
            }
            ui.onNodeWithText("继续学习").assertIsDisplayed()
            grid.performTouchInput {
                swipe(Offset(width * .35f, height * .87f), Offset(width * .35f, height * .24f))
            }
            ui.onNodeWithText("继续学习").assertIsNotDisplayed()
            grid.performScrollToIndex(0)
            ui.onNodeWithText("研习方向").performTouchInput { click() }
            await("上次学习")
            ui.onNodeWithText("续学技能树").assertIsDisplayed()
            val trees = ui.onNodeWithTag("skill-tree-list")
            trees.performTouchInput {
                swipe(Offset(width * .50f, height * .89f), Offset(width * .50f, height * .17f))
            }
            ui.onNodeWithText("续学技能树").assertIsNotDisplayed()
            trees.performScrollToIndex(14)
            ui.onNodeWithText("技能树卡片 12").performTouchInput { click() }
            await("新增技能点")
            pressBack()
            ui.onNodeWithText("技能树卡片 12").assertIsDisplayed()
            pressBack()
            runBlocking { library.delete(skill.id) }
            ui.waitUntil(8_000) {
                ui.onAllNodesWithText("课程回顾").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            runBlocking {
                library.delete(direction.id)
                fillers.forEach { library.delete(it.id) }
            }
        }
    }

    @Test fun conflictingDependenciesRemainVisibleInTheReferenceList() {
        await("本职工作")
        runBlocking {
            val library = fixture.container.library
            val direction = library.snapshot().childrenOf(null).first { it.name == "本职工作" }
            val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, "连线分支"))
            val left = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "左起点", "基础"))
            val right = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "右起点", "基础"))
            val parents = setOf(left.id, right.id)
            library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "左下游", "综合应用", parents))
            library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "右下游", "综合应用", parents))
        }
        ui.onNodeWithText("本职工作").performClick()
        await("连线分支")
        ui.onNodeWithText("连线分支").performClick()
        await("关联 1")
        ui.onNodeWithText("关联 1").performClick()
        await("前置关联")
        await("右起点 → 左下游", substring = true)
        ui.onNodeWithText("关闭").performClick()
        ui.onNodeWithContentDescription("左下游").performTouchInput { click() }
        await("建议先学")
        await("右起点")
        await("左起点")
    }

    @Test fun manualLibraryGenerationMapAndTutorWorkThroughTheirScreens() {
        await("本职工作")
        ui.onNodeWithContentDescription("设置").performClick()
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
        val nameInput = ui.onNode(hasSetTextAction() and hasText("名称"))
        nameInput.performTextInput("测".repeat(49))
        await("名称最多 48 个字符")
        ui.onNodeWithText("保存").assertIsNotEnabled()
        nameInput.performTextReplacement("模拟器验证")
        ui.onNodeWithText("选择图标").performClick()
        ui.onNodeWithText("阅读").performClick()
        ui.onNodeWithText("保存").performClick()
        await("模拟器验证")
        assertEquals(EntryIcon.BOOK, iconOf("模拟器验证"))
        ui.onNodeWithText("模拟器验证").performClick()
        ui.onNodeWithText("新增技能树").performClick()
        fill("名称", "手动技能树")
        fixture.ai.reply("""{"icon":"CODE"}""")
        ui.onNodeWithText("AI 推荐图标").performClick()
        await("编程")
        ui.onNodeWithText("保存").performClick()
        await("手动技能树")
        assertEquals(EntryIcon.CODE, iconOf("手动技能树"))
        ui.onNodeWithText("手动技能树").performClick()
        ui.onNodeWithText("新增技能点").performClick()
        fill("名称", "手动技能点")
        fill("技能说明", "能解释一个基本概念")
        fill("节点简称（选填）", "基础课")
        ui.onNodeWithText("保存").performClick()
        await("基础课")
        ui.onNodeWithContentDescription("手动技能点").performTouchInput { click() }
        ui.onNodeWithText("手动点亮").performClick()
        await("已点亮 · 手动")
        ui.onNodeWithText("取消点亮").performClick()
        await("待学习")
        ui.onNodeWithText("手动点亮").performClick()
        await("已点亮 · 手动")
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("编辑技能点").performClick()
        ui.onNode(hasSetTextAction() and hasText("名称")).performTextReplacement("已编辑技能点")
        ui.onNodeWithText("保存").performClick()
        await("已编辑技能点")
        ui.onNodeWithContentDescription("关闭技能详情").performClick()
        await("1 / 1 已点亮")
        ui.onNodeWithText("新增技能点").performClick()
        fill("名称", "练习依赖")
        val goalInput = ui.onNode(hasSetTextAction() and hasText("技能说明"))
        goalInput.performTextInput("练".repeat(321))
        await("技能说明最多 320 个字符")
        ui.onNodeWithText("保存").assertIsNotEnabled()
        goalInput.performTextReplacement("使用前置概念完成一项练习")
        ui.onNodeWithText("前置节点").performClick()
        ui.onNode(hasText("已编辑技能点") and isToggleable()).performClick()
        ui.onNodeWithText("保存").performClick()
        await("1 / 2 已点亮")
        ui.onNodeWithContentDescription("练习依赖").performTouchInput { click() }
        await("建议先学")
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("删除技能点").performClick()
        ui.onNodeWithText("取消").performClick()
        await("建议先学")
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("删除技能点").performClick()
        ui.onNodeWithText("删除").performClick()
        await("1 / 1 已点亮")
        pressBack()
        await("新增技能树")
        pressBack()
        await("AI 生成技能树")

        fixture.ai.enqueue { throw RuleViolation("模型服务请求过于频繁，请稍后重试。") }
        fixture.ai.reply("""{"kind":"message","reply":"你想用什么主题？"}""")
        fixture.ai.reply(RepositoryTest.CREATED)
        ui.onNodeWithText("AI 生成技能树").performClick()
        await("AI 生成")
        ui.onNodeWithTag("chat-input").performTextInput("测".repeat(4001))
        await("每条消息最多 4000 个字符")
        ui.onNodeWithContentDescription("发送").assertIsNotEnabled()
        send("我想搭一个新技能树")
        await("我想搭一个新技能树")
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
        val pivot = root.fetchSemanticsNode().boundsInRoot.center - map.fetchSemanticsNode().boundsInRoot.topLeft
        map.performTouchInput {
            val inner = Offset(width * .06f, 0f)
            val outer = Offset(width * .10f, 0f)
            pinch(
                pivot - inner, pivot - outer,
                pivot + inner, pivot + outer,
            )
        }
        assertTrue(root.fetchSemanticsNode().boundsInRoot.width > before.width)
        ui.onNodeWithContentDescription("重置地图视图").performClick()
        ui.onNodeWithContentDescription("实践").performTouchInput { click() }

        ui.onNodeWithText("学习技能").performClick()
        val fence = "```"
        val lesson = "## 数据核对 💡\n\n| 对象 | 特点 |\n| --- | --- |\n| 文本 | 原样 |\n\n" +
            "${fence}mermaid\nflowchart TD\nA{读取} --> B[核对]\n$fence"
        fixture.ai.reply(JSONObject().put("kind", "teach").put("reply", lesson).toString())
        ui.onNodeWithText("开始学习").performClick()
        await("数据核对 💡")
        await("原样")
        await("读取")
        fixture.ai.reply("""{"kind":"exercise","reply":"怎样识别异常记录？","choices":["直接删除","核对必填字段","全部保留"]}""")
        ui.onNodeWithText("检验掌握").performClick()
        await("怎样识别异常记录？")
        val exerciseId = runBlocking {
            val practice = fixture.container.library.snapshot().entries.single { it.kind == EntryKind.SKILL && it.name == "实践" }
            fixture.container.conversations.watch(ChatScope(ChatMode.TUTOR, practice.id)).first().last().id
        }
        fixture.ai.enqueue { messages ->
            assertEquals("B. 核对必填字段", messages.last().text)
            assertTrue(messages.first().text.contains("A. 直接删除"))
            RepositoryTest.verdict("passed", "你给出了可核验的标准。", exerciseId)
        }
        ui.onNodeWithText("B. 核对必填字段").performClick()
        await("已点亮技能点", substring = true)
        ui.activityRule.scenario.recreate()
        await("已点亮技能点", substring = true)
        pressBack()
        await("已点亮 · AI 检验通过")
        fixture.ai.enqueue { messages ->
            assertEquals(2, messages.size)
            assertTrue(messages.last().text.contains("改用账单举例"))
            """{"kind":"teach","reply":"从核对账单开始讲。"}"""
        }
        ui.onNodeWithText("重新学习").performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("开始新一轮")
        ui.onNodeWithTag("restart-requirements").performTextInput("改用账单举例")
        ui.onNodeWithText("开始新一轮").performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("新一轮学习")
        await("从核对账单开始讲。")
        ui.onNodeWithContentDescription("重新学习").performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("开始新一轮")
        ui.onNodeWithText("取消").performClick()
        await("从核对账单开始讲。")
        pressBack()
        await("已点亮 · AI 检验通过")
        pressBack()
        await("1 / 2 已点亮")
    }

    private fun iconOf(name: String): EntryIcon? = runBlocking {
        fixture.container.library.snapshot().entries.single { it.name == name }.icon
    }

    private fun send(text: String) {
        ui.onNodeWithTag("chat-input").performTextReplacement(text)
        // 文本测试会唤起键盘。用发送语义避免把按键测试和键盘动画的坐标变化混在一起。
        ui.onNodeWithContentDescription("发送").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun fill(label: String, value: String) {
        ui.onNode(hasSetTextAction() and hasText(label)).performTextInput(value)
    }

    private fun await(text: String, substring: Boolean = false) {
        try {
            ui.waitUntil(8_000) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("未显示「$text」\n${ui.onRoot().printToString(maxDepth = 6)}", error)
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun imeVisible(): Boolean = ui.runOnUiThread {
        ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun lightStatusBar(): Boolean = ui.runOnUiThread {
        WindowCompat.getInsetsController(ui.activity.window, ui.activity.window.decorView).isAppearanceLightStatusBars
    }
}
