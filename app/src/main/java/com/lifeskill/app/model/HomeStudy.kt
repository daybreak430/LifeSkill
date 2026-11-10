package com.lifeskill.app.model

internal enum class HomeStudyState { RESUME, LOCKED, NEXT, COMPLETED }

internal data class HomeStudy(
    val tree: Entry,
    val skill: Entry,
    val progress: Progress,
    val state: HomeStudyState,
)

internal fun Library.homeStudy(recent: Entry?): HomeStudy? {
    val skill = byId[recent?.id]?.takeIf { it.kind == EntryKind.SKILL } ?: return null
    val tree = byId[skill.parentId] ?: return null
    val progress = progress(tree.id)
    return when (skillState(skill.id)) {
        SkillState.AVAILABLE -> HomeStudy(tree, skill, progress, HomeStudyState.RESUME)
        SkillState.LOCKED -> HomeStudy(tree, skill, progress, HomeStudyState.LOCKED)
        SkillState.LIT -> {
            if (progress.lit == progress.total) return HomeStudy(tree, skill, progress, HomeStudyState.COMPLETED)
            val available = childrenOf(tree.id).filter { skillState(it.id) == SkillState.AVAILABLE }
            // 优先延续刚学完的分支；没有直接后续时，选同一棵树里已经解锁的技能。
            val next = available.firstOrNull { skill.id in prerequisitesOf(it.id) } ?: available.firstOrNull()
            if (next != null) HomeStudy(tree, next, progress, HomeStudyState.NEXT)
            else HomeStudy(tree, skill, progress, HomeStudyState.RESUME)
        }
    }
}
