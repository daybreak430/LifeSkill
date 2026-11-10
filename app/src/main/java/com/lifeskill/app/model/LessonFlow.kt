package com.lifeskill.app.model

internal data class LessonArrow(val from: String, val to: String, val label: String?)
internal data class LessonFlow(
    val labels: Map<String, String>,
    val tiers: List<List<String>>,
    val arrows: List<LessonArrow>,
    val returnArrows: List<LessonArrow>,
    val decisions: Set<String>,
) {
    companion object {
        private val header = Regex("""(?:flowchart|graph)(?:\s+(?:TD|TB|BT|LR|RL))?""", RegexOption.IGNORE_CASE)
        private val step = Regex(
            """([A-Za-z_][A-Za-z0-9_]{0,31})(?:\[([^\[\]]{1,64})\]|\{([^{}]{1,64})\}|\(\(([^()]{1,64})\)\)|\(([^()]{1,64})\))?""",
        )
        private val arrow = Regex("""\s*(?:(?:-->|==>|-\.->|→)(?:\s*\|([^|]{1,24})\|)?|--\s+([^\[\]{}|]{1,24}?)\s+-->)\s*""")

        // 各模型的写法统一为本地纵向布局。缺少图头时，整段必须符合节点与箭头语法。
        fun parse(source: String, mermaidFence: Boolean = false): LessonFlow? {
            val lines = source.lineSequence().map { it.trim().removeSuffix(";").trimEnd() }
                .filter { it.isNotEmpty() && !it.startsWith("%%") }.take(34).toList()
            if (lines.size !in 1..33) return null
            val declared = header.matches(lines.first())
            val nodes = linkedMapOf<String, String?>()
            val edges = mutableListOf<LessonArrow>()
            val decisions = mutableSetOf<String>()
            fun readStep(match: MatchResult): String? {
                val id = match.groupValues[1]
                val decision = match.groups[3] != null
                val label = match.groups.drop(2).firstOrNull { it != null }?.value?.trim()?.removeSurrounding("\"")
                val previous = nodes[id]
                if (label != null && previous != null && (label != previous || decision != (id in decisions))) return null
                if (decision) decisions.add(id)
                nodes[id] = label ?: previous
                return id
            }
            for (line in lines.drop(if (declared) 1 else 0)) {
                var node = step.matchAt(line, 0) ?: return null
                var from = readStep(node) ?: return null
                while (node.range.last < line.lastIndex) {
                    val connection = arrow.matchAt(line, node.range.last + 1) ?: return null
                    node = step.matchAt(line, connection.range.last + 1) ?: return null
                    val to = readStep(node) ?: return null
                    edges.add(LessonArrow(from, to, (connection.groups[1] ?: connection.groups[2])?.value?.trim()))
                    from = to
                    if (nodes.size > 16 || edges.size > 32) return null
                }
            }
            if (nodes.size !in 1..16 || edges.isEmpty()) return null
            if (!declared && !mermaidFence && edges.size == 1 && nodes.values.all { it == null }) return null
            val arrows = edges.distinct()
            val returns = findReturnArrows(nodes.keys, arrows)
            val forward = arrows.filterNot { it in returns }
            val tiers = SkillGraph.tiers(nodes.keys.toList(), forward.map { Edge(it.to, it.from) })
            return LessonFlow(nodes.mapValues { (id, label) -> label ?: id }, tiers, forward, arrows.filter { it in returns }, decisions)
        }

        private fun findReturnArrows(ids: Set<String>, arrows: List<LessonArrow>): Set<LessonArrow> {
            val outgoing = arrows.groupBy(LessonArrow::from)
            val visited = mutableSetOf<String>()
            val path = mutableSetOf<String>()
            val returns = mutableSetOf<LessonArrow>()
            // 指向当前路径祖先的边沿图侧边绘制，不参与分层。
            fun visit(id: String) {
                if (!visited.add(id)) return
                path.add(id)
                for (edge in outgoing[id].orEmpty()) {
                    if (edge.to in path) returns.add(edge) else visit(edge.to)
                }
                path.remove(id)
            }
            ids.forEach(::visit)
            return returns
        }
    }
}
