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
import com.lifeskill.app.data.EntryEdit
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
        val lesson = openLesson("data")
        ui.onNodeWithText("验证预测").assertIsNotEnabled()
        ui.onNodeWithTag("prediction-demand-choice-0").performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(8_000) { state(lesson, "demand").prediction == 0 }
        ui.onNodeWithTag("prediction-demand-reveal").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        scrollTo(hasTestTag("teaching-price-demand"))
        ui.onNodeWithTag("teaching-price-demand").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        await("价格 4 元")
        ui.waitUntil(8_000) { state(lesson, "demand").value == 4.0 }
        ui.onNodeWithText("需求量 80 杯").assertExists()
        val slider = ui.onNodeWithTag("teaching-price-demand")
        slider.performTouchInput { swipe(Offset(width * .25f, height / 2f), Offset(width * .75f, height / 2f), 500) }
        ui.waitUntil(8_000) { (state(lesson, "demand").value ?: 0.0) > 6.0 }
        scrollTo(hasContentDescription("放大价格与需求量"))
        ui.onNodeWithContentDescription("放大价格与需求量").performTouchInput { click() }
        ui.onNodeWithTag("teaching-expanded").assertIsDisplayed()
        val expandedBounds = ui.onNodeWithTag("teaching-expanded").getUnclippedBoundsInRoot()
        val screenBounds = ui.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()
        assertTrue(expandedBounds.left > screenBounds.left)
        assertTrue(expandedBounds.top > screenBounds.top)
        assertTrue(expandedBounds.right < screenBounds.right)
        assertTrue(expandedBounds.bottom < screenBounds.bottom)
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
        val lesson = openLesson("practice")
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
        val lesson = openLesson("structure")
        scrollTo(hasTestTag("flow-node-B"))
        ui.onNodeWithTag("flow-node-B").performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(8_000) { state(lesson, "flow").selection == "B" }
        scrollTo(hasTestTag("flow-node-C"))
        ui.onNodeWithTag("flow-node-C").performSemanticsAction(SemanticsActions.OnClick)
        ui.waitUntil(8_000) { state(lesson, "flow").selection == "C" }
        scrollTo(hasTestTag("flow-node-C"))
        val node = ui.onNodeWithTag("flow-node-C")
        // 在节点上起手横滑只移动流程图，不把滑动误判成节点点击。
        node.performTouchInput { swipe(Offset(width*.8f,height/2f),Offset(width*.1f,height/2f),450) }
        assertEquals("C", state(lesson, "flow").selection)
        ui.onNodeWithTag("chat-messages").performTouchInput { swipeUp() }
        scrollTo(hasText("否 → 回访用户"))
        ui.onNodeWithText("否 → 回访用户").performTouchInput { click() }
        ui.waitUntil(8_000) { state(lesson, "flow").selection == "E" }
        ui.onNodeWithContentDescription("放大需求怎样进入开发").assertDoesNotExist()
    }

    @Test fun timelineComparisonAndComposerRemainUsableTogether() {
        val lesson = openLesson("structure")
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
        val comparison = BuiltInTeaching.lessons.first { it.key == "structure" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(comparison.name))
        ui.onNodeWithContentDescription(comparison.name).performClick()
        await("学习目标")
        ui.onNodeWithText("开始学习").performClick()
        scrollTo(hasText("图上表现"))
        ui.onNodeWithText("图上表现").performTouchInput { click() }
        ui.waitUntil(8_000) { state(comparison, "comparison").selection == "graph" }
        ui.onNodeWithText("需求曲线整体移动，相同价格下的需求量改变。").assertExists()
        val left = ui.onNodeWithTag("comparison-comparison-column-0").getUnclippedBoundsInRoot()
        val right = ui.onNodeWithTag("comparison-comparison-column-1").getUnclippedBoundsInRoot()
        assertEquals(left.bottom.value - left.top.value, right.bottom.value - right.top.value, 0.1f)
    }

    @Test fun additionalChartAndComparisonStylesKeepInteractiveState() {
        val lesson = openLesson("data")
        scrollTo(hasText("科学 68"))
        ui.onNodeWithText("科学 68").performTouchInput { click() }
        ui.waitUntil(8_000) { state(lesson, "categories").selection == "1" }
        ui.onNodeWithText("科学 · 68").assertExists()
        back()
        val area = BuiltInTeaching.lessons.first { it.key == "data" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(area.name))
        ui.onNodeWithContentDescription(area.name).performClick()
        ui.onNodeWithText("开始学习").performClick()
        scrollTo(hasText("三月 28"))
        ui.onNodeWithText("三月 28").performTouchInput { click() }
        ui.waitUntil(8_000) { state(area, "attendance").selection == "2" }
        ui.onNodeWithText("三月 · 28").assertExists()
    }

    @Test fun flipSortingAndClassificationUseRetryBeforeShowingAnswers() {
        val flip = openLesson("practice")
        scrollTo(hasTestTag("flip-card"))
        ui.onNodeWithTag("flip-card-turn").performClick()
        await("其他条件不变时，价格下降会让更多消费者愿意且能够购买，因此需求曲线上的对应点向右移动。")
        assertEquals("back", state(flip, "flip").selection)
        back()

        val sorting = BuiltInTeaching.lessons.first { it.key == "practice" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(sorting.name))
        ui.onNodeWithContentDescription(sorting.name).performClick()
        ui.onNodeWithText("开始学习").performClick()
        scrollTo(hasContentDescription("上移记录用户在什么场景遇到困难"))
        ui.onNodeWithContentDescription("上移记录用户在什么场景遇到困难").performClick()
        ui.waitUntil(8_000) { state(sorting, "sorting").order.firstOrNull() == "observe" }
        scrollTo(hasText("提交顺序"))
        ui.onNodeWithText("提交顺序").performClick()
        scrollTo(hasText("再想一步"))
        ui.onNodeWithText("再想一步").assertIsDisplayed()
        scrollTo(hasText("查看完整答案"))
        ui.onNodeWithText("查看完整答案").performClick()
        ui.waitUntil(8_000) { state(sorting, "sorting").revealed }
        assertTrue(state(sorting, "sorting").attempts >= 1)
        back()

        val classification = BuiltInTeaching.lessons.first { it.key == "practice" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(classification.name))
        ui.onNodeWithContentDescription(classification.name).performClick()
        ui.onNodeWithText("开始学习").performClick()
        val block = classification.blocks.filterIsInstance<TeachingClassification>().single()
        block.items.forEach { item ->
            scrollTo(hasTestTag("classification-${item.id}-observation"))
            ui.onNodeWithTag("classification-${item.id}-observation").performTouchInput { click() }
        }
        scrollTo(hasText("提交分类"))
        ui.onNodeWithText("提交分类").performClick()
        scrollTo(hasTestTag("feedback-classification"))
        ui.onNodeWithTag("feedback-classification").assertIsDisplayed()
        scrollTo(hasTestTag("feedback-classification-reveal"))
        ui.onNodeWithTag("feedback-classification-reveal").performClick()
        ui.waitUntil(8_000) { state(classification, "classification").revealed }
        scrollTo(hasText(block.items.first().explanation))
        ui.onNodeWithText(block.items.first().explanation).assertExists()
    }

    @Test fun sortingHandleSupportsLongPressDragReordering() {
        val lesson = openLesson("practice")
        scrollTo(hasTestTag("sort-handle-validate"))
        ui.onNodeWithTag("sort-handle-validate").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f, 110f))
            advanceEventTime(250)
            up()
        }
        ui.waitUntil(8_000) {
            state(lesson, "sorting").sortOrder(
                BuiltInTeaching.lessonByKey("practice")!!.blocks.filterIsInstance<TeachingSorting>().single(),
            ).take(2) == listOf("observe", "validate")
        }
    }

    @Test fun stepTraceAdvancesAcrossMultipleCheckpoints() {
        val lesson = BuiltInTeaching.lessonByKey("practice")!!
        runBlocking {
            app.conversations.interact(
                ChatScope(ChatMode.TUTOR, lesson.id), lesson.turnId, "trace", TeachingAction.Reset,
            )
        }
        openLesson("practice")
        scrollTo(hasTestTag("trace-choice-down-0"))
        ui.onNodeWithTag("trace-choice-down-0").performClick()
        scrollTo(hasText("提交预测"))
        ui.onNodeWithText("提交预测").performClick()
        scrollTo(hasText("进入下一步"))
        ui.onNodeWithText("进入下一步").performClick()
        ui.waitUntil(8_000) { state(lesson, "trace").step == 1 }

        scrollTo(hasTestTag("trace-choice-up-1"))
        ui.onNodeWithTag("trace-choice-up-1").performClick()
        scrollTo(hasText("提交预测"))
        ui.onNodeWithText("提交预测").performClick()
        scrollTo(hasTestTag("trace-choice-up-0"))
        ui.onNodeWithTag("trace-choice-up-0").performClick()
        ui.waitUntil(8_000) {
            state(lesson, "trace").selection == "0" && !state(lesson, "trace").submitted
        }
        ui.onNodeWithTag("trace-choice-up-1").performClick()
        ui.waitUntil(8_000) {
            state(lesson, "trace").selection == "1" && !state(lesson, "trace").submitted
        }
        ui.onNodeWithText("提交预测").performClick()
        ui.waitUntil(8_000) { state(lesson, "trace").submitted }
        scrollTo(hasText("进入下一步"))
        ui.onNodeWithText("进入下一步").performClick()
        ui.waitUntil(8_000) { state(lesson, "trace").step == 2 }
        scrollTo(hasTestTag("trace-step-combine"))
        ui.onNodeWithTag("trace-step-combine").assertIsDisplayed()
    }

    @Test fun hotspotZoomAndStepTraceExplicitRevealRemainInteractive() {
        val hotspot = openLesson("visual")
        scrollTo(hasTestTag("hotspot-nucleus"))
        ui.onNodeWithTag("hotspot-nucleus").performTouchInput { click() }
        await("细胞核含有遗传物质，参与控制细胞活动。")
        ui.onNodeWithContentDescription("放大观察植物细胞").assertDoesNotExist()
        scrollTo(hasText("查看全部热点"))
        ui.onNodeWithText("查看全部热点").performClick()
        ui.waitUntil(8_000) { state(hotspot, "hotspot").revealed }
        scrollTo(hasText("叶绿体 · 叶绿体是植物进行光合作用的场所。"))
        ui.onNodeWithText("叶绿体 · 叶绿体是植物进行光合作用的场所。").assertExists()
        back()

        val trace = BuiltInTeaching.lessons.first { it.key == "practice" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(trace.name))
        ui.onNodeWithContentDescription(trace.name).performClick()
        ui.onNodeWithText("开始学习").performClick()
        scrollTo(hasTestTag("trace-choice-down-0"))
        ui.onNodeWithTag("trace-choice-down-0").performTouchInput { click() }
        scrollTo(hasText("提交预测"))
        ui.onNodeWithText("提交预测").performClick()
        scrollTo(hasText("进入下一步"))
        ui.onNodeWithText("进入下一步").performClick()
        ui.waitUntil(8_000) { state(trace, "trace").step == 1 }
        scrollTo(hasTestTag("trace-choice-up-0"))
        ui.onNodeWithTag("trace-choice-up-0").performTouchInput { click() }
        scrollTo(hasText("提交预测"))
        ui.onNodeWithText("提交预测").performClick()
        scrollTo(hasText("查看完整推演"))
        ui.onNodeWithText("查看完整推演").performClick()
        ui.waitUntil(8_000) { state(trace, "trace").revealed }
        val traceBlock = trace.blocks.filterIsInstance<TeachingStepTrace>().single()
        scrollTo(hasText(traceBlock.summary))
        ui.onNodeWithText(traceBlock.summary).assertExists()
    }

    @Test fun deletedShowcaseCanBeRestoredWithoutReinstallingTheApp() {
        val library = app.library
        val personalCourse = runBlocking {
            val direction = library.snapshot().childrenOf(null).first { it.name == "本职工作" }
            library.save(EntryEdit(null, direction.id, EntryKind.TREE, "保留的用户课程"))
        }
        try {
            await("新领域")
            ui.onNodeWithText("新领域").performClick()
            await(BuiltInTeaching.COURSE_NAME)

            runBlocking { library.delete(BuiltInTeaching.COURSE_ID) }
            await("恢复演示课")
            ui.onNodeWithText("恢复演示课").performClick()
            await(BuiltInTeaching.COURSE_NAME)

            assertEquals(4, runBlocking { library.snapshot().childrenOf(BuiltInTeaching.COURSE_ID).size })
            assertNotNull(runBlocking { library.entry(personalCourse.id) })
        } finally {
            runBlocking { library.delete(personalCourse.id) }
        }
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
