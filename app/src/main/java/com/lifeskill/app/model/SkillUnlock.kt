package com.lifeskill.app.model

internal data class SkillUnlock(
    val skillId: String,
    val treeId: String,
    val masteryVersion: Long,
    val availableChildren: Set<String>,
) {
    val key get() = "$skillId:$masteryVersion"
}

// 只播放本次运行中由考试写入的新进度，旧记录和仍缺前置的分支不会触发。
internal fun Library.unlocksSince(previous: Library): List<SkillUnlock> =
    entries.mapNotNull { skill ->
        val old = previous.byId[skill.id]
        val treeId = skill.parentId ?: return@mapNotNull null
        if (skill.kind != EntryKind.SKILL || skill.mastery != Mastery.AI || old?.mastery != Mastery.UNLIT ||
            skillState(skill.id) != SkillState.LIT
        ) return@mapNotNull null
        val children = edges.asSequence().filter { it.prerequisiteId == skill.id }
            .map(Edge::skillId).filter { id ->
                id in previous.byId && previous.skillState(id) == SkillState.LOCKED && skillState(id) == SkillState.AVAILABLE
            }.toSet()
        SkillUnlock(skill.id, treeId, skill.masteryVersion, children)
    }
