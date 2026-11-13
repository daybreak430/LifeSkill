package com.lifeskill.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.OpenAiGateway
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.ai.RecoverableReply
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.UsageTarget
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.GenerationLevel
import com.lifeskill.app.model.GenerationOptions
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TurnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

class AiGatewayTest {
    private val settings = ModelSettings(Provider.MINIMAX, apiKey = "test-key")
    private val messages = listOf(ModelMessage("user", "帮我规划\n中文技能树「拍照」"))

    @Test fun serializesChineseAndKeepsReasoningOutOfTheReply() = runBlocking {
        val body = """{"choices":[{"finish_reason":"stop","message":{"content":"<think>内部</think>你好","reasoning_content":"不要显示"}}]}"""
        val connection = Response(body = body)
        val gateway = OpenAiGateway { endpoint ->
            assertEquals("https://api.minimax.cn/v1/chat/completions", endpoint.toString())
            connection
        }
        assertEquals("你好", gateway.complete(settings, messages, structured = true))
        val request = JSONObject(connection.written.toByteArray().decodeToString())
        assertEquals(messages.single().text, request.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("MiniMax-M3.1-Flash", request.getString("model"))
        assertEquals("Bearer test-key", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    @Test fun sendsImagesAsMultimodalContentForBothPresets() = runBlocking {
        val image = "data:image/jpeg;base64,c2FtcGxl"
        for (provider in listOf(Provider.DEEPSEEK, Provider.MINIMAX)) {
            val connection = Response(body = """{"choices":[{"message":{"content":"收到图片"}}]}""")
            val gateway = OpenAiGateway { endpoint ->
                assertEquals("${provider.baseUrl}/chat/completions", endpoint.toString())
                connection
            }
            val settings = ModelSettings(provider, apiKey = "test-key")
            val messages = listOf(ModelMessage("user", "请解读图片", listOf(image)))
            assertEquals("收到图片", gateway.complete(settings, messages, structured = true))
            val request = JSONObject(connection.written.toByteArray().decodeToString())
            val content = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            assertEquals("请解读图片", content.getJSONObject(0).getString("text"))
            assertEquals("image_url", content.getJSONObject(1).getString("type"))
            assertEquals(image, content.getJSONObject(1).getJSONObject("image_url").getString("url"))
        }
    }

    @Test fun readsDeepSeekBalanceFromTheDedicatedEndpoint() = runBlocking {
        val connection = Response(
            body = """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}]}""",
        )
        val gateway = OpenAiGateway { endpoint ->
            assertEquals("https://api.deepseek.com/user/balance", endpoint.toString())
            connection
        }
        val balance = gateway.balance(ModelSettings(Provider.DEEPSEEK, apiKey = "test-key"))
        assertTrue(balance.isAvailable)
        assertEquals("CNY", balance.balances.single().currency)
        assertEquals("110.00", balance.balances.single().totalBalance)
        assertEquals("GET", connection.requestMethod)
        assertEquals("Bearer test-key", connection.getRequestProperty("Authorization"))
        assertTrue(connection.disconnected)
    }

    @Test fun searchesDeepSeekOnceAndReusesResultsWhenTheReplyIsRetried() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LifeSkillDatabase::class.java,
        ).build()
        try {
            val search = Response(body = """{"results":[
                {"title":"官方发布","url":"https://example.com/update","summary":"更新了第 3 节。","date":"2026-11-01"},
                {"title":"无效来源","url":"javascript:alert(1)","summary":"不应传入模型"}
            ]}""")
            val reply = Response(body = """{"created":1794362400,"choices":[{"message":{"content":"{\"kind\":\"teach\",\"reply\":\"新版更新了第 3 节。\"}"}}],
                "usage":{"prompt_tokens":100,"completion_tokens":20,"prompt_cache_hit_tokens":0}}""")
            var searchResponse = search
            var searchCalls = 0
            var modelCalls = 0
            val gateway = OpenAiGateway { endpoint ->
                when (endpoint.path) {
                    "/v1/tools/search" -> {
                        searchCalls++
                        searchResponse
                    }
                    "/chat/completions" -> if (modelCalls++ == 0) Response(status = 503) else reply
                    else -> error("意外请求：$endpoint")
                }
            }
            val app = fixture(database, gateway)
            app.library.initialize()
            val direction = app.library.snapshot().childrenOf(null).first()
            val course = app.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "规程"))
            val lesson = app.library.save(EntryEdit(null, course.id, EntryKind.SKILL, "版本更新", "识别新版变化"))
            val scope = ChatScope(ChatMode.TUTOR, lesson.id)
            val failure = runCatching { app.conversations.send(scope, "查一下最近变化", webSearch = true) }.exceptionOrNull()
            assertTrue(failure is RuleViolation)
            assertEquals(5_000_000L, app.usage.summary.first().lessons.getValue(lesson.id).costNanos)
            app.conversations.retry(scope)
            assertEquals(1, searchCalls)
            assertEquals(2, modelCalls)
            assertEquals("POST", search.requestMethod)
            assertFalse(search.instanceFollowRedirects)
            assertEquals("Bearer instrumentation-fixture", search.getRequestProperty("Authorization"))
            assertEquals("查一下最近变化 版本更新", JSONObject(search.written.toString("UTF-8")).getString("query"))
            val prompt = JSONObject(reply.written.toString("UTF-8")).getJSONArray("messages").getJSONObject(0)
                .getString("content")
            assertTrue(prompt.contains("更新了第 3 节。"))
            assertFalse(prompt.contains("javascript:"))
            val turn = app.conversations.watch(scope).first().last()
            assertEquals("新版更新了第 3 节。", turn.text)
            assertTrue(turn.searchSources.orEmpty().contains("https://example.com/update"))
            assertTrue(app.conversations.exportConversation(lesson.id).markdown.contains("https://example.com/update"))
            val totals = app.usage.summary.first()
            val lessonTotal = totals.lessons.getValue(lesson.id)
            assertEquals(120L, lessonTotal.tokens)
            assertEquals(360_000L, lessonTotal.modelCostNanos)
            assertEquals(1L, lessonTotal.searchCalls)
            assertEquals(5_000_000L, lessonTotal.searchCostNanos)
            assertEquals(5_360_000L, lessonTotal.costNanos)
            assertEquals(lessonTotal, totals.courses.getValue(course.id))
            assertEquals(lessonTotal, totals.total)

            val settings = ModelSettings(Provider.DEEPSEEK, apiKey = "test-key")
            val generation = UsageTarget(courseId = course.id, generation = true)
            searchResponse = Response(body = """{"results":[]}""")
            assertFalse(app.usage.search(settings, "空结果", generation).billable)
            searchResponse = Response(status = 503)
            assertTrue(runCatching { app.usage.search(settings, "失败请求", generation) }.exceptionOrNull() is RuleViolation)
            assertEquals(totals, app.usage.summary.first())

            searchResponse = Response(body = """{"results":[{"url":"javascript:alert(1)"}]}""")
            assertEquals(0, app.usage.search(settings, "来源被过滤", generation).results.length())
            val combined = app.usage.summary.first()
            val courseTotal = combined.courses.getValue(course.id)
            assertEquals(2L, courseTotal.searchCalls)
            assertEquals(10_360_000L, courseTotal.costNanos)
            assertEquals(120L, courseTotal.tokens)
            assertEquals(lessonTotal, combined.lessons.getValue(lesson.id))
            assertEquals(5_000_000L, combined.generation.getValue(course.id).costNanos)
            assertEquals(courseTotal, combined.total)
        } finally {
            database.close()
        }
    }

    @Test fun mapsHttpAndNetworkErrors() = runBlocking {
        val http = mapOf(
            302 to "跳转", 401 to "API Key", 403 to "权限", 402 to "余额", 404 to "Base URL",
            429 to "频繁", 503 to "不可用", 504 to "超时",
        )
        http.forEach { (status, text) -> expectError(Response(status = status), text) }
        expectError(Response(failure = SocketTimeoutException()), "超时")
        expectError(Response(failure = UnknownHostException()), "网络")
        expectError(Response(failure = SSLHandshakeException("模拟证书错误")), "HTTPS")
    }

    @Test fun combinesOfficialProviderReceiptsAndLeavesSubscriptionFeesUnpriced() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val deepSeek = ModelSettings(Provider.DEEPSEEK, apiKey = "test-key")
            val miniMax = ModelSettings(Provider.MINIMAX, apiKey = "test-pay-as-you-go")
            val response = """{"created":1794362400,"choices":[{"message":{"content":"完成"}}],
                "usage":{"prompt_tokens":1000,"completion_tokens":200,"total_tokens":1200,
                "prompt_tokens_details":{"cached_tokens":100}}}"""
            val totalOnly = """{"choices":[{"message":{"content":"完成"}}],"usage":{"total_tokens":50}}"""
            val replies = ArrayDeque(listOf(response, response, response, totalOnly, response))
            val gateway = OpenAiGateway { Response(body = replies.removeFirst()) }
            val app = fixture(database, gateway)
            val target = UsageTarget(courseId = "course", lessonId = "lesson")
            app.usage.complete(deepSeek, messages, structured = false, target = target)
            app.usage.complete(miniMax, messages, structured = false, target = target)
            app.usage.complete(miniMax.copy(apiKey = "sk-cp-fixture"), messages, structured = false, target = target)
            app.usage.complete(miniMax, messages, structured = false, target = target)
            app.usage.complete(
                ModelSettings(Provider.CUSTOM, miniMax.baseUrl, miniMax.model, "test-custom"),
                messages, structured = false, target = target,
            )
            val summary = app.usage.summary.first()
            val total = checkNotNull(summary.total)
            assertEquals(3_650L, total.tokens)
            assertEquals(5_114_000L, total.costNanos)
            assertEquals(2, total.unpriced)
            assertEquals(total, summary.courses["course"])
            assertEquals(total, summary.lessons["lesson"])
        } finally {
            database.close()
        }
    }

    @Test fun assignsEveryBilledGenerationAttemptToTheConfirmedCourse() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        fun response(content: String, cached: Int?): Response {
            val usage = JSONObject().put("prompt_tokens", 100).put("completion_tokens", 20)
            cached?.let { usage.put("prompt_cache_hit_tokens", it) }
            val choice = JSONObject().put("message", JSONObject().put("content", content))
            return Response(body = JSONObject().put("choices", JSONArray().put(choice)).put("usage", usage).toString())
        }
        val responses = ArrayDeque(listOf(
            response(RepositoryTest.TREE_OFFER, 10), response("   ", 10), response(RepositoryTest.CREATED, null),
        ))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val app = fixture(database, OpenAiGateway { responses.removeFirst() })
            val conversations = app.conversations
            conversations.send(scope, "生成数据质量课程")
            val offer = conversations.watch(scope).first().last()
            conversations.generateTree(scope, offer.id, GenerationOptions(GenerationLevel.BEGINNER, 2..2))
            val draft = conversations.watch(scope).first().last()
            assertEquals(TurnKind.DRAFT, draft.kind)
            assertEquals(360L, app.usage.summary.first().total?.tokens)
            assertTrue(app.usage.summary.first().courses.isEmpty())
            conversations.confirmDraft(scope, draft.id, GenerationDestination.New("数据能力"))
            val library = app.library.snapshot()
            val course = library.childrenOf(library.childrenOf(null).single().id).single()
            val summary = app.usage.summary.first()
            assertEquals(240L, summary.courses.getValue(course.id).tokens)
            assertEquals(summary.courses[course.id], summary.generation[course.id])
            assertEquals(1, summary.courses.getValue(course.id).unpriced)
            conversations.clearGeneration(scope)
            assertEquals(360L, app.usage.summary.first().total?.tokens)
            assertTrue(responses.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test fun deepSeekBlankJsonContentRetriesWithoutJsonModeAndKeepsTheOriginalRequest() = runBlocking {
        val blank = Response(body = """{"choices":[{"finish_reason":"stop","message":{"content":"   "}}]}""")
        val answer = Response(body = JSONObject().put("choices", JSONArray().put(
            JSONObject().put("finish_reason", "stop").put("message", JSONObject().put("content", RepositoryTest.TREE_OFFER)),
        )).toString())
        val responses = ArrayDeque(listOf(blank, answer))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val container = fixture(database, OpenAiGateway { responses.removeFirst() })
            container.settings.save(ModelSettings(Provider.DEEPSEEK, apiKey = "test-key"))
            val scope = ChatScope(ChatMode.GENERATE)
            val topic = "从零学习文明 6"
            container.conversations.send(scope, topic)

            val initial = JSONObject(blank.written.toByteArray().decodeToString())
            val retry = JSONObject(answer.written.toByteArray().decodeToString())
            assertEquals("json_object", initial.getJSONObject("response_format").getString("type"))
            assertFalse(retry.has("response_format"))
            assertEquals(initial.getInt("max_tokens"), retry.getInt("max_tokens"))
            val retryMessages = retry.getJSONArray("messages")
            assertTrue(retryMessages.getJSONObject(0).getString("content").contains("完整的 JSON 对象"))
            assertEquals(topic, retryMessages.getJSONObject(1).getString("content"))
            val turns = container.conversations.watch(scope).first()
            assertEquals(listOf(TurnKind.MESSAGE, TurnKind.TREE_OFFER), turns.map { it.kind })
            assertTrue(responses.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test fun handlesProviderErrorsAndIncompleteOrUnexpectedResponses() = runBlocking {
        val codes = mapOf(
            1001 to "超时", 1002 to "频繁", 2049 to "API Key", 1008 to "余额", 1026 to "调整输入",
            1039 to "长度限制", 1042 to "不可见字符", 2022 to "模型名", 2067 to "套餐额度",
        )
        codes.forEach { (code, text) ->
            expectError(Response(body = """{"base_resp":{"status_code":$code}}"""), text)
        }
        expectError(Response(body = "网关临时不可用"), "格式有误", recoverable = true)
        expectError(Response(body = """{"choices":[]}"""), "没有返回文字", recoverable = true)
        expectError(
            Response(body = """{"choices":[{"finish_reason":"stop","message":{"content":"  ","reasoning_content":"内部推理"}}]}"""),
            "没有返回文字", recoverable = true,
        )
        expectError(
            Response(body = """{"choices":[{"finish_reason":"length","message":{"content":"半条消息"}}]}"""),
            "未完成", recoverable = true,
        )
        expectError(Response(body = """{"choices":[{"finish_reason":"content_filter","message":{"content":"截断"}}]}"""), "拦截")
        expectError(Response(body = """{"choices":[{"finish_reason":"tool_calls","message":{"content":"工具调用"}}]}"""), "工具调用")
        expectError(Response(body = "a".repeat(512_001)), "过长")
    }

    private suspend fun expectError(connection: Response, text: String, recoverable: Boolean = false) {
        val failure = runCatching { OpenAiGateway { connection }.complete(settings, messages, true) }.exceptionOrNull()
        assertTrue("预期包含 $text，实际是 ${failure?.message}", failure is RuleViolation && failure.message.orEmpty().contains(text))
        assertEquals(recoverable, failure is RecoverableReply)
        assertTrue(connection.disconnected)
    }

    private class Response(
        private val status: Int = 200,
        private val body: String = "",
        private val failure: IOException? = null,
    ) : HttpsURLConnection(URI("https://example.com").toURL()) {
        val written = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream() = written
        override fun getResponseCode(): Int = failure?.let { throw it } ?: status
        override fun getInputStream() = body.byteInputStream()
        override fun getCipherSuite() = "测试套件"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
