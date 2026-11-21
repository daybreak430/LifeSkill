package com.lifeskill.app

import android.view.KeyEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeachingUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = (ui.activity.application as FixtureApplication).container

    @Test fun predictionSliderTouchPersistenceAndSystemBack() {
        val lesson = openLesson("chart")
        ui.onNodeWithText("验证预测").assertIsNotEnabled()
        ui.onNodeWithText("增加").performTouchInput { click() }
        ui.onNodeWithText("验证预测").assertIsEnabled().performClick()
        scrollTo(hasTestTag("teaching-price"))
        ui.onNodeWithTag("teaching-price").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        await("价格 4 元")
        ui.waitUntil(8_000) { state(lesson, "demand").value == 4.0 }
        ui.onNodeWithText("需求量 80 杯").assertExists()
        val slider = ui.onNodeWithTag("teaching-price")
        slider.performTouchInput { swipe(Offset(width * .25f, height / 2f), Offset(width * .75f, height / 2f), 500) }
        ui.waitUntil(8_000) { (state(lesson, "demand").value ?: 0.0) > 6.0 }
        scrollTo(hasContentDescription("放大价格与需求量"))
        ui.onNodeWithContentDescription("放大价格与需求量").performTouchInput { click() }
        ui.onNodeWithTag("teaching-expanded").assertIsDisplayed()
        awaitDialogFocus()
        back()
        ui.onNodeWithTag("teaching-expanded").assertDoesNotExist()
        await("AI 老师")
        ui.activityRule.scenario.recreate()
        await("AI 老师")
        assertTrue(state(lesson, "demand").revealed)
        assertTrue((state(lesson, "demand").value ?: 0.0) > 6.0)
    }

    @Test fun practiceWrongAnswerRetryNeverCompletesTheLesson() {
        val lesson = openLesson("quiz")
        scrollTo(hasTestTag("practice-choice-0"))
        val option = ui.onNodeWithTag("practice-choice-0")
        val containerBounds = option.getUnclippedBoundsInRoot()
        val textBounds = ui.onNodeWithText("A. 直接交给开发", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs((containerBounds.top.value + containerBounds.bottom.value - textBounds.top.value - textBounds.bottom.value) / 2f) < 2f)
        option.performTouchInput { click() }
        scrollTo(hasText("提交随堂答案"))
        ui.onNodeWithText("提交随堂答案").performTouchInput { click() }
        scrollTo(hasText("再想一步"))
        ui.onNodeWithText("再想一步").assertIsDisplayed()
        scrollTo(hasTestTag("practice-choice-1"))
        ui.onNodeWithTag("practice-choice-1").performTouchInput { click() }
        scrollTo(hasText("提交随堂答案"))
        ui.onNodeWithText("提交随堂答案").performClick()
        scrollTo(hasText("理解正确"))
        ui.onNodeWithText("理解正确").assertIsDisplayed()
        assertEquals(2, state(lesson, "quiz").attempts)
        assertEquals(Mastery.UNLIT, runBlocking { app.library.entry(lesson.id)?.mastery })
    }

    @Test fun flowHorizontalAndVerticalScrollingDoesNotTriggerNodesAndBackClosesOnlyTheDiagram() {
        val lesson = openLesson("flow")
        scrollTo(hasTestTag("flow-node-C"))
        val node = ui.onNodeWithTag("flow-node-C")
        node.performTouchInput { click() }
        ui.waitUntil(8_000) { state(lesson, "flow").selection == "C" }
        // 在节点上起手横滑只移动流程图，不把滑动误判成节点点击。
        node.performTouchInput { swipe(Offset(width*.8f,height/2f),Offset(width*.1f,height/2f),450) }
        assertEquals("C", state(lesson, "flow").selection)
        ui.onNodeWithTag("chat-messages").performTouchInput { swipeUp() }
        scrollTo(hasText("否 → 回访用户"))
        ui.onNodeWithText("否 → 回访用户").performTouchInput { click() }
        ui.waitUntil(8_000) { state(lesson, "flow").selection == "E" }
        scrollTo(hasContentDescription("放大需求怎样进入开发"))
        ui.onNodeWithContentDescription("放大需求怎样进入开发").performClick()
        ui.onNodeWithTag("teaching-expanded").assertIsDisplayed()
        awaitDialogFocus()
        back()
        ui.onNodeWithTag("teaching-expanded").assertDoesNotExist()
        await("AI 老师")
    }

    @Test fun timelineComparisonAndComposerRemainUsableTogether() {
        val lesson = openLesson("timeline")
        scrollTo(hasTestTag("timeline-uprising"))
        ui.onNodeWithTag("timeline-uprising").performTouchInput { click() }
        ui.waitUntil(8_000) { state(lesson, "timeline").selection == "uprising" }
        // 键盘打开时仍可在对话内滚动，第一次系统返回应先收起键盘。
        val composer = ui.onNode(hasSetTextAction() and hasText("输入学习问题"))
        composer.performTouchInput { click() }
        composer.performTextInput("这两个事件有什么关系？")
        ui.waitUntil(8_000) { imeVisible() }
        ui.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        back()
        ui.waitUntil(8_000) { !imeVisible() }
        await("AI 老师")
        back()
        await("课程大纲")
        val comparison = BuiltInTeaching.lessons.first { it.key == "comparison" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(comparison.name))
        ui.onNodeWithContentDescription(comparison.name).performClick()
        await("学习目标")
        ui.onNodeWithText("开始学习").performClick()
        scrollTo(hasText("图上表现"))
        ui.onNodeWithText("图上表现").performTouchInput { click() }
        ui.waitUntil(8_000) { state(comparison, "comparison").selection == "graph" }
        ui.onNodeWithText("需求曲线整体移动，相同价格下的需求量改变。").assertExists()
        val left = ui.onNodeWithTag("comparison-column-0").getUnclippedBoundsInRoot()
        val right = ui.onNodeWithTag("comparison-column-1").getUnclippedBoundsInRoot()
        assertEquals(left.bottom.value - left.top.value, right.bottom.value - right.top.value, 0.1f)
    }

    @Test fun additionalChartAndComparisonStylesKeepInteractiveState() {
        val lesson = openLesson("chart-bar")
        scrollTo(hasText("科学 68"))
        ui.onNodeWithText("科学 68").performTouchInput { click() }
        ui.waitUntil(8_000) { state(lesson, "categories").selection == "1" }
        ui.onNodeWithText("科学 · 68").assertExists()
        back()
        val area = BuiltInTeaching.lessons.first { it.key == "chart-area" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(area.name))
        ui.onNodeWithContentDescription(area.name).performClick()
        ui.onNodeWithText("开始学习").performClick()
        scrollTo(hasText("三月 28"))
        ui.onNodeWithText("三月 28").performTouchInput { click() }
        ui.waitUntil(8_000) { state(area, "attendance").selection == "2" }
        ui.onNodeWithText("三月 · 28").assertExists()
    }

    private fun openLesson(key: String): BuiltInTeachingLesson {
        val lesson = BuiltInTeaching.lessons.first { it.key == key }
        await("新领域")
        ui.onNodeWithText("新领域").performScrollTo().performClick()
        await(BuiltInTeaching.COURSE_NAME)
        ui.onNodeWithText(BuiltInTeaching.COURSE_NAME).performClick()
        await("课程大纲")
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(lesson.name))
        ui.onNodeWithContentDescription(lesson.name).performClick()
        await("学习目标")
        ui.onNodeWithText("开始学习").performClick()
        await("内置示例")
        return lesson
    }

    private fun state(lesson: BuiltInTeachingLesson, blockId: String): TeachingState = runBlocking {
        app.conversations.watch(ChatScope(ChatMode.TUTOR, lesson.id)).first().first { it.id == lesson.turnId }
            .teachingStates[blockId] ?: TeachingState()
    }
    private fun scrollTo(matcher: SemanticsMatcher) {
        ui.onNodeWithTag("chat-messages").performScrollToNode(matcher)
    }
    private fun await(text: String) {
        try {
            ui.waitUntil(8_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("未显示$text\n${ui.onRoot().printToString(maxDepth = 5)}", error)
        }
    }
    private fun awaitDialogFocus() {
        ui.waitUntil(8_000) { ui.runOnUiThread { !ui.activity.hasWindowFocus() } }
    }
    private fun imeVisible(): Boolean = ui.runOnUiThread {
        ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }
    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        ui.waitForIdle()
    }
}
