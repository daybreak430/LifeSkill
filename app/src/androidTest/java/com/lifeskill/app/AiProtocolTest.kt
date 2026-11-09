package com.lifeskill.app

import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TurnKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
            """{"kind":"delete","reply":"不允许"}""",
            """{"kind":"create","reply":"已规划","trees":[null]}""",
            """{"kind":"create","reply":"已规划","skills":[{"ref":"x","name":"节点","goal":"目标","requires":[7]}]}""",
            """{"kind":"create","reply":"已规划","trees":${"[".repeat(200)}0${"]".repeat(200)}}""",
        )
        bad.forEach { input -> assertThrows(RuleViolation::class.java) { AiProtocol.generation(input) } }
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
        assertEquals(6, AiProtocol.generation(creation().put("trees", trees(6)).toString()).draft?.trees?.size)
        assertEquals(30, AiProtocol.generation(creation().put("skills", skills(30)).toString()).draft?.skills?.size)
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("trees", trees(7)).toString()) }
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("skills", skills(31)).toString()) }

        val skill = skills(1)
        skill.getJSONObject(0).put("name", name + "超")
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("skills", skill).toString()) }
        skill.getJSONObject(0).put("name", name).put("goal", goal + "超")
        assertThrows(RuleViolation::class.java) { AiProtocol.generation(creation().put("skills", skill).toString()) }
    }
}
