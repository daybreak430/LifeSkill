package com.lifeskill.app

import android.app.Application
import androidx.room.Room
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.OpenAiGateway
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.data.ConversationRepository
import com.lifeskill.app.data.LibraryRepository
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.LocalSettings
import com.lifeskill.app.data.SecretBox
import com.lifeskill.app.data.TreeAssessmentRepository

open class LifeSkillApplication : Application() {
    internal val container by lazy { createContainer() }

    internal open fun createContainer(): AppContainer {
        val database = Room.databaseBuilder(this, LifeSkillDatabase::class.java, "lifeskill.db").build()
        val secret = SecretBox("lifeskill-data")
        return AppContainer(database, secret, LocalSettings(this, secret), OpenAiGateway())
    }
}

internal class AppContainer(
    database: LifeSkillDatabase,
    secret: SecretBox,
    val settings: SettingsStore,
    val gateway: AiGateway,
) {
    val library = LibraryRepository(database, secret)
    val conversations = ConversationRepository(database, library, settings, gateway, secret)
    val assessments = TreeAssessmentRepository(database, library, settings, gateway, secret)
}
