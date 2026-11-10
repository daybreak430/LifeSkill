package com.lifeskill.app.model

enum class EntryIcon(val label: String) {
    DIAMOND("晶石"), TREE("分支"), WORK("工作"), SIDE("成长"),
    HOBBY("星星"), PRACTICAL("清单"), EXPLORE("探索"), TARGET("目标"),
    BOOK("阅读"), CODE("编程"), CHART("数据"), SPEECH("表达"),
    MUSIC("音乐"), CAMERA("摄影"), LEAF("自然"), TOOLS("工具");

    companion object {
        fun fromId(id: String?): EntryIcon? = entries.firstOrNull { it.name == id }

        fun default(kind: EntryKind, id: String? = null): EntryIcon = when (kind) {
            EntryKind.SKILL -> DIAMOND
            EntryKind.TREE -> TREE
            EntryKind.DIRECTION -> when (id) {
                "preset-0" -> WORK
                "preset-1" -> SIDE
                "preset-2" -> HOBBY
                "preset-3" -> PRACTICAL
                else -> EXPLORE
            }
        }
    }
}
