package com.lifeskill.app.model

internal data class LessonArrow(val from: String, val to: String, val label: String?)
internal data class LessonFlow(
    val labels: Map<String, String>,
    val tiers: List<List<String>>,
    val arrows: List<LessonArrow>,
    val decisions: Set<String>,
) {
    companion object {
        private val header = Regex("(?:flowchart|graph)\\s+(?:TD|TB)")
        private val step = Regex("""([A-Za-z][A-Za-z0-9_]{0,15})(?:\[([^\[\]]{1,48})\]|\{([^{}]{1,48})\})?""")
        private val arrow = Regex("""\s*-->\s*(?:\|([^|]{1,18})\|\s*)?""")

        // 只识别可在本地绘制的纵向步骤图，其他语法交给代码块展示。
        fun parse(source: String): LessonFlow? {
            val lines = source.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            if (lines.size !in 2..17 || !header.matches(lines.first())) return null
            val nodes = linkedMapOf<String, String?>()
            val edges = mutableListOf<LessonArrow>()
            val decisions = mutableSetOf<String>()
            fun readStep(text: String): String? {
                val match = step.matchEntire(text.trim()) ?: return null
                val id = match.groupValues[1]
                val decision = match.groups[3] != null
                val label = (match.groups[2] ?: match.groups[3])?.value?.trim()?.removeSurrounding("\"")
                val previous = nodes[id]
                if (label != null && previous != null && (label != previous || decision != (id in decisions))) return null
                if (decision) decisions.add(id)
                nodes[id] = label ?: previous
                return id
            }
            for (line in lines.drop(1)) {
                val connection = arrow.find(line)
                if (connection == null) {
                    if (readStep(line) == null) return null
                } else {
                    val from = readStep(line.substring(0, connection.range.first)) ?: return null
                    val to = readStep(line.substring(connection.range.last + 1)) ?: return null
                    edges.add(LessonArrow(from, to, connection.groups[1]?.value))
                }
            }
            if (nodes.size !in 2..8 || edges.isEmpty()) return null
            val tiers = runCatching { SkillGraph.tiers(nodes.keys.toList(), edges.map { Edge(it.to, it.from) }) }
                .getOrNull() ?: return null
            return LessonFlow(nodes.mapValues { (id, label) -> label ?: id }, tiers, edges.distinct(), decisions)
        }
    }
}
