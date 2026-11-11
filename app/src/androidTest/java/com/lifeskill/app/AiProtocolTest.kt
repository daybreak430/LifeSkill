package com.lifeskill.app

import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.GenerationLevel
import com.lifeskill.app.model.GenerationOptions
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TurnKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProtocolTest {
    @Test fun acceptsFencedJsonUnicodeAndNullOptionalFields() {
        val fence = "```"
        val raw = """{"kind":"teach","reply":"先看「你好」\n再举例。","exerciseId":null}"""
        val result = AiProtocol.tutor("${fence}JSON\n$raw\n$fence")
        assertEquals(TurnKind.MESSAGE, result.kind)
        assertEquals("先看「你好」\n再举例。", result.reply)
        assertNull(result.exerciseId)

        val draft = AiProtocol.generation(
            """{"kind":"create","reply":"已规划","directionName":"阅读","directionId":null,"trees":null}"""
        ).draft!!
        assertEquals("阅读", draft.directionName)
        assertEquals(emptyList<Any>(), draft.trees)
    }

    @Test fun rejectsMalformedAmbiguousAndOverscopedDocuments() {
        val valid = """{"kind":"message","reply":"想学什么？"}"""
        val bad = listOf(
            "",
            "这是建议。",
            valid.dropLast(1),
            "$valid{}",
            "$valid\u0000{}",
            """{'kind':'message','reply':'使用单引号'}""",
            """{"kind":"message","kind":"create","reply":"重复指令","directionName":"测试"}""",
            """{"kind":"message","reply":false}""",
            """{"kind":"message","reply":"  "}""",
            """{"kind":"message","reply":"建议","trees":[]}""",
            """{"kind":"offer_tree","reply":"提议","trees":[]}""",
            """{"kind":"delete","reply":"不允许"}""",
            """{"kind":"create","reply":"已规划","trees":[null]}""",
            """{"kind":"create","reply":"已规划","skills":[{"ref":"x","name":"节点","goal":"目标","requires":[7]}]}""",
            """{"kind":"create","reply":"已规划","trees":${"[".repeat(200)}0${"]".repeat(200)}}""",
        )
        bad.forEach { input -> assertThrows(RuleViolation::class.java) { AiProtocol.generation(input) } }
    }

    @Test fun generationAcceptsCatalogIconsAndIgnoresInvalidOptionalIcons() {
        val skills = JSONArray()
        listOf(" speech ", "UNKNOWN", JSONObject(), "DIAMOND", JSONObject.NULL).forEachIndexed { index, icon ->
            skills.put(JSONObject().put("ref", "skill_$index").put("name", "技能$index")
                .put("goal", "能说明用途").put("requires", JSONArray()).put("icon", icon))
        }
        val reply = JSONObject().put("kind", "create").put("reply", "已规划").put("skills", skills)
        assertEquals(listOf(EntryIcon.SPEECH, null, null, null, null),
            AiProtocol.generation(reply.toString()).draft?.skills?.map { it.icon })

        val trees = JSONArray()
        listOf(" chart ", "TREE", "UNKNOWN", JSONObject.NULL).forEachIndexed { index, icon ->
            trees.put(JSONObject().put("name", "技能树$index").put("icon", icon))
        }
        reply.remove("skills")
        reply.put("trees", trees)
        assertEquals(listOf(EntryIcon.CHART, null, null, null),
            AiProtocol.generation(reply.toString(), savedDraft = true).draft?.trees?.map { it.icon })
    }

    @Test fun acceptsExactLimitsAndRejectsOneOver() {
        val name = "技".repeat(LibraryRules.NAME_LIMIT)
        val goal = "练".repeat(LibraryRules.GOAL_LIMIT)
        fun skills(count: Int) = JSONArray().apply {
            repeat(count) { index ->
                put(JSONObject().put("ref", "node_$index").put("name", name).put("goal", goal).put("requires", JSONArray()))
            }
        }
        fun creation() = JSONObject().put("kind", "create").put("reply", "已规划")
        fun trees(count: Int) = JSONArray().apply {
            repeat(count) { index -> put(JSONObject().put("name", "树$index").put("skills", JSONArray())) }
        }
        fun newTree(count: Int) = creation().put("trees", JSONArray().put(
            JSONObject().put("name", "新树").put("skills", skills(count)),
        )).toString()
        assertEquals(6, AiProtocol.generation(creation().put("trees", trees(6)).toString(), savedDraft = true).draft?.trees?.size)
        assertThrows(RuleViolation::class.java) {
            AiProtocol.generation(creation().put("trees", trees(7)).toString(), savedDraft = true)
        }
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("trees", trees(2)).toString()) }
        assertEquals(33, AiProtocol.generation(newTree(33)).draft?.trees?.single()?.skills?.size)
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(newTree(34)) }
        assertEquals(30, AiProtocol.generation(creation().put("skills", skills(30)).toString()).draft?.skills?.size)
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("skills", skills(31)).toString()) }

        val skill = skills(1)
        skill.getJSONObject(0).put("name", name + "超")
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("skills", skill).toString()) }
        skill.getJSONObject(0).put("name", name).put("goal", goal + "超")
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("skills", skill).toString()) }
    }

    @Test fun treePromptUsesTheConfirmedLevelAndCourseCount() {
        val expectations = listOf(
            Triple(GenerationOptions(GenerationLevel.BEGINNER, 3..5), "用日常语言和熟悉的例子", "从 3–5 节中选择"),
            Triple(GenerationOptions(GenerationLevel.INTERMEDIATE, 8..12), "方法选择、常见失误、排查步骤和结果验证", "从 8–12 节中选择"),
            Triple(GenerationOptions(GenerationLevel.EXPERT, 1..1), "失效模式和方案权衡", "精确生成 1 节"),
        )
        for ((options, depth, quantity) in expectations) {
            val prompt = AiProtocol.generationPrompt(null, emptyList(), emptyMap(), emptyMap(), options)
            assertTrue(prompt.contains("本次掌握程度为「${options.level.label}」"))
            assertTrue(prompt.contains(depth))
            assertTrue(prompt.contains(quantity))
            assertTrue(prompt.contains("恰好包含 1 棵技能树"))
        }
    }
}
