package com.lifeskill.app.ai

import com.lifeskill.app.model.RuleViolation
import java.net.URI

internal enum class Provider(val label: String, val baseUrl: String, val model: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    MINIMAX("MiniMax", "https://api.minimax.cn/v1", "MiniMax-M3.1-Flash"),
    CUSTOM("自定义", "", ""),
}

internal data class ModelSettings(
    val provider: Provider = Provider.DEEPSEEK,
    val baseUrl: String = provider.baseUrl,
    val model: String = provider.model,
    val apiKey: String = "",
) {
    val supportsWebSearch get() = provider == Provider.DEEPSEEK && baseUrl.trim().trimEnd('/') == Provider.DEEPSEEK.baseUrl

    fun validated(requireKey: Boolean = true): ModelSettings {
        val url = baseUrl.trim().trimEnd('/')
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            uri.path.endsWith("/chat/completions") || uri.port == 0 || uri.port > 65535
        ) {
            throw RuleViolation("请输入 HTTPS Base URL，地址末尾保留版本路径即可，例如 /v1。")
        }
        val modelName = model.trim()
        if (modelName.isEmpty() || modelName.length > 160 || modelName.any(Char::isISOControl)) {
            throw RuleViolation("请输入有效的模型名。")
        }
        val key = apiKey.trim()
        if (requireKey && key.isEmpty()) throw RuleViolation("请先在模型设置中填写 API Key。")
        if (key.length > 4096 || key.any { it.isWhitespace() || it.isISOControl() }) {
            throw RuleViolation("API Key 格式有误。")
        }
        return copy(baseUrl = url, model = modelName, apiKey = key)
    }
}

internal interface SettingsStore {
    fun active(): ModelSettings
    fun load(provider: Provider): ModelSettings
    fun save(settings: ModelSettings)
}
