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

internal class RecoverableReply(
    message: String, val retryInstruction: String, val retryWithoutJsonMode: Boolean = false,
) : RuleViolation(message)

internal interface AiGateway {
    suspend fun complete(
        settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean, useJsonMode: Boolean = true,
    ): String
}

internal class OpenAiGateway(
    private val openConnection: (URI) -> HttpsURLConnection = { it.toURL().openConnection() as HttpsURLConnection },
) : AiGateway {
    override suspend fun complete(
        settings: ModelSettings,
        messages: List<ModelMessage>,
        structured: Boolean,
        useJsonMode: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val valid = settings.validated()
        val jsonMode = valid.provider == Provider.DEEPSEEK && structured && useJsonMode
        val connection = openConnection(URI("${valid.baseUrl}/chat/completions"))
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 85_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${valid.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val request = requestBody(valid, messages, structured, jsonMode).toString().toByteArray()
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
            readCompletion(raw, jsonMode)
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

    private fun requestBody(
        settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean, jsonMode: Boolean,
    ): JSONObject =
        JSONObject().apply {
            put("model", settings.model)
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) }
            })
            when (settings.provider) {
                Provider.DEEPSEEK -> {
                    put("thinking", JSONObject().put("type", "disabled"))
                    if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
                    if (structured) put("max_tokens", 10_000)
                }
                Provider.MINIMAX -> {
                    put("reasoning_effort", "low")
                    put("reasoning_split", true)
                    put("max_completion_tokens", if (structured) 12_000 else 2_000)
                }
                Provider.CUSTOM -> Unit
            }
        }

    private fun readCompletion(raw: String, jsonMode: Boolean): String {
        val response = AiJson.objectOrNull(raw) ?: throw RecoverableReply(
            "模型服务返回的格式有误，请重试。",
            "请重新回答最后一条用户消息，直接输出一个完整的 JSON 对象。",
        )
        checkMiniMaxStatus(response.optJSONObject("base_resp")?.optInt("status_code"))
        val choice = response.optJSONArray("choices")?.optJSONObject(0)
        when (choice?.optString("finish_reason")) {
            "length" -> throw RecoverableReply(
                "模型回复未完成，请重试或缩小本次请求。",
                "上一次回复被截断。请重新回答最后一条用户消息，精简说明，保留原请求的范围和必要字段，输出完整的 JSON 对象。",
            )
            "content_filter" -> throw RuleViolation("模型服务拦截了这次回复，请调整输入后重试。")
            "tool_calls", "function_call" -> throw RuleViolation("模型返回了工具调用，请检查模型设置后重试。")
        }
        val text = (choice?.optJSONObject("message")?.opt("content") as? String)
            ?.replace(Regex("(?is)^\\s*(?:<think>.*?</think>\\s*)+"), "")
            ?.trim()
        return text?.takeIf(String::isNotEmpty) ?: throw RecoverableReply(
            "模型没有返回文字，请重试。",
            "上一次没有返回正文。请重新回答最后一条用户消息，直接输出一个完整的 JSON 对象。",
            // DeepSeek 的 JSON 模式会将无法识别的输出替换为空格。补试时仍要求 JSON，由应用校验。
            retryWithoutJsonMode = jsonMode,
        )
    }

    private fun checkMiniMaxStatus(status: Int?) {
        when (status) {
            null, 0 -> return
            1001, 2066 -> throw httpError(408)
            1002, 2045, 2046, 2047, 2062 -> throw httpError(429)
            1004, 2049 -> throw httpError(401)
            1008 -> throw httpError(402)
            1026, 1027 -> throw RuleViolation("模型服务拦截了这次请求，请调整输入后重试。")
            1039 -> throw RuleViolation("请求或回复超过模型的长度限制，请缩小本次请求。")
            1042 -> throw RuleViolation("输入可能含有过多不可见字符，请清理后重试。")
            2022, 2061, 2068, 2069 -> throw RuleViolation("当前账户无法使用此模型，请检查模型名和权限。")
            2053, 2056, 2067 -> throw RuleViolation("MiniMax 套餐额度或积分不足，请检查账户。")
            1000, 1024, 1033, 2064 -> throw httpError(503)
            else -> throw RuleViolation("模型服务暂时无法处理请求，请检查模型配置后重试。")
        }
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
