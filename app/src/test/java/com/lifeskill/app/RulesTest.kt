package com.lifeskill.app

import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.Exercise
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.SkillGraph
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RulesTest {
    @Test fun graphUsesLongestPathForSharedPrerequisites() {
        val skills = listOf("root", "branch", "tip", "other")
        val edges = listOf(Edge("tip", "root"), Edge("branch", "root"), Edge("tip", "branch"))
        SkillGraph.validate(skills.toSet(), edges)
        assertEquals(listOf(listOf("root", "other"), listOf("branch"), listOf("tip")), SkillGraph.tiers(skills, edges))
    }

    @Test fun graphRejectsSelfEdgesCyclesAndForeignPrerequisites() {
        val skills = setOf("alpha", "beta")
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(skills, listOf(Edge("alpha", "alpha"))) }
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(skills, listOf(Edge("alpha", "foreign"))) }
        assertThrows(RuleViolation::class.java) {
            SkillGraph.validate(skills, listOf(Edge("alpha", "beta"), Edge("beta", "alpha")))
        }
    }

    @Test fun duplicatesAreDetectedAfterWhitespaceCaseAndWidthNormalization() {
        assertThrows(RuleViolation::class.java) { LibraryRules.checkUnique("  ＡＩ ", listOf("ai")) }
    }

    @Test fun verdictRequiresTheActiveExerciseAndAnAnswer() {
        val input = Turn("user", Speaker.USER, TurnKind.MESSAGE, "我的回答")
        val exercise = Exercise("exercise", "题目")
        assertThrows(RuleViolation::class.java) {
            TutorRules.validateVerdict(TurnKind.PASSED, exercise.id, null, input)
        }
        assertThrows(RuleViolation::class.java) {
            TutorRules.validateVerdict(TurnKind.PASSED, "other", exercise, input)
        }
        assertThrows(RuleViolation::class.java) {
            TutorRules.validateVerdict(TurnKind.PASSED, exercise.id, exercise, input.copy(kind = TurnKind.CHECK))
        }
        TutorRules.validateVerdict(TurnKind.PASSED, exercise.id, exercise, input)
    }

    @Test fun anOffTopicReplyDoesNotConsumeThePendingExercise() {
        val question = Turn("exercise", Speaker.ASSISTANT, TurnKind.EXERCISE, "题目", exerciseId = "exercise")
        val offTopic = Turn("off", Speaker.ASSISTANT, TurnKind.OFF_TOPIC, "回到学习主题")
        assertEquals(Exercise("exercise", "题目"), TutorRules.pendingExercise(listOf(question, offTopic)))
        assertNull(TutorRules.pendingExercise(listOf(question, offTopic.copy(kind = TurnKind.PASSED))))
    }

    @Test fun customEndpointAcceptsAVersionPathButRejectsCleartextAndCredentials() {
        val config = ModelSettings(Provider.CUSTOM, "https://example.com/v1/", " demo ", " demo-key ")
        assertEquals("https://example.com/v1", config.validated().baseUrl)
        for (url in listOf(
            "http://example.com/v1",
            "https://user@example.com",
            "https://example.com/v1?token=secret",
            "https://example.com/v1/chat/completions",
        )) {
            assertThrows(RuleViolation::class.java) { config.copy(baseUrl = url).validated() }
        }
    }
}
