package com.lifeskill.app.data

import android.content.Context
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.model.RuleViolation

internal class LocalSettings(context: Context, private val secret: SecretBox) : SettingsStore {
    private val preferences = context.getSharedPreferences("models", Context.MODE_PRIVATE)

    override fun active(): ModelSettings {
        val name = preferences.getString("active", Provider.DEEPSEEK.name)
        return load(Provider.entries.firstOrNull { it.name == name } ?: Provider.DEEPSEEK)
    }

    override fun load(provider: Provider): ModelSettings {
        val prefix = provider.name
        return ModelSettings(
            provider = provider,
            baseUrl = preferences.getString("$prefix:url", provider.baseUrl).orEmpty(),
            model = preferences.getString("$prefix:model", provider.model).orEmpty(),
            apiKey = preferences.getString("$prefix:key", null)?.let { secret.open(it, "$prefix:key") }.orEmpty(),
        )
    }

    override fun save(settings: ModelSettings) {
        val valid = settings.validated(requireKey = false)
        val prefix = valid.provider.name
        val key = valid.apiKey.takeIf(String::isNotEmpty)?.let { secret.seal(it, "$prefix:key") }
        val saved = preferences.edit()
            .putString("active", prefix)
            .putString("$prefix:url", valid.baseUrl)
            .putString("$prefix:model", valid.model)
            .putString("$prefix:key", key)
            .commit()
        if (!saved) throw RuleViolation("模型设置保存失败，请重试。")
    }
}
