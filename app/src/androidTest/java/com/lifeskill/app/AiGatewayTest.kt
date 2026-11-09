package com.lifeskill.app

import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.OpenAiGateway
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.runBlocking
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

    @Test fun handlesProviderErrorsAndIncompleteOrUnexpectedResponses() = runBlocking {
        val codes = mapOf(
            1001 to "超时", 1002 to "频繁", 2049 to "API Key", 1008 to "余额", 1026 to "调整输入",
            1039 to "长度限制", 1042 to "不可见字符", 2022 to "模型名", 2067 to "套餐额度",
        )
        codes.forEach { (code, text) ->
            expectError(Response(body = """{"base_resp":{"status_code":$code}}"""), text)
        }
        expectError(Response(body = "网关临时不可用"), "格式有误")
        expectError(Response(body = """{"choices":[]}"""), "没有返回文字")
        expectError(Response(body = """{"choices":[{"message":{"content":" "}}]}"""), "没有返回文字")
        expectError(Response(body = """{"choices":[{"finish_reason":"length","message":{"content":"半条消息"}}]}"""), "未完成")
        expectError(Response(body = """{"choices":[{"finish_reason":"content_filter","message":{"content":"截断"}}]}"""), "拦截")
        expectError(Response(body = """{"choices":[{"finish_reason":"tool_calls","message":{"content":"工具调用"}}]}"""), "工具调用")
        expectError(Response(body = "a".repeat(512_001)), "过长")
    }

    private suspend fun expectError(connection: Response, text: String) {
        val failure = runCatching { OpenAiGateway { connection }.complete(settings, messages, true) }.exceptionOrNull()
        assertTrue("预期包含 $text，实际是 ${failure?.message}", failure is RuleViolation && failure.message.orEmpty().contains(text))
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
