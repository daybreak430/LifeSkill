package com.lifeskill.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.model.*
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实 DeepSeek Flash 回复的回归样本：验证新图形不只是协议能过，而是模型真会选、解码器真能解。
 * 样本于 2026-11-21 记录，模型更新后类型可能漂移；此测试保护解码与字段完整性，不评价模型好坏。
 */
@RunWith(AndroidJUnit4::class)
class GraphModelSamplesTest {
    private fun samples(): JSONArray {
        val raw = InstrumentationRegistry.getInstrumentation().context.assets
            .open("teaching-graph-model-samples.json").bufferedReader().use { it.readText() }
        return JSONArray(raw)
    }

    @Test fun realSamplesDecodeIntoTheRecordedStructures() {
        val array = samples()
        val covered = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val entry = array.getJSONObject(index)
            val scenario = entry.getString("scenario")
            val reply = AiProtocol.tutor(entry.getString("response"))
            assertTrue("$scenario 缺少正文", reply.reply.isNotBlank())

            val expected = entry.optString("type")
            if (expected.isEmpty()) {
                // 模型认为信息不足时允许只回文字，不能因此判失败。
                assertTrue("$scenario 记录为无组件", reply.blocks.isEmpty())
                continue
            }
            val block = reply.blocks.firstOrNull { it.isAvailable }
                ?: error("$scenario 期望 $expected，但解码后没有可用组件")
            assertEquals("$scenario 组件类型漂移", expected, block.typeName())
            covered += expected
            when (block) {
                is TeachingChart -> {
                    assertEquals(TeachingChartStyle.SCATTER, block.style)
                    assertTrue(block.points.all { it.x != null })
                }
                is TeachingMatrix -> assertTrue(block.values.all { line -> line.size == block.columns.size })
                is TeachingRange -> assertTrue(block.low <= block.high)
                is TeachingFunnel -> assertTrue(block.stages.size >= 2)
                is TeachingFormula -> assertTrue(block.lines.isNotEmpty())
                else -> Unit
            }
        }
        assertEquals(setOf("matrix", "chart", "range", "funnel", "formula", "figure"), covered)
    }

    private fun TeachingBlock.typeName(): String = when (this) {
        is TeachingText -> "text"
        is TeachingTimeline -> "timeline"
        is TeachingFlow -> "flow"
        is TeachingComparison -> "compare"
        is TeachingFigure -> "figure"
        is TeachingMatrix -> "matrix"
        is TeachingRange -> "range"
        is TeachingFunnel -> "funnel"
        is TeachingFormula -> "formula"
        is TeachingQuadrant -> "quadrant"
        is TeachingChart -> "chart"
        is TeachingQuiz -> "quiz"
        is TeachingFlipCard -> "flip"
        is TeachingSorting -> "sort"
        is TeachingClassification -> "classify"
        is TeachingHotspot -> "hotspot"
        is TeachingStepTrace -> "steps"
        is TeachingExperiment -> "experiment"
        is TeachingGame -> "game"
    }
}
