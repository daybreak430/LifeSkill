package com.lifeskill.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifeskill.app.ai.TeachingBlockProtocol
import com.lifeskill.app.model.BuiltInTeaching
import com.lifeskill.app.model.TeachingAction
import com.lifeskill.app.model.TeachingFlipCard
import com.lifeskill.app.model.TeachingInteractions
import com.lifeskill.app.model.TeachingQuiz
import com.lifeskill.app.model.TeachingState
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 提示阶梯的编解码与交互边界：旧快照只有单条 hint 时必须仍可降级为一级，
 * 新数据要完整保留 1–3 级提示，且不能通过重复点击越过最后一级。
 */
@RunWith(AndroidJUnit4::class)
class HintLadderTest {
    private val question = com.lifeskill.app.model.TeachingQuestion(
        "证据不足时更合适的下一步是什么？", listOf("直接开发", "补充验证", "永久否决"), 1,
        "先补充验证再判断。", "先想最需要减少哪类不确定性。",
        listOf("先想最需要减少哪类不确定性。", "跳过验证会同时跳过哪一步？", "先降低判断错误的风险。"),
    )

    @Test
    fun 新数据保留完整提示阶梯() {
        val original = TeachingQuiz("q", "随堂练习", question)
        val decoded = TeachingBlockProtocol.decode(TeachingBlockProtocol.encode(listOf(original)))
            .single() as TeachingQuiz
        assertEquals(3, decoded.question.hintLevels.size)
        assertEquals("跳过验证会同时跳过哪一步？", decoded.question.hintLevels[1])
    }

    @Test
    fun 旧快照只有单条提示时仍可解码() {
        val legacy = JSONArray(
            """[{"id":"q","type":"quiz","title":"旧题","question":{"text":"问题","options":["甲","乙"],""" +
                """"answer":0,"hint":"只有一条线索","explanation":"解释"}}]""",
        )
        val decoded = TeachingBlockProtocol.decode(legacy).single() as TeachingQuiz
        assertEquals(listOf("只有一条线索"), decoded.question.hintLevels)
    }

    @Test
    fun 提示逐级展开且不能越过最后一级() {
        val quiz = TeachingQuiz("q", "随堂练习", question)
        var state = TeachingState(selection = "0", submitted = true)
        state = TeachingInteractions.apply(quiz, state, TeachingAction.Hint)
        assertEquals(1, state.hintLevel)
        state = TeachingInteractions.apply(quiz, state, TeachingAction.Hint)
        assertEquals(2, state.hintLevel)
        assertTrue(runCatching { TeachingInteractions.apply(quiz, state, TeachingAction.Hint) }.isFailure)
    }

    @Test
    fun 翻卡提示与翻面互不干扰() {
        val flip = TeachingFlipCard(
            "f", "先回忆", "说出条件", "完整答案", "先看适用条件",
            listOf("先看适用条件", "再想边界情况"),
        )
        val opened = TeachingInteractions.apply(flip, TeachingState(), TeachingAction.Hint)
        assertEquals(1, opened.hintLevel)
        val turned = TeachingInteractions.apply(flip, opened, TeachingAction.Flip)
        assertEquals("back", turned.selection)
        assertEquals(1, turned.hintLevel)
    }

    @Test
    fun 状态持久化保留提示层级() {
        val encoded = TeachingBlockProtocol.encodeStates(mapOf("q" to TeachingState(hintLevel = 2)))
        assertEquals(2, TeachingBlockProtocol.decodeStates(encoded).getValue("q").hintLevel)
    }

    @Test
    fun 内置练习课已配置多级提示() {
        val practice = requireNotNull(BuiltInTeaching.lessonByKey("practice"))
        val flip = practice.items.mapNotNull { it.block as? TeachingFlipCard }.first()
        val quiz = practice.items.mapNotNull { it.block as? TeachingQuiz }.first()
        assertEquals(2, flip.hintLevels.size)
        assertTrue(quiz.question.hintLevels.size >= 3)
        val roundTrip = TeachingBlockProtocol.decode(TeachingBlockProtocol.encode(practice.blocks))
        val decodedFlip = roundTrip.mapNotNull { it as? TeachingFlipCard }.first()
        assertEquals(2, decodedFlip.hintLevels.size)
        val decodedQuiz = roundTrip.mapNotNull { it as? TeachingQuiz }.first()
        assertEquals(3, decodedQuiz.question.hintLevels.size)
    }
}
