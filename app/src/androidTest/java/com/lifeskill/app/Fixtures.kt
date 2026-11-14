package com.lifeskill.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.DeepSeekBalance
import com.lifeskill.app.ai.DeepSeekSearch
import com.lifeskill.app.ai.ModelUsage
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.LessonImageSearch
import com.lifeskill.app.data.SecretBox
import org.json.JSONArray
import org.junit.Assert.assertEquals
import java.util.concurrent.ConcurrentLinkedQueue

class LifeSkillTestRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(loader, FixtureApplication::class.java.name, context)
}

class FixtureApplication : LifeSkillApplication() {
    internal val ai = ScriptedGateway()
    internal var imageSearch = LessonImageSearch { emptyList() }
    internal override fun createContainer(): AppContainer {
        val database = Room.inMemoryDatabaseBuilder(this, LifeSkillDatabase::class.java).build()
        return fixture(database, ai, LessonImageSearch { imageSearch.find(it) })
    }
}

internal fun fixture(
    database: LifeSkillDatabase, ai: AiGateway, imageSearch: LessonImageSearch = LessonImageSearch { emptyList() },
): AppContainer =
    AppContainer(database, SecretBox("lifeskill-tests"), FixtureSettings(), ai,
        InstrumentationRegistry.getInstrumentation().targetContext, imageSearch)

internal class ScriptedGateway : AiGateway {
    private data class Reply(
        val action: suspend (List<ModelMessage>) -> String,
        val usage: ModelUsage? = null,
        val expectedDeepThinking: Boolean? = null,
    )
    private val script = ConcurrentLinkedQueue<Reply>()
    private val balanceScript = ConcurrentLinkedQueue<suspend () -> DeepSeekBalance>()
    private val searchScript = ConcurrentLinkedQueue<(String) -> DeepSeekSearch>()

    fun enqueueSearch(action: (String) -> DeepSeekSearch) {
        searchScript.add(action)
    }

    override suspend fun search(settings: ModelSettings, query: String): DeepSeekSearch =
        searchScript.poll()?.invoke(query) ?: DeepSeekSearch(JSONArray(), billable = false)

    fun reply(json: String, usage: ModelUsage? = null, expectedDeepThinking: Boolean? = null) {
        script.add(Reply({ json }, usage, expectedDeepThinking))
    }

    fun enqueue(expectedDeepThinking: Boolean? = null, action: suspend (List<ModelMessage>) -> String) {
        script.add(Reply(action, expectedDeepThinking = expectedDeepThinking))
    }

    fun enqueueBalance(action: suspend () -> DeepSeekBalance) {
        balanceScript.add(action)
    }

    override suspend fun balance(settings: ModelSettings): DeepSeekBalance =
        checkNotNull(balanceScript.poll()) { "测试缺少预设余额响应。" }()

    override suspend fun complete(
        settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean, useJsonMode: Boolean,
        deepThinking: Boolean,
        onUsage: (suspend (ModelUsage) -> Unit)?,
    ): String {
        val reply = checkNotNull(script.poll()) { "测试缺少预设 AI 响应。" }
        reply.expectedDeepThinking?.let { assertEquals("模型请求应遵循深度思考开关", it, deepThinking) }
        if (onUsage != null && reply.usage != null) onUsage(reply.usage)
        return reply.action(messages)
    }
}

private class FixtureSettings : SettingsStore {
    private var active = ModelSettings(apiKey = "instrumentation-fixture")
    override fun active() = active
    override fun load(provider: Provider) = if (provider == active.provider) active else ModelSettings(provider)
    override fun save(settings: ModelSettings) { active = settings }
}
