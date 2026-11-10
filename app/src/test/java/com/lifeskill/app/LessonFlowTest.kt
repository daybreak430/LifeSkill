package com.lifeskill.app

import com.lifeskill.app.model.LessonFlow
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LessonFlowTest {
    @Test fun parsesBranchesAndRejectsUnsupportedOrCyclicFlows() {
        val flow = LessonFlow.parse("flowchart TD\nA{核对} -->|通过| B[保存]\nA -->|失败| C[修正]")!!
        assertEquals(listOf(listOf("A"), listOf("B", "C")), flow.tiers)
        assertEquals("核对", flow.labels["A"])
        assertEquals(setOf("A"), flow.decisions)
        assertEquals(listOf("通过", "失败"), flow.arrows.map { it.label })
        assertNull(LessonFlow.parse("flowchart TD\nA[核对] --> B[保存]\nB --> A"))
        assertNull(LessonFlow.parse("flowchart TD\nA --> B\nclick A \"https://example.com\""))
    }

    @Test fun compactLabelsKeepFullNameSeparate() {
        assertEquals("学习数据清", LibraryRules.mapName("学习数据清理"))
        assertEquals("画课", LibraryRules.mapName("画"))
        assertEquals("数据清理", LibraryRules.shortName(" 数据清理 "))
        assertThrows(RuleViolation::class.java) { LibraryRules.shortName("超长技能名称") }
    }

    @Test fun exerciseIncludesItsChoicesAndQuickReplyCannotCountAsAnAnswer() {
        val quiz = Turn("quiz", Speaker.ASSISTANT, TurnKind.EXERCISE, "讲解", exerciseId = "quiz", choices = listOf("检查", "跳过"))
        assertEquals("讲解\nA. 检查\nB. 跳过", quiz.modelText)
        val input = Turn("input", Speaker.USER, TurnKind.MESSAGE, TutorRules.CONTINUE_REPLY)
        val pending = TutorRules.pendingExercise(listOf(quiz, input))
        assertThrows(RuleViolation::class.java) {
            TutorRules.validateVerdict(TurnKind.PASSED, quiz.id, pending, input)
        }
    }
}
