package com.lifeskill.app

import com.lifeskill.app.model.*
import org.junit.Assert.*
import org.junit.Test

class TeachingGameTest {
    @Test fun codeBudgetIsSmallAndRejectsEmptyOrTruncatedPayloads() {
        assertTrue(validGameCode("x".repeat(MAX_GAME_CODE_LENGTH)))
        assertFalse(validGameCode("x".repeat(MAX_GAME_CODE_LENGTH + 1)))
        assertFalse(validGameCode("   "))
        assertFalse(validGameCode("game.say('a');\u0000"))
    }
    @Test fun gameExperienceNeverCountsAsAnAssessment() {
        val game = TeachingGame("g", "游戏", "凑成1", "点击", "game.say('开始');")
        val state = TeachingInteractions.apply(game, TeachingState(), TeachingAction.OpenGame)
        assertTrue(state.gameOpened)
        assertFalse(state.submitted)
        assertEquals(0, state.attempts)
        assertEquals(state, TeachingInteractions.apply(game, state, TeachingAction.OpenGame))
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(game, state, TeachingAction.Submit) }
        assertThrows(RuleViolation::class.java) { TeachingInteractions.apply(TeachingText("t", "t", "t"), state, TeachingAction.OpenGame) }
        assertTrue(game.interactionSummary(state).contains("不计正式评估"))
        assertFalse(game.markdown().contains(game.code))
    }
    @Test fun showcaseKeepsOneComparisonAndAddsGameToExistingPracticeCategory() {
        assertEquals(1, BuiltInTeaching.lessonByKey("structure")!!.blocks.filterIsInstance<TeachingComparison>().size)
        val game = BuiltInTeaching.lessonByKey("practice")!!.items.single { it.installMarker == 1022 }.block as TeachingGame
        assertTrue(validGameCode(game.code))
        assertEquals(4, BuiltInTeaching.lessons.size)
    }
}
