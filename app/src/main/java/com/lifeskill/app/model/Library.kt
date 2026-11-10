package com.lifeskill.app.model

import java.text.Normalizer
import java.text.BreakIterator
import java.util.Locale

enum class EntryKind(val label: String) {
    DIRECTION("方向"), TREE("技能树"), SKILL("技能点");

    val childKind: EntryKind?
        get() = when (this) {
            DIRECTION -> TREE
            TREE -> SKILL
            SKILL -> null
        }
}

// MANUAL 用于读取旧版本的点亮记录。
enum class Mastery { UNLIT, MANUAL, AI }

enum class SkillState(val label: String) {
    LOCKED("未解锁"), AVAILABLE("可点亮"), LIT("已点亮"),
}

data class Entry(
    val id: String,
    val parentId: String?,
    val kind: EntryKind,
    val name: String,
    val goal: String = "",
    val mastery: Mastery = Mastery.UNLIT,
    val contentVersion: Long = 0,
    val masteryVersion: Long = 0,
    val icon: EntryIcon? = null,
    val shortName: String? = null,
) {
    val lit get() = mastery != Mastery.UNLIT
    val mapName get() = shortName ?: LibraryRules.mapName(name)
    val displayIcon get() = icon ?: EntryIcon.default(kind, id)
}

data class Edge(val skillId: String, val prerequisiteId: String)

data class Progress(val lit: Int, val total: Int) {
    val fraction get() = if (total == 0) 0f else lit.toFloat() / total
}

data class Library(val entries: List<Entry> = emptyList(), val edges: List<Edge> = emptyList()) {
    val byId = entries.associateBy(Entry::id)
    private val children = entries.groupBy(Entry::parentId)
    private val prerequisites = edges.groupBy(Edge::skillId)
    private val dependents = edges.groupBy(Edge::prerequisiteId)

    private val skillStates by lazy {
        val ids = entries.filter { it.kind == EntryKind.SKILL }.map(Entry::id)
        val states = mutableMapOf<String, SkillState>()
        // 按拓扑顺序计算有效进度。旧版越级点亮的记录保留，补齐前置后恢复显示。
        SkillGraph.tiers(ids, edges).flatten().forEach { id ->
            states[id] = when {
                prerequisites[id].orEmpty().any { states[it.prerequisiteId] != SkillState.LIT } -> SkillState.LOCKED
                byId.getValue(id).lit -> SkillState.LIT
                else -> SkillState.AVAILABLE
            }
        }
        states
    }

    fun childrenOf(id: String?) = children[id].orEmpty()
    fun prerequisitesOf(id: String) = prerequisites[id].orEmpty().map(Edge::prerequisiteId).toSet()
    fun skillState(id: String) = skillStates.getValue(id)
    fun remainingBranches(id: String) = (SkillGraph.BRANCH_LIMIT - dependents[id].orEmpty().size).coerceAtLeast(0)

    fun skillsUnder(id: String?): List<Entry> = when (byId[id]?.kind) {
        EntryKind.SKILL -> listOfNotNull(byId[id])
        EntryKind.TREE -> childrenOf(id)
        EntryKind.DIRECTION -> childrenOf(id).flatMap { childrenOf(it.id) }
        null -> entries.filter { it.kind == EntryKind.SKILL }
    }

    fun progress(id: String? = null): Progress {
        val skills = skillsUnder(id)
        return Progress(skills.count { skillState(it.id) == SkillState.LIT }, skills.size)
    }
}

open class RuleViolation(message: String) : IllegalArgumentException(message)

object LibraryRules {
    const val NAME_LIMIT = 48
    const val GOAL_LIMIT = 320
    const val SHORT_NAME_LIMIT = 5

    fun shortName(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any(Char::isISOControl) || boundaries(trimmed).size !in 2..SHORT_NAME_LIMIT) {
            throw RuleViolation("节点简称需要 2–5 个字，且不能换行。")
        }
        return trimmed
    }

    fun mapName(name: String): String {
        val ends = boundaries(name)
        return if (ends.size == 1) "${name}课"
        else ends.getOrNull(SHORT_NAME_LIMIT - 1)?.let { name.substring(0, it) } ?: name
    }

    private fun boundaries(text: String): List<Int> = buildList {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            add(end)
            end = iterator.next()
        }
    }

    fun name(value: String): String = value.trim().also {
        if (it.isBlank() || it.length > NAME_LIMIT || it.any(Char::isISOControl)) {
            throw RuleViolation("名称需要 1–${NAME_LIMIT} 个字符，且不能换行。")
        }
    }

    fun goal(value: String): String = value.trim().also {
        if (it.isBlank() || it.length > GOAL_LIMIT) {
            throw RuleViolation("技能说明需要 1–${GOAL_LIMIT} 个字符。")
        }
    }

    fun checkUnique(name: String, siblings: Iterable<String>) {
        val key = normalize(name)
        if (siblings.any { normalize(it) == key }) throw RuleViolation("这一层已有同名项目：$name")
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
}

object SkillGraph {
    const val BRANCH_LIMIT = 2

    fun validate(skillIds: Set<String>, edges: List<Edge>, previousEdges: List<Edge> = emptyList()) {
        if (edges.any { it.skillId !in skillIds || it.prerequisiteId !in skillIds }) {
            throw RuleViolation("前置节点必须与当前节点属于同一棵技能树。")
        }
        if (edges.any { it.skillId == it.prerequisiteId }) {
            throw RuleViolation("技能点不能把自己设为前置节点。")
        }
        tiers(skillIds.toList(), edges)
        val previousChildren = previousEdges.groupBy(Edge::prerequisiteId)
        for ((parent, outgoing) in edges.distinct().groupBy(Edge::prerequisiteId)) {
            // 旧树允许逐步减少超额分支，保存名称或说明时也不丢弃原有关系。
            if (outgoing.size > BRANCH_LIMIT && !previousChildren[parent].orEmpty().containsAll(outgoing)) {
                throw RuleViolation("每个技能点最多通往 $BRANCH_LIMIT 个后续技能点，请选择还有空位的前置节点。")
            }
        }
    }

    // 用拓扑顺序计算最长依赖路径，手动编辑校验和画布布局复用这一规则。
    fun tiers(skillIds: List<String>, edges: List<Edge>): List<List<String>> {
        val depths = skillIds.associateWith { 0 }.toMutableMap()
        val visible = edges.distinct().filter { it.skillId in depths && it.prerequisiteId in depths }
        val outgoing = visible.groupBy(Edge::prerequisiteId)
        val incoming = visible.groupingBy(Edge::skillId).eachCount().toMutableMap()
        val ready = ArrayDeque(skillIds.filter { it !in incoming })
        var visited = 0
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            visited++
            for (edge in outgoing[id].orEmpty()) {
                val nextId = edge.skillId
                depths[nextId] = maxOf(depths.getValue(nextId), depths.getValue(id) + 1)
                val remaining = incoming.getValue(nextId) - 1
                incoming[nextId] = remaining
                if (remaining == 0) ready.addLast(nextId)
            }
        }
        if (visited != skillIds.size) throw RuleViolation("这些前置关系会形成循环。")
        return skillIds.groupBy { depths.getValue(it) }.toSortedMap().values.toList()
    }
}
