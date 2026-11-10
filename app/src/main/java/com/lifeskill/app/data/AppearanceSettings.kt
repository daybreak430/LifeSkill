package com.lifeskill.app.data

import android.content.Context

internal enum class AppearanceMode(val label: String) {
    LIGHT("浅色"), DARK("深色"), AUTO("自动");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        AUTO -> systemDark
    }

    companion object {
        fun fromId(id: String?): AppearanceMode = entries.firstOrNull { it.name == id } ?: AUTO
    }
}

internal class AppearanceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(): AppearanceMode = AppearanceMode.fromId(preferences.getString("mode", null))

    fun save(mode: AppearanceMode) {
        preferences.edit().putString("mode", mode.name).apply()
    }
}
