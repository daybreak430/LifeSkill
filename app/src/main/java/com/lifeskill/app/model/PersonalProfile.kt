package com.lifeskill.app.model

import java.time.OffsetDateTime

internal const val NICKNAME_LIMIT = 24
internal const val DEFAULT_PROFILE_NAME = "学习者"

internal enum class AvatarPreset(val label: String) {
    BOB("短发女生"), GLASSES("眼镜男生"), LAVENDER("紫发青年"), SILVER("银发女士"),
    CURLS("卷发男生"), SCARF("橙色头巾"), CAT("橘猫"), DOG("垂耳小狗"),
    FOX("赤狐"), RABBIT("奶油兔"), ALIEN("绿星人"), ROBOT("蓝色机器人");

    companion object {
        val default = BOB

        fun fromId(id: String): AvatarPreset = entries.firstOrNull { it.name == id } ?: when (id) {
            "READING" -> GLASSES
            "CRYSTAL" -> ALIEN
            "CODING" -> LAVENDER
            "PHOTOGRAPHY" -> SCARF
            "MUSIC" -> ROBOT
            else -> default
        }
    }
}

internal sealed interface ProfileAvatar {
    data class Preset(val value: AvatarPreset) : ProfileAvatar
    data class Photo(val jpeg: String) : ProfileAvatar
}

internal data class PersonalProfile(
    val nickname: String? = null,
    val avatar: ProfileAvatar = ProfileAvatar.Preset(AvatarPreset.default),
) {
    val displayName get() = nickname ?: DEFAULT_PROFILE_NAME
}

internal fun validatedNickname(value: String, required: Boolean = false): String? {
    val name = value.trim()
    if (name.isEmpty() && required) throw RuleViolation("请填写证书署名。")
    if (name.codePointCount(0, name.length) > NICKNAME_LIMIT || name.any {
            it.isISOControl() || it == '\u2028' || it == '\u2029'
        }
    ) throw RuleViolation("昵称最多 $NICKNAME_LIMIT 字，且不能换行。")
    return name.ifEmpty { null }
}

internal data class CollectedCertificate(
    val treeId: String, val treeName: String, val style: CertificateStyle, val recipient: String, val avatar: ProfileAvatar,
    val hidden: Boolean = false, val collectedAt: OffsetDateTime? = null, val isSample: Boolean = false,
)

internal data class CertificateState(val issued: CollectedCertificate? = null, val destroyed: Boolean = false)

internal data class LearningStatistics(val skills: Progress, val passedTrees: Int) {
    companion object {
        fun from(library: Library, assessments: Map<String, TreeAssessmentSummary>) = LearningStatistics(
            library.progress(),
            assessments.count { (id, summary) -> summary.passed && library.byId[id]?.kind == EntryKind.TREE },
        )
    }
}
