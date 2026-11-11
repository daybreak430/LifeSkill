package com.lifeskill.app

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.CertificateTheme
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.AppearanceMode
import com.lifeskill.app.data.AppearanceSettings
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.ExamAnswer
import com.lifeskill.app.model.AvatarPreset
import com.lifeskill.app.model.PersonalProfile
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MvpFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val fixture get() = ui.activity.application as FixtureApplication

    @Test fun appearanceSelectionUpdatesSystemBarsAndSavesLocally() {
        val settings = AppearanceSettings(ui.activity.applicationContext)
        val original = settings.load()
        try {
            await("本职工作")
            openSettings()
            await("外观")
            ui.onNodeWithText("浅色").performClick().assertIsSelected()
            assertEquals(AppearanceMode.LIGHT, settings.load())
            assertTrue(lightStatusBar())
            ui.onNodeWithText("深色").performClick().assertIsSelected()
            assertEquals(AppearanceMode.DARK, settings.load())
            assertTrue(!lightStatusBar())
            val titleLayout = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText("设置").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(titleLayout))
            }
            assertTrue(titleLayout.single().layoutInput.style.color.luminance() > .7f)
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
        ui.onNodeWithTag("chat-input").performTextInput("第一行\n第二行")
        ui.onNodeWithContentDescription("发送").assertIsDisplayed().assertIsEnabled()
        pressBack()
        ui.waitUntil(8_000) { !imeVisible() }
        ui.onNodeWithText("AI 生成").assertIsDisplayed()
    }

    @Test fun clearGenerationUsesConfirmationAndStartsFreshWhileReplyIsPending() {
        await("AI 生成技能树")
        ui.onNodeWithTag("tab-personal").assertIsDisplayed()
        ui.onNodeWithText("AI 生成技能树").performClick()
        await("AI 生成")
        ui.onNodeWithTag("tab-personal").assertDoesNotExist()

        fixture.ai.enqueue { awaitCancellation() }
        send("清空前的学习请求")
        await("清空前的学习请求")
        ui.onNodeWithContentDescription("更多操作").performTouchInput { click() }
        ui.onNodeWithText("清空当前会话").performTouchInput { click() }
        await("清空当前会话？")
        pressBack()
        ui.onNodeWithText("清空当前会话？").assertDoesNotExist()
        ui.onNodeWithText("清空前的学习请求").assertIsDisplayed()

        ui.onNodeWithContentDescription("更多操作").performTouchInput { click() }
        ui.onNodeWithText("清空当前会话").performTouchInput { click() }
        await("清空当前会话？")
        ui.onNodeWithText("清空").performTouchInput { click() }
        await("一起规划技能树")
        ui.onNodeWithText("清空前的学习请求").assertDoesNotExist()

        fixture.ai.reply("""{"kind":"message","reply":"新会话收到"}""")
        send("清空后的学习请求")
        await("新会话收到")
        ui.onNodeWithContentDescription("返回").performClick()
        await("AI 生成技能树")
        ui.onNodeWithTag("tab-personal").assertIsDisplayed()
    }

    @Test fun tabsRestoreScrollAndProfileEditorKeepsOnlySavedChanges() {
        await("LifeSkill")
        val profiles = fixture.container.profiles
        val library = fixture.container.library
        val original = runBlocking { profiles.current().also { profiles.save(PersonalProfile()) } }
        val directions = runBlocking {
            (1..12).map { library.save(EntryEdit(null, null, EntryKind.DIRECTION, "标签测试方向 $it")) }
        }
        try {
            val learning = ui.onNodeWithTag("tab-learning").assertIsSelected()
            val personal = ui.onNodeWithTag("tab-personal")
            val lastDirection = "direction-card-${directions.last().id}"
            ui.onNodeWithTag("learning-directions").performScrollToNode(hasTestTag(lastDirection))
            personal.performClick().assertIsSelected()
            await("学习者")
            ui.onNodeWithText(directions.first().name).assertDoesNotExist()
            ui.onNodeWithContentDescription("设置").assertIsDisplayed()
            ui.onNodeWithTag("personal-home").performScrollToNode(hasText("证书展柜"))
            learning.performClick().assertIsSelected()
            ui.onNodeWithTag(lastDirection).assertIsDisplayed()
            personal.performClick()
            ui.onNodeWithText("证书展柜").assertIsDisplayed()
            ui.onNodeWithTag("personal-home").performScrollToNode(hasText("学习者"))
            ui.onNodeWithContentDescription("编辑资料").performClick()
            await("内置头像")
            personal.assertDoesNotExist()
            AvatarPreset.entries.forEach { ui.onNodeWithContentDescription("${it.label}头像").assertExists() }
            ui.onNodeWithContentDescription("橘猫头像").performScrollTo().performClick().assertIsSelected()
            captureScreenshot("capturePersonalScreenshots", "avatars.png")
            val activity = ui.activity
            ui.onNodeWithText("从相册选择").performScrollTo().performTouchInput { click() }
            ui.waitUntil(8_000) { !ui.runOnUiThread { activity.hasWindowFocus() } }
            pressBack()
            ui.waitUntil(8_000) { ui.runOnUiThread { activity.hasWindowFocus() } }
            ui.onNodeWithContentDescription("橘猫头像").assertIsSelected()
            ui.onNode(hasSetTextAction() and hasText("昵称")).performScrollTo().performTextReplacement("  新昵称  ")
            ui.onNodeWithText("保存资料").performScrollTo().performClick()
            await("新昵称")
            assertEquals(PersonalProfile("新昵称", ProfileAvatar.Preset(AvatarPreset.CAT)), runBlocking { profiles.current() })

            ui.activityRule.scenario.recreate()
            await("新昵称")
            ui.onNodeWithContentDescription("编辑资料").performClick()
            await("内置头像")
            ui.onNodeWithText("恢复默认头像").assertDoesNotExist()
            ui.onNodeWithContentDescription("短发女生头像").performClick().assertIsSelected()
            pressBack()
            await("新昵称")
            assertEquals(ProfileAvatar.Preset(AvatarPreset.CAT), runBlocking { profiles.current().avatar })
            pressBack()
            learning.assertIsSelected()
            ui.onNodeWithTag(lastDirection).assertIsDisplayed()
        } finally {
            runBlocking {
                profiles.save(original)
                directions.forEach { library.delete(it.id) }
            }
        }
    }

    @Test fun chatAlignsCharacterDiagramsAndScrollsLongRepliesToTheLastOption() {
        await("本职工作")
        val library = fixture.container.library
        val tree = runBlocking {
            val direction = library.snapshot().childrenOf(null).first()
            library.save(EntryEdit(null, direction.id, EntryKind.TREE, "示意图练习树"))
        }
        try {
            val diagram = "┌${"─".repeat(52)}┐\n│ 数据示例${" ".repeat(43)}│\n└${"─".repeat(52)}┘"
            val ascii = "+------+\n|  OK  |\n+------+"
            val fence = "```"
            val lesson = "$fence" + "diagram\n$diagram\n$fence\n\n$ascii"
            runBlocking {
                val skill = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "阅读示意图", "理解图例和边框"))
                fixture.ai.reply(JSONObject().put("kind", "offer_check").put("reply", lesson).toString())
                fixture.container.conversations.send(ChatScope(ChatMode.TUTOR, skill.id), "用字符图演示")
            }
            await("继续学习")
            ui.onNodeWithText("继续学习").performTouchInput { click() }
            await("阅读示意图")
            val unicode = ui.onNode(hasTestTag("lesson-diagram") and hasText(diagram))
            unicode.assertIsDisplayed()
            ui.onNode(hasTestTag("lesson-diagram") and hasText(ascii)).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            unicode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            val textLayout = layouts.single()
            val upperRight = textLayout.getHorizontalPosition(diagram.indexOf('┐'), usePrimaryDirection = true)
            val middleRight = textLayout.getHorizontalPosition(diagram.lastIndexOf('│'), usePrimaryDirection = true)
            assertTrue("中文行右框线应与上边框对齐：$upperRight / $middleRight", abs(upperRight - middleRight) < 1f)
            val horizontal = SemanticsProperties.HorizontalScrollAxisRange
            assertTrue(unicode.fetchSemanticsNode().config[horizontal].maxValue() > 0)
            unicode.performTouchInput { swipe(Offset(width * .86f, height * .55f), Offset(width * .14f, height * .55f)) }
            assertTrue(unicode.fetchSemanticsNode().config[horizontal].value() > 0)

            val question = (1..14).joinToString("\n\n") { "问题背景$it：观察图例和边框的位置，再判断数据如何分区。" }
            fixture.ai.reply(JSONObject().put("kind", "exercise").put("reply", question)
                .put("choices", org.json.JSONArray(listOf("甲", "乙", "丙", "最后一个选项"))).toString())
            ui.onNode(hasText("检验掌握") and hasClickAction()).performTouchInput { click() }
            await("D. 最后一个选项")
            val lastOption = ui.onNode(hasText("D. 最后一个选项") and hasClickAction()).assertIsDisplayed()
            val messages = ui.onNodeWithTag("chat-messages")
            assertTrue(lastOption.getUnclippedBoundsInRoot().bottom < messages.getUnclippedBoundsInRoot().bottom - 16.dp)
            messages.performTouchInput {
                swipe(Offset(width * .52f, height * .18f), Offset(width * .52f, height * .88f))
            }
            lastOption.assertIsNotDisplayed()
        } finally {
            runBlocking { library.delete(tree.id) }
        }
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
            ui.onNode(hasText("研习方向") and hasClickAction())
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已学会 0 个技能点，共 1 个"))
            ui.onNodeWithText("0/1").assertIsDisplayed()
            ui.onNodeWithText("继续学习").performTouchInput { click() }
            await("先梳理已学的要点。")
            ui.activityRule.scenario.recreate()
            await("先梳理已学的要点。")
            pressBack()
            await("续学技能树")
            ui.onNodeWithTag("skill-map").assertIsDisplayed()
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
            runBlocking {
                val prerequisite = library.save(EntryEdit(null, skill.parentId, EntryKind.SKILL, "新增前置", "掌握基础"))
                library.save(EntryEdit(skill.id, skill.parentId, skill.kind, skill.name, skill.goal, setOf(prerequisite.id)))
            }
            await("查看技能")
            ui.onNodeWithText("查看技能").performTouchInput { click() }
            ui.onNodeWithText("学习技能").assertIsNotEnabled()
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

    @Test fun homeCreatesTreeAndLeadsToNextSkillThenCompletedMap() {
        await("本职工作")
        val library = fixture.container.library
        val direction = runBlocking { library.save(EntryEdit(null, null, EntryKind.DIRECTION, "首页练习方向")) }
        val grid = ui.onNodeWithTag("learning-directions")
        val createTree = "为${direction.name}创建技能树"
        try {
            await(direction.name)
            grid.performScrollToNode(hasContentDescription(createTree))
            ui.onNodeWithContentDescription(createTree).performTouchInput { click() }
            await("新增技能树")
            ui.onNodeWithText("取消").performClick()
            grid.assertIsDisplayed()
            ui.onNodeWithContentDescription(createTree).performClick()
            fill("名称", "首页续学路径")
            ui.onNodeWithText("保存").performClick()
            await("首页续学路径")
            ui.onNodeWithTag("skill-tree-list").assertIsDisplayed()
            val (first, next) = runBlocking {
                val tree = library.snapshot().childrenOf(direction.id).single()
                val first = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "理解基础", "掌握基本概念"))
                val next = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "综合练习", "运用基本概念", setOf(first.id)))
                passFixtureSkill(first.id)
                first to next
            }
            pressBack()
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("learning-directions").fetchSemanticsNodes().isNotEmpty() }
            grid.performScrollToIndex(0)
            await("下一步 · 首页续学路径")
            ui.onNodeWithText("综合练习").assertIsDisplayed()
            ui.onNodeWithText("开始学习").performTouchInput { click() }
            await("AI 老师")
            await(next.name)
            pressBack()
            await("技能地图")
            pressBack()
            await("下一步 · 首页续学路径")
            runBlocking { passFixtureSkill(next.id) }
            await("已完成")
            val completedDirection = hasText(direction.name) and hasText("2/2")
            grid.performScrollToNode(completedDirection)
            ui.onNode(completedDirection).assertIsDisplayed()
            grid.performScrollToIndex(0)
            ui.onNodeWithText("查看技能地图").performTouchInput { click() }
            await("2 / 2 已点亮")
            ui.onNodeWithContentDescription(first.name).assertIsDisplayed()
        } finally {
            runBlocking { library.delete(direction.id) }
        }
    }

    @Test fun homeShowsItsMotifAndKeepsDirectionCardHeadersAndHeightsAcrossStates() {
        await("LifeSkill")
        ui.onNodeWithTag("home-study-motif").assertIsDisplayed()
        val library = fixture.container.library
        val directions = runBlocking {
            listOf("待建树方向", "跨行对齐方向", "保持为空的方向").map { name ->
                library.save(EntryEdit(null, null, EntryKind.DIRECTION, name))
            }
        }
        try {
            val grid = ui.onNodeWithTag("learning-directions")
            val changing = hasTestTag("direction-card-${directions.first().id}")
            fun cardHeight(card: SemanticsMatcher) =
                ui.onNode(card).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
            fun titleOffset(): Offset {
                val card = ui.onNode(changing).fetchSemanticsNode().boundsInRoot
                val title = ui.onNodeWithText(directions.first().name, useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                return title.topLeft - card.topLeft
            }
            fun waitForCardText(text: String) {
                val status = hasText(text) and (changing or hasAnyAncestor(changing))
                ui.waitUntil(8_000) { ui.onAllNodes(status).fetchSemanticsNodes().isNotEmpty() }
            }
            grid.performScrollToNode(changing)
            val initial = cardHeight(changing)
            val initialTitle = titleOffset()
            val tree = runBlocking {
                library.save(EntryEdit(null, directions.first().id, EntryKind.TREE, "对齐测试树"))
            }
            waitForCardText("暂无技能点")
            assertEquals(initial, cardHeight(changing), .5f)
            assertEquals(initialTitle, titleOffset())
            runBlocking { library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "测试技能", "掌握测试概念")) }
            waitForCardText("0/1")
            assertEquals(initial, cardHeight(changing), .5f)
            assertEquals(initialTitle, titleOffset())
            val title = ui.onNodeWithText(directions.first().name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val countNode = ui.onNode(hasText("0/1") and hasAnyAncestor(changing), useUnmergedTree = true)
            val count = countNode.fetchSemanticsNode().boundsInRoot
            val card = ui.onNode(changing).fetchSemanticsNode().boundsInRoot
            assertTrue(count.top > title.bottom)
            assertTrue(count.left > card.left && count.right < card.center.x)
            countNode.performTouchInput { click() }
            await("对齐测试树")
            pressBack()
            await("学习方向")
            val otherRow = hasTestTag("direction-card-${directions.last().id}")
            grid.performScrollToNode(otherRow)
            assertEquals(initial, cardHeight(otherRow), .5f)
        } finally {
            runBlocking { directions.forEach { library.delete(it.id) } }
        }
    }

    private suspend fun passFixtureSkill(skillId: String) {
        val scope = ChatScope(ChatMode.TUTOR, skillId)
        val conversations = fixture.container.conversations
        fixture.ai.reply("""{"kind":"offer_check","reply":"可以检验掌握。"}""")
        conversations.send(scope, "开始学习")
        fixture.ai.reply("""{"kind":"exercise","reply":"该怎么做？","choices":["先核对","直接跳过"]}""")
        conversations.send(scope, "", check = true)
        val exerciseId = conversations.watch(scope).first().last().id
        fixture.ai.reply(RepositoryTest.verdict("passed", "已经掌握。", exerciseId))
        conversations.send(scope, "A. 先核对")
    }

    @Test fun conflictingDependenciesStayInSkillDetailsWithoutMapReferences() {
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
        await("技能地图")
        ui.onNodeWithText("关联", substring = true).assertDoesNotExist()
        val lastNode = ui.onNodeWithContentDescription("左下游").fetchSemanticsNode().boundsInRoot
        val examNode = ui.onNodeWithText("验收考试").fetchSemanticsNode().boundsInRoot
        assertTrue(examNode.top >= lastNode.bottom)
        ui.onNodeWithContentDescription("左下游")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未解锁"))
            .performTouchInput { click() }
        await("前置技能")
        await("右起点")
        await("左起点")
    }

    @Test fun darkMapShowsEveryAvailableSiblingWithTheLearnableStyle() {
        await("本职工作")
        val settings = AppearanceSettings(ui.activity.applicationContext)
        val originalAppearance = settings.load()
        val library = fixture.container.library
        val direction = runBlocking { library.save(EntryEdit(null, null, EntryKind.DIRECTION, "深色分支样式")) }
        val tree = runBlocking { library.save(EntryEdit(null, direction.id, EntryKind.TREE, "并行分支")) }
        val root = runBlocking { library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "共同前置", "掌握基础")) }
        val left = runBlocking {
            library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "左侧分支", "完成左侧练习", setOf(root.id)))
        }
        val right = runBlocking {
            library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "右侧分支", "完成右侧练习", setOf(root.id)))
        }
        runBlocking { library.lightFromAssessment(root) }
        try {
            ui.onNodeWithTag("tab-personal").performClick()
            ui.onNodeWithContentDescription("设置").performClick()
            await("外观")
            ui.onNodeWithText("深色").performClick().assertIsSelected()
            pressBack()
            ui.onNodeWithTag("tab-learning").performClick()
            await("LifeSkill")
            val cardTag = "direction-card-${direction.id}"
            ui.onNodeWithTag("learning-directions").performScrollToNode(hasTestTag(cardTag))
            ui.onNodeWithTag(cardTag).performTouchInput { click() }
            await(direction.name)
            ui.onNode(hasText(tree.name) and hasClickAction()).performClick()
            await("1 / 3 已点亮")

            val available = SemanticsMatcher("可学习状态") { node ->
                runCatching {
                    node.config[SemanticsProperties.StateDescription].startsWith("可点亮")
                }.getOrDefault(false)
            }
            ui.onNodeWithContentDescription(left.name).assert(available)
            ui.onNodeWithContentDescription(right.name).assert(available)
            captureScreenshot("captureSkillMapScreenshots", "dark-available-siblings.png")
        } finally {
            settings.save(originalAppearance)
            runBlocking { library.delete(direction.id) }
        }
    }

    @Test fun treeExamUsesSeparatePageAndRevealsChallengesAfterPassing() {
        await("LifeSkill")
        val library = fixture.container.library
        val profiles = fixture.container.profiles
        val original = runBlocking { profiles.current().also { profiles.save(PersonalProfile()) } }
        val (direction, tree, skill) = runBlocking {
            val direction = library.save(EntryEdit(null, null, EntryKind.DIRECTION, "验收考试导航"))
            val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, "验收流程测试"))
            val skill = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "核对凭证", "用凭证核对总额"))
            Triple(direction, tree, skill)
        }
        try {
            ui.waitForIdle()
            val cardTag = "direction-card-${direction.id}"
            ui.onNodeWithTag("learning-directions").performScrollToNode(hasTestTag(cardTag))
            ui.onNodeWithTag(cardTag).performTouchInput { click() }
            await(tree.name)
            ui.onNode(hasText(tree.name) and hasClickAction()).performClick()
            val map = ui.onNodeWithTag("skill-map")
            map.performScrollToNode(hasText("验收考试"))
            ui.onNodeWithText("自以为是检测").assertDoesNotExist()
            ui.onNodeWithText("费曼复习").assertDoesNotExist()
            ui.onNodeWithText("验收考试").performTouchInput { click() }
            await("验收考试未解锁")
            ui.onNodeWithText("开始考试").assertDoesNotExist()
            pressBack()
            runBlocking { library.lightFromAssessment(skill) }
            await("1 / 1 已点亮")
            map.performScrollToNode(hasText("验收考试"))
            ui.onNodeWithText("验收考试").performTouchInput { click() }
            await("开始考试")
            fixture.ai.reply(examFixture())
            ui.onNodeWithText("开始考试").performTouchInput { click() }
            await("验收单选1")

            val paper = ui.onNodeWithTag("exam-questions")
            for (index in 0..6) {
                paper.performScrollToIndex(index)
                ui.onNodeWithTag("exam-choice-$index-0").performTouchInput { click() }
                if (index >= 4) ui.onNodeWithTag("exam-choice-$index-1").performTouchInput { click() }
            }
            for (index in 7..8) {
                paper.performScrollToIndex(index)
                val answer = ui.onNodeWithTag("exam-answer-$index")
                answer.performTouchInput { click() }
                ui.waitUntil(8_000) { imeVisible() }
                answer.performTextReplacement("核对金额")
                pressBack()
                ui.waitUntil(8_000) { !imeVisible() }
            }
            await("已答 9 / 10")
            ui.onNodeWithText("交卷").performTouchInput { click() }
            await("确认交卷？")
            fixture.ai.reply(markFixture())
            ui.onNodeWithText("确认交卷").performTouchInput { click() }
            await("90 分")
            ui.onNodeWithText("通过验收").assertIsDisplayed()
            ui.onNodeWithText("再考一次").assertIsEnabled()
            collectExamCertificate(tree.name)
            ui.onNodeWithText("查看错题集").performTouchInput { click() }
            await("验收填空3")
            ui.onAllNodesWithText("未作答")[0].assertIsDisplayed()
            pressBack()
            await("90 分")
            pressBack()

            ui.waitUntil(10_000) {
                ui.onAllNodes(
                    hasTestTag("challenge-reveal") and
                        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "挑战节点已显现"),
                ).fetchSemanticsNodes().isNotEmpty()
            }
            map.performScrollToNode(hasText("费曼复习"))
            ui.onNodeWithText("最高 90 分").assertIsDisplayed()
            ui.onNodeWithText("自以为是检测").assertIsDisplayed()
            ui.onNodeWithText("费曼复习").performTouchInput { click() }
            await("开始挑战")
            ui.onNodeWithText("开始挑战").performTouchInput { click() }
            await("请选一个你刚学过的核心概念", substring = true)
            fixture.ai.reply(challengeFixture("讲清楚了核对的方法。", true, ""))
            send("把付的钱和收据上的数字比一比。")
            await("挑战成功")
            pressBack()
            map.performScrollToNode(hasText("挑战成功"))
            ui.onNodeWithText("挑战成功").assertIsDisplayed()

            runBlocking {
                val exams = fixture.container.assessments
                fixture.ai.reply(examFixture("重考"))
                exams.startExam(tree.id)
                val retake = exams.watchAttempts(tree.id).first().first()
                fixture.ai.reply(markFixture())
                exams.submitExam(tree.id, retake.id, retake.answers)
            }
            map.performScrollToNode(hasText("验收考试"))
            ui.onNodeWithText("验收考试").performTouchInput { click() }
            await("未通过")
            ui.onNodeWithText("获取结业证书").assertDoesNotExist()
            ui.onNodeWithText("查看结业证书").assertDoesNotExist()

            runBlocking {
                profiles.save(profiles.current().copy(nickname = "更名后的学员"))
                library.delete(direction.id)
            }
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("tab-personal").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("tab-personal").performClick()
            await("更名后的学员")
            ui.onNodeWithText("查看全部").assertDoesNotExist()
            ui.onNodeWithTag("personal-home").performScrollToNode(hasTestTag("collected-certificate-${tree.id}"))
            ui.onNodeWithTag("collected-certificate-${tree.id}").performTouchInput { click() }
            ui.onNodeWithText("证书学员").assertIsDisplayed()
            ui.onNode(hasText(tree.name) and hasAnyAncestor(hasTestTag("exam-certificate"))).assertIsDisplayed()
            pressBack()
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithTag("personal-home").assertIsDisplayed()
            ui.onNodeWithTag("tab-personal").assertIsSelected()
        } finally {
            runBlocking {
                library.delete(tree.id)
                library.delete(direction.id)
                profiles.save(original)
            }
        }
    }

    private fun collectExamCertificate(treeName: String) {
        val entry = ui.onNodeWithText("获取结业证书")
        val envelope = ui.onNode(hasText("上滑拆开") and hasClickAction())
        entry.performTouchInput { click() }
        envelope.assertIsDisplayed()
        pressBack()
        envelope.assertDoesNotExist()
        ui.onNodeWithText("90 分").assertIsDisplayed()

        entry.performTouchInput { click() }
        envelope.performTouchInput { click() }
        envelope.performTouchInput { swipe(Offset(width * .15f, centerY), Offset(width * .85f, centerY)) }
        envelope.performTouchInput { swipe(Offset(centerX, height * .2f), Offset(centerX, height * .8f)) }
        ui.onNodeWithText("收下").assertDoesNotExist()
        envelope.performTouchInput { swipe(Offset(centerX, height * .85f), Offset(centerX, height * .15f)) }

        ui.onNodeWithText("结业证书").assertIsDisplayed()
        ui.onNode(hasText("90 分", substring = true) and hasAnyAncestor(hasTestTag("exam-certificate")))
            .assertDoesNotExist()
        ui.onNode(hasText(treeName) and hasAnyAncestor(hasTestTag("exam-certificate"))).assertIsDisplayed()
        val collect = ui.onNodeWithText("收下").assertIsEnabled()
        collect.performScrollTo().performTouchInput { click() }
        await("填写证书署名")
        ui.onNodeWithText("取消").performScrollTo().performClick()
        ui.onNodeWithText("结业证书").assertIsDisplayed()
        assertEquals(null, runBlocking { fixture.container.profiles.current().nickname })
        collect.performScrollTo().performTouchInput { click() }
        await("填写证书署名")
        ui.onNodeWithTag("certificate-nickname").performTextReplacement("  证书学员  ")
        ui.onNodeWithText("保存并收下").performScrollTo().performTouchInput { click() }
        ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("结业证书").assertDoesNotExist()
        ui.onNodeWithText("90 分").assertIsDisplayed()
        entry.assertDoesNotExist()

        ui.activityRule.scenario.recreate()
        await("查看结业证书")
        ui.onNodeWithText("查看结业证书").performTouchInput { click() }
        ui.onNodeWithText("结业证书").assertIsDisplayed()
        ui.onNodeWithText("证书学员").assertIsDisplayed()
        ui.onNodeWithTag("certificate-avatar").assertIsDisplayed()
        ui.onNodeWithText("隐藏该证书").assertDoesNotExist()
        ui.onNodeWithText("更多").assertIsDisplayed()
        envelope.assertDoesNotExist()
        ui.onNodeWithText("收下").assertDoesNotExist()
        ui.onNodeWithText("关闭").performTouchInput { click() }
        ui.onNodeWithText("结业证书").assertDoesNotExist()
    }

    @Test fun showsFourCertificateThemesWithoutScores() {
        await("LifeSkill")
        val library = fixture.container.library
        val exams = fixture.container.assessments
        val profiles = fixture.container.profiles
        val originalProfile = runBlocking {
            profiles.current().also { profiles.save(PersonalProfile("林知远", ProfileAvatar.Preset(AvatarPreset.CAT))) }
        }
        val direction = runBlocking { library.save(EntryEdit(null, null, EntryKind.DIRECTION, "结业证书样式")) }
        val examples = listOf(
            Triple(CertificateTheme.TECH, EntryIcon.CODE, "Python 自动化与脚本开发"),
            Triple(CertificateTheme.BUSINESS, EntryIcon.CHART, "财务分析与商业决策"),
            Triple(CertificateTheme.COMMUNICATION, EntryIcon.SPEECH, "结构化表达与公众演讲"),
            Triple(CertificateTheme.ARTS, EntryIcon.CAMERA, "街头摄影与城市影像叙事"),
        )
        try {
            runBlocking {
                examples.forEach { (_, icon, name) ->
                    val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, name, icon = icon))
                    val skill = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "主题实践", "完成一个主题案例"))
                    library.lightFromAssessment(skill)
                    fixture.ai.reply(examFixture())
                    exams.startExam(tree.id)
                    val attempt = exams.watchAttempts(tree.id).first().single()
                    fixture.ai.reply(markFixture())
                    exams.submitExam(tree.id, attempt.id, attempt.questions.map { question ->
                        ExamAnswer(question.correctChoices, "核对金额")
                    })
                    fixture.container.certificates.issue(tree.id, attempt.id)
                }
            }
            val directionTag = "direction-card-${direction.id}"
            ui.onNodeWithTag("learning-directions").performScrollToNode(hasTestTag(directionTag))
            ui.onNodeWithTag(directionTag).performTouchInput { click() }
            await(direction.name)
            examples.forEach { (theme, _, name) ->
                ui.onNodeWithTag("skill-tree-list").performScrollToNode(hasText(name))
                ui.onNode(hasText(name) and hasClickAction()).performClick()
                ui.onNodeWithTag("skill-map").performScrollToNode(hasText("验收考试"))
                ui.onNodeWithText("验收考试").performTouchInput { click() }
                await("100 分")
                ui.onNodeWithText("查看结业证书").performTouchInput { click() }

                val paperTag = "certificate-paper-${theme.name.lowercase()}"
                ui.onNodeWithTag(paperTag).assertIsDisplayed()
                ui.onNode(hasText(name) and hasAnyAncestor(hasTestTag(paperTag))).assertIsDisplayed()
                ui.onNodeWithText("林知远").assertIsDisplayed()
                ui.onNodeWithTag("certificate-avatar").assertIsDisplayed()
                ui.onNodeWithTag("certificate-collected-at").assertIsDisplayed()
                ui.onNodeWithText("首次收下", substring = true).assertIsDisplayed()
                ui.onNode(hasText("100 分", substring = true) and hasAnyAncestor(hasTestTag(paperTag))).assertDoesNotExist()
                ui.onNodeWithText("更多").assertIsDisplayed()
                ui.onNodeWithText("销毁证书").assertDoesNotExist()
                captureScreenshot("captureCertificateScreenshots", "${theme.name.lowercase()}.png")
                ui.onNodeWithText("关闭").performTouchInput { click() }
                ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithContentDescription("返回").performClick()
                ui.waitUntil(8_000) { ui.onAllNodesWithTag("skill-map").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("返回").performClick()
                await(direction.name)
            }
            ui.onNodeWithContentDescription("返回").performClick()
            await("LifeSkill")
            ui.onNodeWithTag("tab-personal").performTouchInput { click() }
            await("证书展柜")
            ui.onNodeWithText("获取证书", substring = true).assertIsDisplayed()
            ui.onNodeWithText("收藏证书", substring = true).assertDoesNotExist()
            ui.onNodeWithText("查看全部").assertDoesNotExist()
            val cabinet = ui.onNodeWithTag("personal-home")
            examples.forEach { (_, _, name) -> cabinet.performScrollToNode(hasText(name) and hasClickAction()) }
            val certificate = hasText(examples.last().third) and hasClickAction()
            val archived = runBlocking {
                fixture.container.certificates.certificates.first().single { it.treeName == examples.last().third }
            }
            ui.onNode(certificate).performTouchInput { click() }
            ui.onNodeWithTag("certificate-paper-arts").assertIsDisplayed()
            captureScreenshot("captureCertificateScreenshots", "cabinet-certificate.png")
            ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
            ui.onNodeWithText("隐藏该证书").performTouchInput { click() }
            ui.waitUntil(8_000) {
                runBlocking {
                    fixture.container.certificates.watchCertificate(archived.treeId).first().issued?.hidden == true
                }
            }
            ui.onNodeWithText("关闭").performClick()
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
            ui.onNode(certificate).assertDoesNotExist()
            cabinet.performScrollToNode(hasText("已隐藏（1）"))
            ui.onNodeWithText("已隐藏（1）").performClick()
            await("已隐藏的证书")
            ui.onNode(certificate).performClick()
            ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
            ui.onNodeWithText("取消隐藏").performTouchInput { click() }
            ui.waitUntil(8_000) {
                runBlocking {
                    fixture.container.certificates.watchCertificate(archived.treeId).first().issued?.hidden == false
                }
            }
            ui.onNodeWithText("关闭").performClick()
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
            cabinet.performScrollToNode(certificate)
            ui.onNodeWithText("已隐藏（1）").assertDoesNotExist()
            cabinet.performScrollToIndex(0)
            captureScreenshot("capturePersonalScreenshots", "cabinet.png")

            val countBefore = runBlocking { fixture.container.certificates.certificates.first().count { !it.isSample } }
            val countBeforeDestroy = hasText("获取证书") and hasText(countBefore.toString())
            cabinet.performScrollToNode(countBeforeDestroy)
            ui.onNode(countBeforeDestroy).assertIsDisplayed()
            cabinet.performScrollToNode(certificate)
            ui.onNode(certificate).performTouchInput { click() }
            ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
            ui.onNodeWithText("销毁证书").performTouchInput { click() }
            await("销毁证书？")
            ui.onNodeWithText("确认销毁").performTouchInput { click() }
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
            val countAfterDestroy = hasText("获取证书") and hasText((countBefore - 1).toString())
            cabinet.performScrollToNode(countAfterDestroy)
            ui.onNode(countAfterDestroy).assertIsDisplayed()
            ui.onNode(certificate).assertDoesNotExist()

            val tree = runBlocking { library.snapshot().childrenOf(direction.id).single { it.name == examples.last().third } }
            runBlocking {
                val attempt = exams.watchAttempts(tree.id).first().single()
                val failure = runCatching { fixture.container.certificates.issue(tree.id, attempt.id) }.exceptionOrNull()
                assertTrue(failure is RuleViolation && failure.message == "这张证书已销毁。")
            }
            pressBack()
            ui.onNodeWithTag("learning-directions").performScrollToNode(hasTestTag(directionTag))
            ui.onNodeWithTag(directionTag).performClick()
            ui.onNodeWithTag("skill-tree-list").performScrollToNode(hasText(tree.name))
            ui.onNode(hasText(tree.name) and hasClickAction()).performClick()
            ui.onNodeWithTag("skill-map").performScrollToNode(hasText("验收考试"))
            ui.onNodeWithText("验收考试").performClick()
            await("100 分")
            ui.onNodeWithText("获取结业证书").assertDoesNotExist()
            ui.onNodeWithText("查看结业证书").assertDoesNotExist()
        } finally {
            runBlocking {
                library.delete(direction.id)
                profiles.save(originalProfile)
            }
        }
    }

    @Test fun sampleCertificateCanBeHiddenAndDestroyedWithoutAffectingStatistics() {
        await("LifeSkill")
        ui.onNodeWithTag("tab-personal").performTouchInput { click() }
        await("证书展柜")
        val archive = fixture.container.certificates
        val certificates = runBlocking { archive.certificates.first() }
        val sample = certificates.single { it.isSample }
        val count = certificates.count { !it.isSample }
        val cabinet = ui.onNodeWithTag("personal-home")
        val metric = hasText("获取证书") and hasText(count.toString())
        cabinet.performScrollToNode(metric)
        ui.onNode(metric).assertIsDisplayed()
        val sampleTag = "collected-certificate-${sample.treeId}"
        cabinet.performScrollToNode(hasTestTag(sampleTag))
        ui.onNodeWithTag(sampleTag).assert(hasText("示例 · ${sample.treeName}")).performTouchInput { click() }
        ui.onNodeWithText("示例证书").assertIsDisplayed()
        ui.onNodeWithTag("certificate-collected-at").assertIsDisplayed()
        val sampleDate = checkNotNull(sample.collectedAt)
        ui.onNodeWithText(
            "展示日期  ${sampleDate.year}年${sampleDate.monthValue}月${sampleDate.dayOfMonth}日",
        ).assertIsDisplayed()
        ui.onNodeWithText("隐藏该证书").assertDoesNotExist()
        ui.onNodeWithText("销毁证书").assertDoesNotExist()

        ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("隐藏该证书").assertIsDisplayed()
        ui.onNodeWithText("销毁证书").assertIsDisplayed()
        ui.onNodeWithText("销毁证书").performTouchInput { click() }
        await("销毁证书？")
        pressBack()
        ui.onNodeWithText("确认销毁").assertDoesNotExist()
        ui.onNodeWithText("示例证书").assertIsDisplayed()
        ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("销毁证书").performTouchInput { click() }
        ui.onNodeWithText("取消").performTouchInput { click() }
        ui.onNodeWithText("示例证书").assertIsDisplayed()
        ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("隐藏该证书").performTouchInput { click() }
        ui.waitUntil(8_000) {
            runBlocking {
                archive.watchCertificate(sample.treeId).first().issued?.hidden == true
            }
        }
        ui.onNodeWithText("关闭").performTouchInput { click() }
        ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag(sampleTag).assertDoesNotExist()
        cabinet.performScrollToNode(hasText("已隐藏（1）"))
        ui.onNodeWithText("已隐藏（1）").performTouchInput { click() }
        ui.onNodeWithTag("hidden-certificate-${sample.treeId}").performTouchInput { click() }
        ui.onNodeWithText("更多").performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("销毁证书").performTouchInput { click() }
        ui.onNodeWithText("确认销毁").performTouchInput { click() }
        ui.waitUntil(8_000) { ui.onAllNodesWithTag("exam-certificate").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("已隐藏的证书").assertDoesNotExist()
        ui.onNodeWithText("已隐藏（1）").assertDoesNotExist()
        ui.onNodeWithTag(sampleTag).assertDoesNotExist()
        cabinet.performScrollToNode(metric)
        ui.onNode(metric).assertIsDisplayed()
    }

    private fun captureScreenshot(argument: String, filename: String) {
        if (InstrumentationRegistry.getArguments().getString(argument) != "true") return
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.waitForIdle(200, 3_000)
        val screenshot = checkNotNull(automation.takeScreenshot())
        try {
            val directory = File(ui.activity.cacheDir, "certificate-screenshots").apply { mkdirs() }
            File(directory, filename).outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }

    @Test fun skillMapWrapsIntoTwoColumnsAndOnlyScrollsVertically() {
        await("本职工作")
        val library = fixture.container.library
        val tree = runBlocking {
            val direction = library.snapshot().childrenOf(null).first { it.name == "本职工作" }
            val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, "纵向地图"))
            repeat(16) { index ->
                library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "技能编号 ${index + 1}", "掌握本节概念"))
            }
            tree
        }
        try {
            ui.onNodeWithText("本职工作").performClick()
            await(tree.name)
            ui.onNodeWithText(tree.name).performClick()
            val map = ui.onNodeWithTag("skill-map")
            val first = ui.onNodeWithContentDescription("技能编号 1")
            val second = ui.onNodeWithContentDescription("技能编号 2")
            val third = ui.onNodeWithContentDescription("技能编号 3")
            val before = first.fetchSemanticsNode().boundsInRoot
            val secondBounds = second.fetchSemanticsNode().boundsInRoot
            assertEquals(before.top, secondBounds.top, .5f)
            assertTrue(before.right < secondBounds.left)
            assertTrue(third.fetchSemanticsNode().boundsInRoot.top > before.bottom)
            val center = before.center - map.fetchSemanticsNode().boundsInRoot.topLeft
            map.performTouchInput {
                swipe(center - Offset(42f, 0f), center + Offset(42f, 0f))
            }
            assertEquals(before, first.fetchSemanticsNode().boundsInRoot)
            map.performTouchInput {
                pinch(
                    center - Offset(24f, 0f), center - Offset(80f, 0f),
                    center + Offset(24f, 0f), center + Offset(80f, 0f),
                )
            }
            assertEquals(before, first.fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithContentDescription("放大地图").assertDoesNotExist()
            map.performTouchInput {
                swipe(Offset(width * .50f, height * .88f), Offset(width * .50f, height * .14f))
            }
            first.assertIsNotDisplayed()
            map.performScrollToIndex(7)
            ui.onNodeWithContentDescription("技能编号 16").performTouchInput { click() }
            await("技能编号 16")
            pressBack()
            ui.onNodeWithContentDescription("技能编号 16").assertIsDisplayed()
        } finally {
            runBlocking { library.delete(tree.id) }
        }
    }

    @Test fun newlySavedSkillScrollsIntoViewAndDuplicateKeepsForm() {
        await("本职工作")
        runBlocking {
            val library = fixture.container.library
            val direction = library.snapshot().childrenOf(null).first { it.name == "本职工作" }
            val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, "本地保存长地图"))
            repeat(32) { index ->
                library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "练习节点 ${index + 1}", "完成练习"))
            }
        }
        ui.onNodeWithText("本职工作").performClick()
        await("本地保存长地图")
        ui.onNodeWithText("本地保存长地图").performClick()
        ui.onNodeWithText("新增技能点").performClick()
        val nameInput = ui.onNode(hasSetTextAction() and hasText("名称"))
        nameInput.performTextInput("练习节点 1")
        fill("技能说明", "新增技能说明")
        ui.onNodeWithText("保存").performClick()
        await("这一层已有同名项目", substring = true)
        nameInput.assertIsDisplayed().performTextReplacement("新增可见节点")
        ui.onNodeWithText("保存").performClick()
        await("0 / 33 已点亮")
        ui.onNodeWithContentDescription("新增可见节点").assertIsDisplayed().performTouchInput { click() }
        await("新增技能说明")
    }

    @Test fun prerequisitePickerDisablesEarlierTiersAfterChoosingAnAdjacentSkill() {
        await("本职工作")
        val library = fixture.container.library
        val tree = runBlocking {
            val direction = library.snapshot().childrenOf(null).first { it.name == "本职工作" }
            val tree = library.save(EntryEdit(null, direction.id, EntryKind.TREE, "层级选择验证"))
            val root = library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "基础层", "掌握基础"))
            library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "进阶层", "完成进阶练习", setOf(root.id)))
            tree
        }
        try {
            ui.onNodeWithText("本职工作").performClick()
            await(tree.name)
            ui.onNodeWithText(tree.name).performClick()
            ui.onNodeWithText("新增技能点").performClick()
            fill("名称", "应用层")
            fill("技能说明", "完成综合练习")
            ui.onNodeWithText("前置节点").performScrollTo().performTouchInput { click() }
            val rootChoice = ui.onNode(hasText("基础层") and isToggleable())
            val middleChoice = ui.onNode(hasText("进阶层") and isToggleable())
            rootChoice.assertIsEnabled()
            middleChoice.performScrollTo().performTouchInput { click() }
            rootChoice.assertIsNotEnabled()
            middleChoice.performScrollTo().performTouchInput { click() }
            rootChoice.assertIsEnabled()
            middleChoice.performScrollTo().performTouchInput { click() }
            ui.onNodeWithText("保存").performClick()
            await("0 / 3 已点亮")
            runBlocking {
                val saved = library.snapshot()
                val added = saved.childrenOf(tree.id).single { it.name == "应用层" }
                assertEquals(listOf("进阶层"), saved.prerequisitesOf(added.id).map { saved.byId.getValue(it).name })
            }
        } finally {
            runBlocking { library.delete(tree.id) }
        }
    }

    @Test fun manualLibraryGenerationMapAndTutorWorkThroughTheirScreens() {
        await("本职工作")
        openSettings()
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
        ui.onNodeWithTag("tab-learning").performClick()
        await("本职工作")
        ui.onNodeWithText("新增方向").performClick()
        val nameInput = ui.onNode(hasSetTextAction() and hasText("名称"))
        nameInput.performTouchInput { click() }
        nameInput.performTextInput("测".repeat(49))
        await("名称最多 48 个字符")
        ui.onNodeWithText("保存").assertIsDisplayed().assertIsNotEnabled()
        pressBack()
        nameInput.assertIsDisplayed()
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
        ui.onNodeWithText("学习技能").assertIsEnabled()
        ui.onNodeWithText("手动点亮").assertDoesNotExist()
        ui.onNodeWithText("取消点亮").assertDoesNotExist()
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("编辑技能点").performClick()
        ui.onNode(hasSetTextAction() and hasText("名称")).performTextReplacement("已编辑技能点")
        ui.onNodeWithText("保存").performClick()
        await("已编辑技能点")
        ui.onNodeWithContentDescription("关闭技能详情").performClick()
        await("0 / 1 已点亮")
        ui.onNodeWithText("新增技能点").performClick()
        fill("名称", "练习依赖")
        val goalInput = ui.onNode(hasSetTextAction() and hasText("技能说明"))
        goalInput.performTextInput("练".repeat(321))
        await("技能说明最多 320 个字符")
        ui.onNodeWithText("保存").assertIsNotEnabled()
        goalInput.performTextReplacement("使用前置概念完成一项练习")
        ui.onNodeWithText("前置节点").performScrollTo().performTouchInput { click() }
        ui.onNode(hasText("已编辑技能点") and isToggleable()).performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("保存").performClick()
        await("0 / 2 已点亮")
        ui.onNodeWithContentDescription("练习依赖").performTouchInput { click() }
        await("前置技能")
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("删除技能点").performClick()
        ui.onNodeWithText("取消").performClick()
        await("前置技能")
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("删除技能点").performClick()
        ui.onNodeWithText("删除").performClick()
        await("0 / 1 已点亮")
        pressBack()
        await("新增技能树")
        pressBack()
        await("AI 生成技能树")

        fixture.ai.enqueue { throw RuleViolation("模型服务请求过于频繁，请稍后重试。") }
        fixture.ai.reply("""{"kind":"message","reply":"你想用什么主题？"}""")
        fixture.ai.reply(RepositoryTest.TREE_OFFER)
        fixture.ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("按每个技能点的名称和学习目标选择 icon"))
            assertTrue(messages.first().text.contains("树的 icon 应代表树名及整棵树的学习主题"))
            assertTrue(messages.first().text.contains("本次掌握程度为「进阶」"))
            assertTrue(messages.first().text.contains("精确生成 2 节"))
            RepositoryTest.CREATED
        }
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
        ui.onNodeWithText("重试").assertDoesNotExist()
        ui.onNodeWithText("撤回编辑").assertDoesNotExist()
        send("请生成一个数据质量方向的技能树")
        await("当前掌握程度")
        ui.onNodeWithText("通用").assertDoesNotExist()
        ui.onNodeWithText("确认生成").assertIsNotEnabled()
        pressBack()
        await("生成设置")
        ui.onNodeWithText("当前掌握程度").assertDoesNotExist()
        assertEquals(TurnKind.TREE_OFFER, runBlocking {
            fixture.container.conversations.watch(ChatScope(ChatMode.GENERATE)).first().last().kind
        })
        ui.onNodeWithText("生成设置").performTouchInput { click() }
        ui.onNodeWithText("进阶").performTouchInput { click() }.assertIsSelected()
        ui.onNodeWithText("大致了解（3–5 节）").performScrollTo().performTouchInput { click() }.assertIsSelected()
        ui.onNodeWithText("确认生成").assertIsEnabled()
        ui.onNodeWithText("自定义").performScrollTo().performTouchInput { click() }.assertIsSelected()
        ui.onNodeWithText("确认生成").assertIsNotEnabled()
        val count = ui.onNode(hasSetTextAction() and hasText("课数（1–33）"))
        count.performScrollTo().performTouchInput { click() }
        count.performTextInput("34")
        await("请输入 1–33 的整数")
        ui.onNodeWithText("确认生成").assertIsNotEnabled()
        pressBack()
        await("当前掌握程度")
        count.performTextReplacement("2")
        ui.onNodeWithText("确认生成").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        await("选择学习方向")
        ui.onNodeWithText("选择方向").assertDoesNotExist()
        ui.onNodeWithText("确认添加").assertIsNotEnabled()
        ui.onNodeWithText("数据质量").assertDoesNotExist()
        pressBack()
        ui.onNodeWithText("选择学习方向").assertDoesNotExist()
        assertTrue(runBlocking { fixture.container.library.snapshot().entries.none { it.name == "数据质量" } })
        val draftBubble = ui.onNode(hasText("已生成 1 棵技能树") and hasClickAction())
        draftBubble.performTouchInput { click() }
        await("选择学习方向")
        ui.activityRule.scenario.recreate()
        await("选择学习方向")
        ui.onNodeWithText("新建 · 数据能力").performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("方向名称").assertDoesNotExist()
        ui.onNodeWithText("确认添加").assertIsEnabled().performTouchInput { click() }
        await("已新增")
        ui.onNodeWithText("技能点").assertIsDisplayed()
        ui.onNodeWithContentDescription("打开技能树 · 数据质量").performClick()
        await("技能地图")
        assertEquals(EntryIcon.TARGET, iconOf("数据质量"))
        assertEquals(EntryIcon.CHART, iconOf("基础"))
        assertEquals(EntryIcon.TOOLS, iconOf("实践"))

        val practiceNode = ui.onNodeWithContentDescription("实践")
        practiceNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未解锁"))
        practiceNode.performTouchInput { click() }
        await("点亮全部前置技能后解锁")
        ui.onNodeWithText("手动点亮").assertDoesNotExist()
        ui.onNodeWithText("学习技能").assertIsNotEnabled().performTouchInput { click() }
        ui.onNodeWithText("AI 老师").assertDoesNotExist()
        val masteryCheck = ui.onNode(hasText("检验掌握") and hasClickAction())
        ui.onNodeWithContentDescription("关闭技能详情").performClick()
        ui.onNodeWithContentDescription("基础").performTouchInput { click() }
        await("可点亮")
        ui.onNodeWithText("学习技能").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("开始学习")
        fixture.ai.reply("""{"kind":"offer_check","reply":"基础知识已讲完。"}""")
        ui.onNodeWithText("开始学习").performClick()
        await("可以试试「检验掌握」")
        fixture.ai.reply("""{"kind":"exercise","reply":"先做哪一步？","choices":["核对数据","提交结果"]}""")
        masteryCheck.performTouchInput { click() }
        await("先做哪一步？")
        fixture.ai.enqueue { messages ->
            assertEquals("A. 核对数据", messages.last().text)
            val id = JSONObject(messages.first().text.substringAfter("当前待答练习：").lineSequence().first()).getString("id")
            RepositoryTest.verdict("passed", "掌握基础知识。", id)
        }
        ui.onNodeWithText("A. 核对数据").performTouchInput { click() }
        await("已点亮技能点", substring = true)
        val closeCelebration = ui.onNodeWithContentDescription("关闭庆祝")
        closeCelebration.assertIsDisplayed().performTouchInput { click() }
        ui.waitUntil(8_000) { ui.onAllNodesWithTag("skill-celebration").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription("返回").performClick()
        await("技能地图")
        ui.onNodeWithText("取消点亮").assertDoesNotExist()
        ui.onNodeWithContentDescription("关闭技能详情").assertDoesNotExist()
        practiceNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "可点亮，下一步"))
        practiceNode.performTouchInput { click() }
        ui.onNodeWithText("学习技能").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("开始学习")
        masteryCheck.assertDoesNotExist()
        val fence = "```"
        val lesson = "## 数据核对 💡\n\n| 对象 | 特点 |\n| --- | --- |\n| 文本 | 原样 |\n\n" +
            "${fence}text\nA{读取} --> B[核对]\nB -->|继续迭代| A\n$fence"
        fixture.ai.reply(JSONObject().put("kind", "teach").put("reply", lesson).toString())
        ui.onNodeWithText("开始学习").performClick()
        await("数据核对 💡")
        await("原样")
        await("读取")
        await("继续迭代")
        masteryCheck.assertDoesNotExist()
        fixture.ai.reply("没有按 JSON 格式输出的讲解。")
        fixture.ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("上一次回复未通过格式校验"))
            val previous = JSONObject(messages.single { it.role == "assistant" }.text)
            assertEquals("teach", previous.getString("kind"))
            assertEquals(lesson, previous.getString("reply"))
            assertEquals("好的，继续", messages.last().text)
            """{"kind":"offer_check","reply":"现在已经讲完核对步骤。"}"""
        }
        ui.onNodeWithTag("chat-messages").performScrollToNode(hasText("好的，继续"))
        val continueReply = ui.onNode(hasText("好的，继续") and hasClickAction())
        val examReply = ui.onNode(hasText(TutorRules.EXAM_REPLY) and hasClickAction())
        examReply.assertDoesNotExist()
        continueReply.assertIsEnabled().performTouchInput { click() }
        await("可以试试「检验掌握」")
        masteryCheck.assertIsEnabled()
        examReply.assertDoesNotExist()
        listOf("先核对记录来源。", "再确认字段类型。").forEach { reply ->
            fixture.ai.reply(JSONObject().put("kind", "teach").put("reply", reply).toString())
            continueReply.performTouchInput { click() }
            await(reply)
            examReply.assertDoesNotExist()
        }
        fixture.ai.reply("""{"kind":"teach","reply":"核对必填字段是最后一步。"}""")
        continueReply.performTouchInput { click() }
        await(TutorRules.EXAM_REPLY)
        continueReply.assertIsDisplayed()
        fixture.ai.enqueue { throw RuleViolation("考试请求暂时失败，请重试。") }
        fixture.ai.enqueue { messages ->
            assertTrue(messages.last().text.contains("App 发起检验掌握"))
            """{"kind":"exercise","reply":"怎样识别异常记录？","choices":["直接删除","核对必填字段","全部保留"]}"""
        }
        examReply.assertIsEnabled().performTouchInput { click() }
        await("考试请求暂时失败，请重试。")
        ui.onNodeWithText("重试").performClick()
        await("怎样识别异常记录？")
        ui.onNodeWithText("重试").assertDoesNotExist()
        ui.onNodeWithText("撤回检验").assertDoesNotExist()
        masteryCheck.assertDoesNotExist()
        examReply.assertDoesNotExist()
        continueReply.assertDoesNotExist()
        val exerciseId = runBlocking {
            val practice = fixture.container.library.snapshot().entries.single { it.kind == EntryKind.SKILL && it.name == "实践" }
            fixture.container.conversations.watch(ChatScope(ChatMode.TUTOR, practice.id)).first().last().id
        }
        fixture.ai.enqueue { messages ->
            assertEquals("A. 直接删除", messages.last().text)
            RepositoryTest.verdict("retry", "先判断记录缺少什么。", exerciseId)
        }
        ui.onNode(hasText("A. 直接删除") and hasClickAction()).performTouchInput { click() }
        await("先判断记录缺少什么。")
        examReply.assertDoesNotExist()
        continueReply.assertDoesNotExist()
        fixture.ai.enqueue { messages ->
            assertEquals("B. 核对必填字段", messages.last().text)
            assertTrue(messages.first().text.contains("A. 直接删除"))
            RepositoryTest.verdict("passed", "你给出了可核验的标准。", exerciseId)
        }
        ui.mainClock.autoAdvance = false
        try {
            ui.onNode(hasText("B. 核对必填字段") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { it() }
            ui.waitUntil(8_000) {
                ui.mainClock.advanceTimeByFrame()
                ui.onAllNodesWithText("已点亮技能点", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithTag("skill-celebration").assertIsDisplayed()
            ui.mainClock.advanceTimeBy(2_000)
            ui.onNodeWithTag("celebration-fireworks").assertExists()
            ui.mainClock.advanceTimeBy(1_100)
            ui.onNodeWithTag("celebration-fireworks").assertDoesNotExist()
            closeCelebration.assertIsDisplayed()
        } finally {
            ui.mainClock.autoAdvance = true
        }
        pressBack()
        closeCelebration.assertIsDisplayed()
        ui.onNodeWithTag("skill-celebration").performTouchInput { click(Offset(20f, 40f)) }
        closeCelebration.assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        await("已点亮技能点", substring = true)
        ui.onNodeWithTag("celebration-fireworks").assertDoesNotExist()
        closeCelebration.assertIsDisplayed().performClick()
        ui.waitUntil(8_000) { ui.onAllNodesWithTag("skill-celebration").fetchSemanticsNodes().isEmpty() }
        pressBack()
        await("2 / 2 已点亮")
        ui.onNodeWithContentDescription("关闭技能详情").assertDoesNotExist()
        practiceNode.performTouchInput { click() }
        await("已点亮 · AI 检验通过")
        ui.onNodeWithText("重新学习").assertDoesNotExist()
        ui.onNodeWithContentDescription("重新学习").assertDoesNotExist()
        ui.onNodeWithText("学习技能").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("已点亮技能点", substring = true)
        ui.onNodeWithTag("skill-celebration").assertDoesNotExist()
        fixture.ai.enqueue { messages ->
            assertEquals(2, messages.size)
            assertTrue(messages.last().text.contains("改用账单举例"))
            """{"kind":"teach","reply":"从核对账单开始讲。"}"""
        }
        ui.onNodeWithContentDescription("重新学习").performTouchInput { click() }
        await("开始新一轮")
        await("开始后将清空此技能的旧对话，且无法恢复。点亮进度会保留。")
        ui.onNodeWithTag("restart-requirements").performTextInput("改用账单举例")
        ui.onNodeWithText("开始新一轮").performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("新一轮学习")
        await("从核对账单开始讲。")
        continueReply.assertIsDisplayed()
        examReply.assertDoesNotExist()
        ui.onNodeWithText("已点亮技能点", substring = true).assertDoesNotExist()
        masteryCheck.assertDoesNotExist()
        ui.onNodeWithContentDescription("重新学习").performSemanticsAction(SemanticsActions.OnClick) { it() }
        await("开始新一轮")
        ui.onNodeWithText("取消").performClick()
        await("从核对账单开始讲。")
        // 底层消息在弹窗关闭前也可读，系统返回要等窗口焦点回到对话页。
        ui.waitUntil(8_000) { ui.runOnUiThread { ui.activity.hasWindowFocus() } && !imeVisible() }
        pressBack()
        await("2 / 2 已点亮")
    }

    private fun iconOf(name: String): EntryIcon? = runBlocking {
        fixture.container.library.snapshot().entries.single { it.name == name }.icon
    }

    private fun send(text: String) {
        ui.onNodeWithTag("chat-input").performTextReplacement(text)
        // 文本测试会唤起键盘。用发送语义避免把按键测试和键盘动画的坐标变化混在一起。
        ui.onNodeWithContentDescription("发送").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun openSettings() {
        ui.onNodeWithTag("tab-personal").performClick()
        ui.onNodeWithContentDescription("设置").performClick()
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
