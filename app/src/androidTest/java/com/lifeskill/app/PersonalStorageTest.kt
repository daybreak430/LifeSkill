package com.lifeskill.app

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.PersonalProfileRow
import com.lifeskill.app.data.SecretBox
import com.lifeskill.app.data.readAvatarPhoto
import com.lifeskill.app.model.CertificateStyle
import com.lifeskill.app.model.AvatarPreset
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.LearningStatistics
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.PersonalProfile
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.Progress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalStorageTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun migratesVersionThreeAndArchivesOnlyCollectedCertificatesOnce() = runBlocking {
        val name = "personal-upgrade-${UUID.randomUUID()}"
        val secret = SecretBox("lifeskill-tests")
        val direction = Entry("legacy-direction", null, EntryKind.DIRECTION, "旧版方向")
        val old = Entry("legacy-tree", direction.id, EntryKind.TREE, "旧版课名", icon = EntryIcon.CAMERA)
        val second = Entry("old-no-style", direction.id, EntryKind.TREE, "早期证书")
        val waiting = Entry("waiting-tree", direction.id, EntryKind.TREE, "通过未领取")
        val lit = Entry("old-skill", old.id, EntryKind.SKILL, "已学会", mastery = Mastery.AI)
        val unlit = Entry("new-skill", old.id, EntryKind.SKILL, "未学会")
        val frozenStyle = CertificateStyle.forTree(old.copy(icon = EntryIcon.CODE))
        try {
            createVersionThree(name).use { database ->
                database.execSQL("INSERT INTO bootstrap (id) VALUES (1)")
                listOf(direction, old, second, waiting, lit, unlit).forEachIndexed { index, entry ->
                    database.execSQL(
                        "INSERT INTO entries (id, parentId, kind, name, goal, mastery, contentVersion, masteryVersion, position, icon) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)",
                        arrayOf<Any?>(entry.id, entry.parentId, entry.kind.name, secret.seal(entry.name, "${entry.id}:name"),
                            secret.seal(entry.goal, "${entry.id}:goal"), entry.mastery.name, index, entry.icon?.name),
                    )
                }
                fun insertAttempt(id: String, tree: Entry, collected: Boolean, style: CertificateStyle? = null) {
                    val stored = JSONObject(legacyAttemptFixture()).put("certificateCollected", collected)
                        .put("certificateStyle", style?.id)
                    database.execSQL("INSERT INTO exam_attempts (id, treeId, score, content) VALUES (?, ?, 100, ?)",
                        arrayOf(id, tree.id, secret.seal(stored.toString(), "$id:exam")))
                }
                insertAttempt("old-first", old, true)
                insertAttempt("old-retake", old, true, frozenStyle)
                insertAttempt("old-second", second, true)
                insertAttempt("old-waiting", waiting, false)
            }
            withDatabase(name) { database ->
                val app = fixture(database, ScriptedGateway())
                app.profiles.save(PersonalProfile(nickname = "现在的昵称"))
                repeat(2) { app.certificates.importLegacy() }
                val archive = app.certificates.certificates.first()
                assertEquals(listOf(
                    CollectedCertificate(second.id, second.name, CertificateStyle.forTree(second), null),
                    CollectedCertificate(old.id, old.name, frozenStyle, null),
                ), archive)
                assertNull(app.certificates.watchCertificate(waiting.id).first())
                val stats = LearningStatistics.from(app.library.snapshot(), app.assessments.summaries.first())
                assertEquals(Progress(1, 2), stats.skills)
                assertEquals(Progress(1, 2), app.library.snapshot().progress(direction.id))
                assertEquals(3, stats.passedTrees)
                app.certificates.issue(waiting.id, "old-waiting")
                assertEquals("现在的昵称", app.certificates.certificates.first().first().recipient)
                app.library.delete(direction.id)
                assertTrue(app.assessments.summaries.first().isEmpty())
                assertEquals(3, app.certificates.certificates.first().size)
            }
            withDatabase(name) { database ->
                val app = fixture(database, ScriptedGateway())
                app.certificates.importLegacy()
                assertEquals(listOf(waiting.id, second.id, old.id), app.certificates.certificates.first().map { it.treeId })
                assertNull(app.certificates.watchCertificate(old.id).first()?.recipient)
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test fun savesEncryptedAvatarCopyAndNicknameAcrossDatabaseReopen() = runBlocking {
        val name = "personal-avatar-${UUID.randomUUID()}"
        val photo = File.createTempFile("avatar-source", ".png", context.cacheDir)
        try {
            val original = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
            try {
                photo.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                original.recycle()
            }
            val avatar = readAvatarPhoto(context.contentResolver, Uri.fromFile(photo))
            val bytes = Base64.decode(avatar.jpeg, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            assertEquals(bounds.outWidth, bounds.outHeight)
            assertTrue(bounds.outWidth in 1..384)
            assertTrue(photo.delete())
            withDatabase(name) { database ->
                val app = fixture(database, ScriptedGateway())
                app.profiles.save(PersonalProfile("  本机学员  ", avatar))
                val stored = checkNotNull(database.personal().profile()).content
                assertFalse(stored.contains("本机学员"))
                assertFalse(stored.contains(avatar.jpeg))
            }
            withDatabase(name) { database ->
                val app = fixture(database, ScriptedGateway())
                assertEquals(PersonalProfile("本机学员", avatar), app.profiles.current())
                val legacyPreset = JSONObject().put("preset", "CODING").toString()
                database.personal().saveProfile(PersonalProfileRow(content =
                    SecretBox("lifeskill-tests").seal(legacyPreset, "personal-profile")))
                assertEquals(ProfileAvatar.Preset(AvatarPreset.LAVENDER), app.profiles.current().avatar)
            }
        } finally {
            photo.delete()
            context.deleteDatabase(name)
        }
        Unit
    }

    private suspend fun withDatabase(name: String, action: suspend (LifeSkillDatabase) -> Unit) {
        val database = Room.databaseBuilder(context, LifeSkillDatabase::class.java, name).build()
        try {
            action(database)
        } finally {
            database.close()
        }
    }

    private fun createVersionThree(name: String): SQLiteDatabase {
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val asset = "${LifeSkillDatabase::class.java.name}/3.json"
        val schema = instrumentation.context.assets.open(asset).bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        return SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            // 使用已提交的 v3 导出结构创建实际旧库，由 Room 在下次打开时执行升级。
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val table = entities.getJSONObject(index)
                val tableName = table.getString("tableName")
                execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = table.optJSONArray("indices") ?: continue
                for (indexPosition in 0 until indices.length()) {
                    execSQL(indices.getJSONObject(indexPosition).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) execSQL(setup.getString(index))
            version = 3
        }
    }
}
