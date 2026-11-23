package com.lifeskill.app

import android.view.KeyEvent
import androidx.activity.compose.setContent
import com.lifeskill.app.ui.LifeSkillTheme
import com.lifeskill.app.ui.TeachingContent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.toRect
import kotlin.math.roundToInt
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

    @Test fun timelineAndComposerRemainUsableTogether() {
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
        // 对比卡已停用：逐项比较改由 Markdown 表格承担，返回大纲后应能重新进入课时。
        val lessonAgain = BuiltInTeaching.lessons.first { it.key == "structure" }
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(lessonAgain.name))
        ui.onNodeWithContentDescription(lessonAgain.name).performClick()
        await("学习目标")
        assertTrue(BuiltInTeaching.lessonByKey("structure")!!.blocks.none { it is TeachingComparison })
    }

    @Test fun additionalChartStylesKeepInteractiveState() {
        val lesson = openLesson("data")
        scrollTo(hasText("科学 68"))
        ui.onNodeWithText("科学 68").performTouchInput { click() }
        ui.waitUntil(8_000) {
            val st = state(lesson, "categories")
            android.util.Log.d("LSCHIP", "probe selection=${st.selection} turnId=${lesson.turnId}")
            st.selection == "1"
        }
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

    @Test fun chartChipsAtLessonEndStayClickable() {
        // 回归：超长课时消息曾是远超两屏的列表项，末端卡片的触摸会被吞掉
        // （绘制正常但命中失效）。内容改为内滚动后，末位与早段的标签都必须
        // 可点、可选中并持久化。
        val lesson = openLesson("data")
        // 先把内滚动窗口滚进外层列表的视口，再滚内部内容到目标芯片。
        ui.onNodeWithTag("chat-messages").performScrollToNode(hasTestTag("teaching-content-scroll"))
        val scroll = ui.onNodeWithTag("teaching-content-scroll")
        scroll.performScrollToNode(hasTestTag("chart-chip-monthly-waterfall-3"))
        ui.onNodeWithTag("chart-chip-monthly-waterfall-3").assertIsDisplayed()
        ui.onNodeWithTag("chart-chip-monthly-waterfall-3").performClick()
        ui.waitUntil(8_000) { state(lesson, "monthly-waterfall").selection == "3" }
        ui.onNodeWithText("复购 · 15").assertExists()

        scroll.performScrollToNode(hasTestTag("chart-chip-study-scatter-1"))
        ui.onNodeWithTag("chart-chip-study-scatter-1").assertIsDisplayed()
        ui.onNodeWithTag("chart-chip-study-scatter-1").performClick()
        ui.waitUntil(8_000) { state(lesson, "study-scatter").selection == "1" }
        ui.onNodeWithText("2班 · 58").assertExists()

        // 早段卡片仍然可点。
        scroll.performScrollToNode(hasTestTag("chart-chip-categories-1"))
        ui.onNodeWithTag("chart-chip-categories-1").performClick()
        ui.waitUntil(8_000) { state(lesson, "categories").selection == "1" }
        ui.onNodeWithText("科学 · 68").assertExists()
    }

    @Test fun flipAndSortingUseRetryBeforeShowingAnswers() {
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

    @Test fun hotspotExplicitRevealRemainsInteractive() {
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

    }

    @Test fun hotspotMarkersStaySmallWithFullTouchTargets() {
        val lesson = openLesson("visual")
        val block = lesson.blocks.filterIsInstance<TeachingHotspot>().single()
        scrollTo(hasTestTag("hotspot-image"))
        val image = ui.onNodeWithTag("hotspot-image").getUnclippedBoundsInRoot()
        block.hotspots.forEach { hotspot ->
            val target = ui.onNodeWithTag("hotspot-${hotspot.id}")
            val bounds = target.getUnclippedBoundsInRoot()
            val marker = ui.onNodeWithTag("hotspot-marker-${hotspot.id}", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertEquals(48f, (bounds.right - bounds.left).value, .1f)
            assertEquals(48f, (bounds.bottom - bounds.top).value, .1f)
            assertEquals(18f, (marker.right - marker.left).value, .1f)
            assertTrue(bounds.left >= image.left && bounds.right <= image.right)
            assertTrue(bounds.top >= image.top && bounds.bottom <= image.bottom)
            // 点击可见标记以外、触控框以内的位置，不能只让小圆点可点。
            target.performTouchInput { click(Offset(width / 2f + 18f, height / 2f)) }
            ui.waitUntil(8_000) { state(lesson, block.id).selection == hotspot.id }
        }
    }

    @Test fun flipKeepsBothFaceHeightsStableThroughoutMotion() {
        val lesson = BuiltInTeaching.lessonByKey("practice")!!
        runBlocking {
            app.conversations.interact(
                ChatScope(ChatMode.TUTOR, lesson.id), lesson.turnId, "flip", TeachingAction.Reset,
            )
        }
        openLesson("practice")
        scrollTo(hasTestTag("flip-card"))
        val beforeBounds = ui.onNodeWithTag("flip-card").getUnclippedBoundsInRoot()
        val before = beforeBounds.bottom - beforeBounds.top
        ui.mainClock.autoAdvance = false
        try {
            ui.onNodeWithTag("flip-card-turn").performClick()
            ui.mainClock.advanceTimeByFrame()
            // 等待仓库提交后再手动推进动画，检查切面前后与完成时的高度。
            ui.waitUntil(8_000) {
                state(BuiltInTeaching.lessonByKey("practice")!!, "flip").selection == "back"
            }
            repeat(6) {
                ui.mainClock.advanceTimeBy(100)
                val currentBounds = ui.onNodeWithTag("flip-card").getUnclippedBoundsInRoot()
                val current = currentBounds.bottom - currentBounds.top
                assertEquals(before.value, current.value, .5f)
            }
        } finally {
            ui.mainClock.autoAdvance = true
        }
        ui.onNodeWithTag("flip-card-turn").assertIsEnabled().performClick()
        ui.waitForIdle()
        val after = ui.onNodeWithTag("flip-card").getUnclippedBoundsInRoot()
        assertEquals(before.value, (after.bottom - after.top).value, .5f)
    }

    @Test fun retiredCardsAreHiddenInLegacyMessagesWhileOtherCardsRemain() {
        val blocks = retiredTeachingBlocks() + TeachingFlipCard("keep", "保留翻卡", "回忆问题", "答案", "提示")
        ui.runOnUiThread {
            ui.activity.setContent {
                LifeSkillTheme(dark = false) {
                    TeachingContent(Turn("legacy", Speaker.ASSISTANT, TurnKind.MESSAGE, "保留正文",
                        teachingBlocks = blocks), true) { _, _ -> }
                }
            }
        }
        ui.onNodeWithTag("teaching-block-classification").assertDoesNotExist()
        ui.onNodeWithTag("teaching-block-trace").assertDoesNotExist()
        ui.onNodeWithTag("teaching-block-keep").assertIsDisplayed()
        ui.onNodeWithTag("flip-card-turn").assertIsEnabled()
    }

    @Test fun leverSlidersDraggingExpansionResetAndReopenShareOneState() {
        val lesson = openLesson("visual")
        fun expandedNode(tag: String) = ui.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("teaching-expanded")))
        scrollTo(hasContentDescription("放大杠杆实验台"))
        ui.onNodeWithContentDescription("放大杠杆实验台").performClick()
        ui.onNodeWithTag("teaching-expanded").assertIsDisplayed()
        val arm = expandedNode("lever-right_arm")
        arm.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        ui.waitUntil(8_000) { state(lesson, "lever").lever?.rightArm == 4.0 }
        expandedNode("lever-status").assertTextEquals("已平衡")
        expandedNode("lever-left_force").performScrollTo().performTouchInput {
            swipe(Offset(width * .25f, height / 2f), Offset(width * .70f, height / 2f), 400)
        }
        ui.waitUntil(8_000) { (state(lesson, "lever").lever?.leftForce ?: 0.0) > 4.0 }
        expandedNode("lever-weight-right").performScrollTo().performTouchInput {
            swipe(center, center + Offset(-70f, 0f), 450)
        }
        ui.waitUntil(8_000) { (state(lesson, "lever").lever?.rightArm ?: 4.0) < 4.0 }
        val saved = state(lesson, "lever").lever
        try {
            // ActivityScenario取Activity可能等待生命周期收敛，必须在测试线程完成，不能占住UI线程等待旋转。
            val portraitActivity = ui.activity
            ui.runOnUiThread { portraitActivity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            ui.waitUntil(8_000) {
                val bounds = ui.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()
                bounds.right - bounds.left > bounds.bottom - bounds.top
            }
            expandedNode("lever-left_force").performScrollTo().assertIsDisplayed()
            val scene = expandedNode("lever-scene").getUnclippedBoundsInRoot()
            val control = expandedNode("lever-left_force").getUnclippedBoundsInRoot()
            assertTrue(control.left > scene.left)
            assertEquals(saved, state(lesson, "lever").lever)
        } finally {
            val landscapeActivity = ui.activity
            ui.runOnUiThread { landscapeActivity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
        ui.waitUntil(8_000) {
            val bounds = ui.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()
            bounds.bottom - bounds.top > bounds.right - bounds.left
        }
        awaitDialogFocus()
        back()
        ui.onNodeWithTag("teaching-expanded").assertDoesNotExist()
        assertEquals(saved, state(lesson, "lever").lever)
        ui.activityRule.scenario.recreate()
        await("AI 老师")
        assertEquals(saved, state(lesson, "lever").lever)
        scrollTo(hasContentDescription("放大杠杆实验台"))
        ui.onNodeWithContentDescription("放大杠杆实验台").performClick()
        ui.onNode(hasContentDescription("重置杠杆实验台") and hasAnyAncestor(hasTestTag("teaching-expanded"))).performClick()
        ui.waitUntil(8_000) { state(lesson, "lever").lever == null }
        expandedNode("lever-status").assertTextEquals("左侧下沉")
        back()
        scrollTo(hasTestTag("lever-scene"))
        ui.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        back()
        await("课程大纲")
    }

    @Test fun relearningUsesCenteredModalAndRestoresRetryOnFailure() {
        val ai = (ui.activity.application as FixtureApplication).ai
        val course = runBlocking {
            val direction = app.library.snapshot().childrenOf(null).first { it.name == "新领域" }
            app.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "重学遮罩测试"))
        }
        val lesson = runBlocking { app.library.save(EntryEdit(null, course.id, EntryKind.SKILL, "遮罩测试课时", "理解概念")) }
        ai.reply("""{"kind":"teach","reply":"旧内容待替换"}""")
        runBlocking { app.conversations.send(ChatScope(ChatMode.TUTOR, lesson.id), "开始学习") }
        await("新领域")
        ui.onNodeWithText("新领域").performScrollTo().performClick()
        await(course.name)
        ui.onNodeWithText(course.name).performClick()
        await("课程大纲")
        ui.onNodeWithTag("skill-map").performScrollToNode(hasContentDescription(lesson.name))
        ui.onNodeWithContentDescription(lesson.name).performClick()
        ui.onNodeWithText("开始学习").performClick()
        await("旧内容待替换")
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        ai.enqueue { started.complete(Unit); release.await(); """{"kind":"teach","reply":"新的学习内容"}""" }
        fun restart() {
            ui.onNodeWithContentDescription("更多操作").performClick()
            ui.onNodeWithText("重新学习").performClick()
            ui.onNodeWithText("开始新一轮").performClick()
        }
        try {
            restart()
            ui.waitUntil(8_000) { started.isCompleted }
            ui.onNodeWithTag("restart-loading").assertIsDisplayed()
            captureTeachingScreenshot("teaching-restart-loading.png")
            ui.onNodeWithText("正在等待 AI 回复").assertDoesNotExist()
            release.complete(Unit)
            await("新的学习内容")
            ui.onNodeWithTag("restart-loading").assertDoesNotExist()
            val failRelease = kotlinx.coroutines.CompletableDeferred<Unit>()
            ai.enqueue { failRelease.await(); error("模拟模型失败") }
            restart()
            ui.onNodeWithTag("restart-loading").assertIsDisplayed()
            failRelease.complete(Unit)
            await("重试")
            ui.onNodeWithTag("restart-loading").assertDoesNotExist()
            val retryRelease = kotlinx.coroutines.CompletableDeferred<Unit>()
            ai.enqueue { retryRelease.await(); """{"kind":"teach","reply":"重试成功"}""" }
            ui.onNodeWithText("重试").performClick()
            ui.onNodeWithTag("restart-loading").assertIsDisplayed()
            back()
            await("课程大纲")
            retryRelease.complete(Unit)
        } finally {
            release.complete(Unit)
            runBlocking { app.library.delete(course.id) }
        }
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
        // 教学内容在内滚动窗口里，外层滚到位后还要滚内部窗口。
        runCatching { ui.onNodeWithTag("teaching-content-scroll").performScrollToNode(matcher) }
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
