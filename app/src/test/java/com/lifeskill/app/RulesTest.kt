package com.lifeskill.app

import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Exercise
import com.lifeskill.app.model.HomeStudyState
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.Progress
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.SkillGraph
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import com.lifeskill.app.model.homeStudy
import com.lifeskill.app.model.unlocksSince
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RulesTest {
    @Test fun graphUsesLongestPathForSharedPrerequisites() {
        val skills = listOf("root", "branch", "tip", "other")
        val edges = listOf(Edge("tip", "root"), Edge("branch", "root"), Edge("tip", "branch"))
        assertEquals(listOf(listOf("root", "other"), listOf("branch"), listOf("tip")), SkillGraph.tiers(skills, edges))
    }

    @Test fun graphAllowsSeveralParentsOnlyFromThePreviousTier() {
        val ids = setOf("root", "left", "right", "target")
        val valid = listOf(Edge("left", "root"), Edge("right", "root"), Edge("target", "left"), Edge("target", "right"))
        SkillGraph.validate(ids, valid)

        val legacy = listOf(Edge("left", "root"), Edge("target", "left"), Edge("target", "root"))
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(ids, legacy) }
        SkillGraph.validate(ids, legacy, legacy)
        SkillGraph.validate(ids, legacy.dropLast(1), legacy)
    }

    @Test fun editingAnUpstreamSkillCannotTurnAnExistingLinkIntoASkippedTier() {
        val ids = setOf("start", "left", "right", "target")
        val previous = listOf(Edge("target", "left"), Edge("target", "right"))
        assertThrows(RuleViolation::class.java) {
            SkillGraph.validate(ids, previous + Edge("left", "start"), previous)
        }
    }

    @Test fun graphRejectsSelfEdgesCyclesAndForeignPrerequisites() {
        val skills = setOf("alpha", "beta")
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(skills, listOf(Edge("alpha", "alpha"))) }
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(skills, listOf(Edge("alpha", "foreign"))) }
        assertThrows(RuleViolation::class.java) {
            SkillGraph.validate(skills, listOf(Edge("alpha", "beta"), Edge("beta", "alpha")))
        }
    }

    @Test fun graphLimitsNewBranchesWhilePreservingExistingDependencies() {
        val ids = setOf("root", "one", "two", "three", "four")
        val legacy = listOf(Edge("one", "root"), Edge("two", "root"), Edge("three", "root"))
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(ids, legacy) }
        SkillGraph.validate(ids, legacy, legacy)
        SkillGraph.validate(ids, legacy.take(2), legacy)
        assertThrows(RuleViolation::class.java) { SkillGraph.validate(ids, legacy + Edge("four", "root"), legacy) }
    }

    @Test fun generatedGraphKeepsValidBranchesAndFitsLinksAfterDepthChanges() {
        val ids = setOf("root", "left", "right", "third", "bridge", "tip", "finish", "independent")
        val proposed = listOf(
            Edge("left", "root"), Edge("right", "root"), Edge("third", "root"),
            Edge("bridge", "left"), Edge("bridge", "independent"), Edge("tip", "third"),
            Edge("finish", "bridge"), Edge("finish", "tip"), Edge("finish", "root"),
            Edge("root", "finish"), Edge("right", "right"),
        )
        val fitted = SkillGraph.fitGeneratedEdges(ids, proposed)
        assertEquals(listOf(
            Edge("left", "root"), Edge("right", "root"), Edge("bridge", "left"),
            Edge("tip", "third"), Edge("finish", "bridge"),
        ), fitted)
        SkillGraph.validate(ids, fitted)
    }

    @Test fun legacyProgressRequiresTheWholePrerequisiteChain() {
        val root = Entry("root", "tree", EntryKind.SKILL, "起点")
        val middle = root.copy(id = "middle", name = "进阶", mastery = Mastery.MANUAL)
        val last = root.copy(id = "last", name = "应用", mastery = Mastery.AI)
        val edges = listOf(Edge("middle", "root"), Edge("last", "middle"))
        val library = Library(listOf(last, middle, root), edges)
        assertEquals(SkillState.AVAILABLE, library.skillState(root.id))
        assertEquals(SkillState.LOCKED, library.skillState(middle.id))
        assertEquals(SkillState.LOCKED, library.skillState(last.id))
        assertEquals(Progress(0, 3), library.progress())
        val unlocked = library.copy(entries = listOf(last, middle, root.copy(mastery = Mastery.MANUAL)))
        assertEquals(Progress(3, 3), unlocked.progress())
    }

    @Test fun duplicatesAreDetectedAfterWhitespaceCaseAndWidthNormalization() {
        assertThrows(RuleViolation::class.java) { LibraryRules.checkUnique("  ＡＩ ", listOf("ai")) }
    }

    @Test fun aNewPassOnlyRevealsChildrenThatActuallyUnlock() {
        val first = Entry("first", "tree", EntryKind.SKILL, "第一前置")
        val other = first.copy(id = "other", name = "第二前置")
        val ready = first.copy(id = "ready", name = "可以学习")
        val blocked = first.copy(id = "blocked", name = "仍缺前置")
        val before = Library(listOf(first, other, ready, blocked),
            listOf(Edge(ready.id, first.id), Edge(blocked.id, first.id), Edge(blocked.id, other.id)))
        val after = before.copy(entries = listOf(first.copy(mastery = Mastery.AI, masteryVersion = 1), other, ready, blocked))
        val cue = after.unlocksSince(before).single()
        assertEquals(first.id, cue.skillId)
        assertEquals(setOf(ready.id), cue.availableChildren)
        assertEquals(emptyList<Any>(), after.unlocksSince(after))
    }

    @Test fun allChildrenOfANewlyLitSkillBecomeAvailableTogether() {
        val parent = Entry("parent", "tree", EntryKind.SKILL, "共同前置")
        val left = parent.copy(id = "left", name = "左侧分支")
        val right = parent.copy(id = "right", name = "右侧分支")
        val before = Library(listOf(parent, left, right), listOf(Edge(left.id, parent.id), Edge(right.id, parent.id)))
        val after = before.copy(entries = listOf(parent.copy(mastery = Mastery.AI, masteryVersion = 1), left, right))

        assertEquals(SkillState.AVAILABLE, after.skillState(left.id))
        assertEquals(SkillState.AVAILABLE, after.skillState(right.id))
        assertEquals(setOf(left.id, right.id), after.unlocksSince(before).single().availableChildren)
    }

    @Test fun homePrefersTheAvailableBranchAndStopsAtACompletedTree() {
        val tree = Entry("tree", "direction", EntryKind.TREE, "技能树")
        val root = Entry("root", tree.id, EntryKind.SKILL, "起点")
        val other = root.copy(id = "other")
        val locked = root.copy(id = "locked")
        val next = root.copy(id = "next")
        val edges = listOf(Edge(locked.id, root.id), Edge(locked.id, other.id), Edge(next.id, root.id))
        val original = Library(listOf(tree, root, other, locked, next), edges)
        assertEquals(HomeStudyState.RESUME, original.homeStudy(root)?.state)
        assertEquals(HomeStudyState.LOCKED, original.homeStudy(locked)?.state)
        assertNull(original.homeStudy(null))

        val passed = root.copy(mastery = Mastery.AI)
        val after = original.copy(entries = listOf(tree, passed, other, locked, next))
        assertEquals(HomeStudyState.NEXT, after.homeStudy(root)?.state)
        assertEquals(next.id, after.homeStudy(root)?.skill?.id)
        val afterNext = after.copy(entries = listOf(tree, passed, other, locked, next.copy(mastery = Mastery.AI)))
        assertEquals(other.id, afterNext.homeStudy(next)?.skill?.id)
        val completed = original.copy(entries = listOf(tree, root, other, locked, next).map {
            if (it.kind == EntryKind.SKILL) it.copy(mastery = Mastery.AI) else it
        })
        assertEquals(HomeStudyState.COMPLETED, completed.homeStudy(root)?.state)
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
        assertThrows(RuleViolation::class.java) {
            TutorRules.validateVerdict(TurnKind.PASSED, exercise.id, exercise, input.copy(text = TutorRules.EXAM_REPLY))
        }
        TutorRules.validateVerdict(TurnKind.PASSED, exercise.id, exercise, input)
        assertNotNull(TutorRules.assessmentCorrection(TurnKind.PASSED, exercise))
        assertNull(TutorRules.assessmentCorrection(TurnKind.PASSED, exercise.copy(number = 2)))
        assertNull(TutorRules.assessmentCorrection(TurnKind.NEXT_EXERCISE, exercise.copy(number = 2)))
        assertNotNull(TutorRules.assessmentCorrection(TurnKind.NEXT_EXERCISE, exercise.copy(number = 3)))
        assertNull(TutorRules.assessmentCorrection(TurnKind.PASSED, exercise.copy(number = 3)))
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
