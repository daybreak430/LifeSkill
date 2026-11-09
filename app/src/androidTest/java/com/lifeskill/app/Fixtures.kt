package com.lifeskill.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.SecretBox
import java.util.concurrent.ConcurrentLinkedQueue

class LifeSkillTestRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(loader, FixtureApplication::class.java.name, context)
}

class FixtureApplication : LifeSkillApplication() {
    internal val ai = ScriptedGateway()
    internal override fun createContainer(): AppContainer {
        val database = Room.inMemoryDatabaseBuilder(this, LifeSkillDatabase::class.java).build()
        return fixture(database, ai)
    }
}

internal fun fixture(database: LifeSkillDatabase, ai: ScriptedGateway): AppContainer =
    AppContainer(database, SecretBox("lifeskill-tests"), FixtureSettings(), ai)

internal class ScriptedGateway : AiGateway {
    private val script = ConcurrentLinkedQueue<suspend (List<ModelMessage>) -> String>()

    fun reply(json: String) = enqueue { json }

    fun enqueue(action: suspend (List<ModelMessage>) -> String) {
        script.add(action)
    }

    override suspend fun complete(settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean): String =
        checkNotNull(script.poll()) { "测试缺少预设 AI 响应。" }(messages)
}

private class FixtureSettings : SettingsStore {
    private var active = ModelSettings(apiKey = "instrumentation-fixture")
    override fun active() = active
    override fun load(provider: Provider) = if (provider == active.provider) active else ModelSettings(provider)
    override fun save(settings: ModelSettings) { active = settings }
}
