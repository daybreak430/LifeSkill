package com.lifeskill.app.model

internal enum class CertificateTheme { TECH, BUSINESS, COMMUNICATION, ARTS }

internal data class CertificateStyle(
    val theme: CertificateTheme,
    val emblem: EntryIcon,
    val cornerVariant: Int,
) {
    val id get() = "${theme.name}/${emblem.name}/$cornerVariant"

    companion object {
        private const val CORNER_VARIANTS = 16

        fun forTree(tree: Entry): CertificateStyle {
            val emblem = tree.displayIcon
            val theme = when (emblem) {
                EntryIcon.CODE, EntryIcon.TOOLS -> CertificateTheme.TECH
                EntryIcon.SPEECH, EntryIcon.BOOK -> CertificateTheme.COMMUNICATION
                EntryIcon.MUSIC, EntryIcon.CAMERA, EntryIcon.LEAF,
                EntryIcon.HOBBY, EntryIcon.EXPLORE -> CertificateTheme.ARTS
                EntryIcon.DIAMOND, EntryIcon.TREE, EntryIcon.WORK, EntryIcon.SIDE,
                EntryIcon.PRACTICAL, EntryIcon.TARGET, EntryIcon.CHART -> CertificateTheme.BUSINESS
            }
            return CertificateStyle(theme, emblem, tree.id.hashCode().mod(CORNER_VARIANTS))
        }

        fun fromId(id: String): CertificateStyle? {
            val parts = id.split('/')
            if (parts.size != 3) return null
            val theme = CertificateTheme.entries.firstOrNull { it.name == parts[0] } ?: return null
            val emblem = EntryIcon.fromId(parts[1]) ?: return null
            val corner = parts[2].toIntOrNull()?.takeIf { it in 0 until CORNER_VARIANTS } ?: return null
            return CertificateStyle(theme, emblem, corner)
        }
    }
}
