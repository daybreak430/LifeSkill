package com.lifeskill.app.model

internal const val NICKNAME_LIMIT = 24

internal enum class AvatarPreset(val icon: EntryIcon) {
    DEFAULT(EntryIcon.TREE), READING(EntryIcon.BOOK), CRYSTAL(EntryIcon.DIAMOND),
    CODING(EntryIcon.CODE), PHOTOGRAPHY(EntryIcon.CAMERA), MUSIC(EntryIcon.MUSIC),
}

internal sealed interface ProfileAvatar {
    data class Preset(val value: AvatarPreset) : ProfileAvatar
    data class Photo(val jpeg: String) : ProfileAvatar
}

internal data class PersonalProfile(
    val nickname: String? = null,
    val avatar: ProfileAvatar = ProfileAvatar.Preset(AvatarPreset.DEFAULT),
) {
    val displayName get() = nickname ?: "学习者"
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
    val treeId: String, val treeName: String, val style: CertificateStyle, val recipient: String?,
)

internal data class DirectionProgress(val id: String, val name: String, val progress: Progress)

internal data class LearningStatistics(
    val skills: Progress, val directions: List<DirectionProgress>, val passedTrees: Int,
) {
    companion object {
        fun from(library: Library, assessments: Map<String, TreeAssessmentSummary>) = LearningStatistics(
            library.progress(),
            library.childrenOf(null).map { DirectionProgress(it.id, it.name, library.progress(it.id)) },
            assessments.count { (id, summary) -> summary.passed && library.byId[id]?.kind == EntryKind.TREE },
        )
    }
}
