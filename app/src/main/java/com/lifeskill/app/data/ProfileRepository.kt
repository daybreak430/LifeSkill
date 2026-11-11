package com.lifeskill.app.data

import com.lifeskill.app.model.AvatarPreset
import com.lifeskill.app.model.PersonalProfile
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.validatedNickname
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

internal class ProfileRepository(database: LifeSkillDatabase, private val secret: SecretBox) {
    private val dao = database.personal()
    val profile = dao.watchProfile().map { it.decode() }.flowOn(Dispatchers.IO)

    suspend fun current(): PersonalProfile = dao.profile().decode()

    suspend fun save(profile: PersonalProfile) {
        val nickname = validatedNickname(profile.nickname.orEmpty())
        val json = JSONObject().put("nickname", nickname).putAvatar(profile.avatar)
        dao.saveProfile(PersonalProfileRow(content = secret.seal(json.toString(), PROFILE_KEY)))
    }

    private fun PersonalProfileRow?.decode(): PersonalProfile {
        if (this == null) return PersonalProfile()
        val json = JSONObject(secret.open(content, PROFILE_KEY))
        return PersonalProfile(json.optString("nickname").takeIf(String::isNotEmpty), json.readAvatar())
    }

    private companion object {
        const val PROFILE_KEY = "personal-profile"
    }
}

internal fun JSONObject.putAvatar(avatar: ProfileAvatar): JSONObject = when (avatar) {
    is ProfileAvatar.Preset -> put("preset", avatar.value.name)
    is ProfileAvatar.Photo -> put("photo", avatar.jpeg)
}

internal fun JSONObject.readAvatar(): ProfileAvatar =
    optString("photo").takeIf(String::isNotEmpty)?.let { ProfileAvatar.Photo(it) }
        ?: ProfileAvatar.Preset(AvatarPreset.fromId(optString("preset")))
