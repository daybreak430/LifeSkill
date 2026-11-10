package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.GenerationDraft
import com.lifeskill.app.ai.SkillDraft
import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.SkillGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID

internal data class EntryEdit(
    val id: String?,
    val parentId: String?,
    val kind: EntryKind,
    val name: String,
    val goal: String = "",
    val prerequisites: Set<String> = emptySet(),
    val icon: EntryIcon? = null,
    val shortName: String = "",
)

internal data class Creation(val entry: Entry, val directions: Int, val trees: Int, val skills: Int) {
    val summary get() = listOfNotNull(
        directions.takeIf { it > 0 }?.let { "${it} 个方向" },
        trees.takeIf { it > 0 }?.let { "${it} 棵技能树" },
        skills.takeIf { it > 0 }?.let { "${it} 个技能点" },
    ).joinToString("、", prefix = "已新增 ")
}

internal class LibraryRepository(private val database: LifeSkillDatabase, private val secret: SecretBox) {
    private val dao = database.skills()

    val library = database.invalidationTracker.createFlow("entries", "edges")
        .map { snapshot() }
        .flowOn(Dispatchers.IO)

    suspend fun initialize() = database.withTransaction {
        if (!dao.initialized()) {
            listOf("本职工作", "副业拓展", "爱好精进", "实用技能", "新领域").forEachIndexed { index, name ->
                dao.insertEntry(Entry("preset-$index", null, EntryKind.DIRECTION, name).row(index.toLong()))
            }
            dao.insertBootstrap(BootstrapRow())
        }
    }

    suspend fun snapshot(): Library = dao.library().let { rows ->
        Library(rows.entries.map { it.toEntry() }, rows.edges.map { Edge(it.skillId, it.prerequisiteId) })
    }

    suspend fun entry(id: String): Entry? = dao.entry(id)?.toEntry()

    suspend fun save(edit: EntryEdit): Entry = database.withTransaction {
        val current = snapshot()
        val parent = edit.parentId?.let { current.byId[it] ?: throw RuleViolation("上级项目已删除。") }
        val expectedKind = if (parent == null) EntryKind.DIRECTION else parent.kind.childKind
        if (expectedKind != edit.kind) {
            throw RuleViolation("无法在此层级创建${edit.kind.label}。")
        }
        val previousRow = edit.id?.let { dao.entry(it) ?: throw RuleViolation("该项目已删除。") }
        val previous = previousRow?.toEntry()
        if (previous != null && (previous.parentId != edit.parentId || previous.kind != edit.kind)) {
            throw RuleViolation("该项目的位置已变化，请重新打开。")
        }
        val name = LibraryRules.name(edit.name)
        LibraryRules.checkUnique(name, current.childrenOf(edit.parentId).filterNot { it.id == edit.id }.map(Entry::name))
        val goal = if (edit.kind == EntryKind.SKILL) LibraryRules.goal(edit.goal) else ""
        val shortName = if (edit.kind == EntryKind.SKILL) LibraryRules.shortName(edit.shortName) else null
        val saved = if (previous == null) {
            Entry(newId(), edit.parentId, edit.kind, name, goal, icon = edit.icon, shortName = shortName)
        } else {
            previous.copy(
                name = name,
                goal = goal,
                icon = edit.icon,
                shortName = shortName,
                contentVersion = previous.contentVersion + if (previous.name != name || previous.goal != goal) 1 else 0,
            )
        }
        val newEdges = edit.prerequisites.map { Edge(saved.id, it) }
        if (saved.kind == EntryKind.SKILL) {
            val ids = current.childrenOf(saved.parentId).map(Entry::id).toSet() + saved.id
            val otherEdges = current.edges.filter { it.skillId in ids && it.skillId != saved.id }
            SkillGraph.validate(ids, otherEdges + newEdges)
        } else if (newEdges.isNotEmpty()) {
            throw RuleViolation("只有技能点可以设置前置关系。")
        }
        if (previousRow == null) {
            dao.insertEntry(saved.row(dao.nextPosition()))
        } else {
            dao.updateEntry(saved.row(previousRow.position))
        }
        if (saved.kind == EntryKind.SKILL) {
            dao.removePrerequisites(saved.id)
            dao.insertEdges(newEdges.map { EdgeRow(it.skillId, it.prerequisiteId) })
        }
        saved
    }

    suspend fun delete(id: String) = database.withTransaction { dao.deleteEntry(id) }

    suspend fun setLit(id: String, lit: Boolean) = database.withTransaction {
        val previous = dao.entry(id) ?: throw RuleViolation("该技能点已删除。")
        val mastery = Mastery.valueOf(previous.mastery)
        if (previous.kind != EntryKind.SKILL.name) throw RuleViolation("只有技能点可以点亮。")
        if ((mastery != Mastery.UNLIT) != lit) {
            val next = if (lit) Mastery.MANUAL else Mastery.UNLIT
            dao.updateEntry(previous.copy(mastery = next.name, masteryVersion = previous.masteryVersion + 1))
        }
    }

