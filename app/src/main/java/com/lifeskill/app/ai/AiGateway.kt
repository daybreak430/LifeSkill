package com.lifeskill.app.ai

import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

internal data class ModelMessage(val role: String, val text: String)

internal interface AiGateway {
    suspend fun complete(settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean): String
}

internal class OpenAiGateway : AiGateway {
    override suspend fun complete(
        settings: ModelSettings,
        messages: List<ModelMessage>,
        structured: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val valid = settings.validated()
        val connection = URI("${valid.baseUrl}/chat/completions").toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 85_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${valid.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val request = requestBody(valid, messages, structured).toString().toByteArray()
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(status)
            val raw = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                buildString {
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (length + count > 512_000) throw RuleViolation("AI 响应过长，请缩小本次请求。")
                        append(buffer, 0, count)
                    }
                }
            }
            readCompletion(raw)
        } catch (_: SocketTimeoutException) {
            throw RuleViolation("模型响应超时，请重试。")
        } catch (_: SSLException) {
            throw RuleViolation("无法验证模型服务的 HTTPS 连接，请检查地址。")
        } catch (_: IOException) {
            throw RuleViolation("无法连接模型服务，请检查网络和服务地址后重试。")
        } finally {
            connection.disconnect()
        }
    }

    private fun requestBody(settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean): JSONObject =
        JSONObject().apply {
            put("model", settings.model)
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) }
            })
            when (settings.provider) {
                Provider.DEEPSEEK -> {
                    put("thinking", JSONObject().put("type", "disabled"))
                    if (structured) {
                        put("response_format", JSONObject().put("type", "json_object"))
                        put("max_tokens", 10_000)
                    }
                }
                Provider.MINIMAX -> {
                    put("reasoning_effort", "low")
                    put("reasoning_split", true)
                    put("max_completion_tokens", if (structured) 12_000 else 2_000)
                }
                Provider.CUSTOM -> Unit
            }
        }

    private fun readCompletion(raw: String): String {
        val response = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw RuleViolation("模型服务返回的格式有误，请重试。")
        when (response.optJSONObject("base_resp")?.optInt("status_code")) {
            null, 0 -> Unit
            1002 -> throw httpError(429)
            1004 -> throw httpError(401)
            1008 -> throw RuleViolation("模型账户余额不足，请检查服务商账户。")
            else -> throw RuleViolation("模型服务暂时无法处理请求，请检查模型配置后重试。")
        }
        val choice = response.optJSONArray("choices")?.optJSONObject(0)
        if (choice?.optString("finish_reason") == "length") {
            throw RuleViolation("模型回复未完成，请缩小本次请求后重试。")
        }
        val text = (choice?.optJSONObject("message")?.opt("content") as? String)
            ?.replace(Regex("(?is)^\\s*(?:<think>.*?</think>\\s*)+"), "")
            ?.trim()
        return text?.takeIf(String::isNotEmpty) ?: throw RuleViolation("模型没有返回文字，请检查配置后重试。")
    }

    private fun httpError(status: Int) = RuleViolation(when (status) {
        401, 403 -> "模型服务拒绝访问，请检查 API Key 和模型权限。"
        402 -> "模型账户余额不足，请检查服务商账户。"
        404 -> "找不到模型接口，请检查 Base URL 和模型名。"
        408, 504 -> "模型响应超时，请重试。"
        429 -> "模型服务请求过于频繁，请稍后重试。"
        in 300..399 -> "模型接口返回了跳转，请填写服务商给出的最终 HTTPS 地址。"
        in 500..599 -> "模型服务暂时不可用，请稍后重试。"
        else -> "模型服务拒绝了请求（$status），请检查模型配置。"
    })
}