    // 评估发出后若用户编辑目标或手动操作，旧结果不覆盖用户的新状态。
    suspend fun lightFromAssessment(expected: Entry): Boolean {
        val row = dao.entry(expected.id) ?: return false
        if (row.masteryVersion != expected.masteryVersion || row.contentVersion != expected.contentVersion) return false
        if (row.mastery != Mastery.UNLIT.name) return false
        dao.updateEntry(row.copy(mastery = Mastery.AI.name, masteryVersion = row.masteryVersion + 1))
        return true
    }

    // 对话仓库在同一事务内调用，生成内容和该轮回复同时提交。
    suspend fun applyDraft(owner: Entry?, draft: GenerationDraft): Creation {
        val current = snapshot()
        var directions = 0
        var skills = 0
        val target = when (owner?.kind) {
            null -> {
                if (draft.skills.isNotEmpty() || (draft.directionId == null) == (draft.directionName == null)) {
                    invalidScope()
                }
                if (draft.directionId != null) {
                    if (draft.trees.isEmpty()) invalidScope()
                    current.byId[draft.directionId]?.takeIf { it.kind == EntryKind.DIRECTION }
                        ?: throw RuleViolation("生成目标方向已不存在，请重试。")
                } else {
                    val name = LibraryRules.name(draft.directionName ?: invalidScope())
                    LibraryRules.checkUnique(name, current.childrenOf(null).map(Entry::name))
                    directions++
                    create(null, EntryKind.DIRECTION, name)
                }
            }
            EntryKind.DIRECTION -> {
                if (draft.directionId != null || draft.directionName != null ||
                    draft.skills.isNotEmpty() || draft.trees.isEmpty()
                ) invalidScope()
                owner
            }
            EntryKind.TREE -> {
                if (draft.directionId != null || draft.directionName != null ||
                    draft.trees.isNotEmpty() || draft.skills.isEmpty()
                ) invalidScope()
                skills = addSkills(owner, draft.skills, current)
                owner
            }
            EntryKind.SKILL -> invalidScope()
        }
        val existingTreeNames = current.childrenOf(target.id).map(Entry::name).toMutableList()
        val newTrees = draft.trees.map { tree ->
            val name = LibraryRules.name(tree.name)
            LibraryRules.checkUnique(name, existingTreeNames)
            existingTreeNames.add(name)
            create(target.id, EntryKind.TREE, name).also { skills += addSkills(it, tree.skills, current) }
        }
        return Creation(newTrees.singleOrNull() ?: target, directions, newTrees.size, skills)
    }

    private suspend fun addSkills(tree: Entry, drafts: List<SkillDraft>, library: Library): Int {
        val existing = library.childrenOf(tree.id)
        val existingIds = existing.map(Entry::id).toSet()
        val names = existing.map(Entry::name).toMutableList()
        val refIds = mutableMapOf<String, String>()
        val additions = drafts.map { draft ->
            if (!REF.matches(draft.ref) || draft.ref in refIds) {
                throw RuleViolation("AI 返回了重复或无效的技能点标识，请重试。")
            }
            val name = LibraryRules.name(draft.name)
            LibraryRules.checkUnique(name, names)
            names.add(name)
            Entry(newId(), tree.id, EntryKind.SKILL, name, LibraryRules.goal(draft.goal), shortName = draft.shortName).also {
                refIds[draft.ref] = it.id
            }
        }
        val addedEdges = drafts.flatMap { draft ->
            draft.requires.distinct().map { ref ->
                val prerequisite = if (ref.startsWith(EXISTING)) {
                    ref.removePrefix(EXISTING).takeIf { it in existingIds }
                } else {
                    refIds[ref]
                } ?: throw RuleViolation("AI 返回的前置节点不存在或属于另一棵技能树，请重试。")
                Edge(refIds.getValue(draft.ref), prerequisite)
            }
        }
        val previousEdges = library.edges.filter { it.skillId in existingIds }
        SkillGraph.validate(existingIds + refIds.values, previousEdges + addedEdges)
        var position = dao.nextPosition()
        for (entry in additions) dao.insertEntry(entry.row(position++))
        dao.insertEdges(addedEdges.map { EdgeRow(it.skillId, it.prerequisiteId) })
        return additions.size
    }

    private suspend fun create(parentId: String?, kind: EntryKind, name: String): Entry =
        Entry(newId(), parentId, kind, name).also { dao.insertEntry(it.row(dao.nextPosition())) }

    private fun Entry.row(position: Long) = EntryRow(
        id, parentId, kind.name, secret.seal(name, "$id:name"), secret.seal(goal, "$id:goal"),
        mastery.name, contentVersion, masteryVersion, position,
        icon?.name, shortName?.let { secret.seal(it, "$id:shortName") },
    )

    private fun EntryRow.toEntry() = Entry(
        id, parentId, EntryKind.valueOf(kind), secret.open(name, "$id:name"), secret.open(goal, "$id:goal"),
        Mastery.valueOf(mastery), contentVersion, masteryVersion,
        EntryIcon.fromId(icon), shortName?.let { secret.open(it, "$id:shortName") },
    )

    private fun invalidScope(): Nothing = throw RuleViolation("AI 返回的新增内容与当前层级不匹配，请重试。")

    private companion object {
        val REF = Regex("[a-zA-Z0-9_-]{1,40}")
        const val EXISTING = "existing:"
    }
}

internal fun newId(): String = UUID.randomUUID().toString()
