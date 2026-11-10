package com.lifeskill.app.model

internal data class MapPoint(val column: Float, val row: Int)
internal data class MapConnection(val edge: Edge, val points: List<MapPoint>)

internal data class SkillMapLayout(
    val nodes: Map<String, MapPoint>,
    val width: Float,
    val rowCount: Int,
    val connections: List<MapConnection>,
    val references: List<Edge>,
) {
    companion object {
        fun arrange(ids: List<String>, edges: List<Edge>): SkillMapLayout = LayeredMap(ids, edges).arrange()
    }
}

private class LayeredMap(ids: List<String>, edges: List<Edge>) {
    private data class Vertex(val row: Int, val skill: String?)
    private data class Route(val edge: Edge, val vertices: List<Int>)
    private data class Segment(val start: Int, val end: Int)

    private val vertices = mutableListOf<Vertex>()
    private val levels = SkillGraph.levels(ids, edges)
    private val rows = ids.groupBy(levels::getValue).toSortedMap().values.flatMap { it.chunked(2) }.mapIndexed { row, skills ->
        skills.map { addVertex(row, it) }.toMutableList()
    }
    private val skills = vertices.mapIndexed { index, vertex -> checkNotNull(vertex.skill) to index }.toMap()
    // 旧跨层依赖仍用于解锁，在画布上只显示关联编号。
    private val edgesByAdjacency = edges.distinct().filter { it.prerequisiteId in skills && it.skillId in skills }
        .partition { SkillGraph.isAdjacent(it, levels) }
    private val routes = edgesByAdjacency.first.map { edge ->
        val source = skills.getValue(edge.prerequisiteId)
        val target = skills.getValue(edge.skillId)
        val firstRow = vertices[source].row
        val lastRow = vertices[target].row
        // 相邻逻辑层仍可能因换行而隔开，给连线留窄通道。
        val tracks = (firstRow + 1 until lastRow).map { row ->
            addVertex(row, null).also { rows[row].add(it) }
        }
        Route(edge, listOf(source) + tracks + target)
    }.sortedWith(compareBy({ it.vertices.size }, { it.vertices.first() }, { it.vertices.last() }))

    fun arrange(): SkillMapLayout {
        val previous = List(vertices.size) { mutableListOf<Int>() }
        val next = List(vertices.size) { mutableListOf<Int>() }
        routes.forEach { route ->
            route.vertices.zipWithNext { from, to ->
                previous[to].add(from)
                next[from].add(to)
            }
        }
        val order = IntArray(vertices.size)
        rows.forEach { row -> row.forEachIndexed { index, vertex -> order[vertex] = index } }
        var best = rows.map { it.toList() }
        var accepted = select(order)
        // 上下扫动，把同一分支的节点排在一起。只有保留更多连线时才替换当前顺序。
        for (pass in 0 until 8) {
            if (accepted.size == routes.size) break
            val downward = pass % 2 == 0
            val levels = if (downward) 1 until rows.size else rows.lastIndex - 1 downTo 0
            for (level in levels) {
                val row = rows[level]
                val neighbors = if (downward) previous else next
                val neighborCount = rows[if (downward) level - 1 else level + 1].size
                row.sortBy { vertex ->
                    val linked = neighbors[vertex]
                    if (linked.isEmpty()) fraction(order[vertex], row.size)
                    else linked.map { fraction(order[it], neighborCount) }.average()
                }
                row.forEachIndexed { index, vertex -> order[vertex] = index }
            }
            val candidate = select(order)
            if (candidate.size > accepted.size) {
                accepted = candidate
                best = rows.map { it.toList() }
            }
        }
        return position(best, accepted)
    }

    private fun select(order: IntArray): Set<Route> = buildSet {
        val occupied = List(rows.size) { mutableListOf<Segment>() }
        val branches = IntArray(vertices.size)
        val tracks = IntArray(rows.size)
        for (route in routes) {
            val source = route.vertices.first()
            val trackRows = vertices[source].row + 1 until vertices[route.vertices.last()].row
            // 固定横向通道预算。旧树的额外分支及挤不下的跨行关系转成关联编号。
            if (branches[source] >= SkillGraph.BRANCH_LIMIT || trackRows.any { tracks[it] >= TRACKS_PER_ROW }) continue
            val segments = route.vertices.zipWithNext(::Segment)
            val blocked = segments.any { segment ->
                occupied[vertices[segment.start].row].any { existing ->
                    val first = order[segment.start].compareTo(order[existing.start])
                    val second = order[segment.end].compareTo(order[existing.end])
                    // 共享同一端点是合法分叉或汇合。只有上下两端顺序相反才是交叉。
                    first * second < 0
                }
            }
            if (!blocked) {
                add(route)
                branches[source]++
                trackRows.forEach { tracks[it]++ }
                segments.forEach { occupied[vertices[it.start].row].add(it) }
            }
        }
    }

    private fun position(order: List<List<Int>>, accepted: Set<Route>): SkillMapLayout {
        val used = accepted.flatMap { it.vertices }.toSet() + skills.values
        val visible = order.map { row -> row.filter { it in used } }
        val widths = visible.map { row -> row.sumOf { widthOf(it) } + GAP * (row.size - 1).coerceAtLeast(0) }
        val worldWidth = widths.maxOrNull() ?: 0.0
        val points = mutableMapOf<Int, MapPoint>()
        visible.forEachIndexed { row, verticesInRow ->
            var left = (worldWidth - widths[row]) / 2
            for (vertex in verticesInRow) {
                val width = widthOf(vertex)
                points[vertex] = MapPoint((left + width / 2).toFloat(), row)
                left += width + GAP
            }
        }
        return SkillMapLayout(
            nodes = skills.mapValues { (_, vertex) -> points.getValue(vertex) },
            width = worldWidth.toFloat(),
            rowCount = visible.size,
            connections = accepted.map { route -> MapConnection(route.edge, route.vertices.map(points::getValue)) },
            references = routes.filterNot { it in accepted }.map(Route::edge) + edgesByAdjacency.second,
        )
    }

    private fun addVertex(row: Int, skill: String?): Int {
        vertices.add(Vertex(row, skill))
        return vertices.lastIndex
    }

    private fun widthOf(vertex: Int) = if (vertices[vertex].skill != null) 1.0 else .12

    private fun fraction(index: Int, count: Int) = if (count <= 1) .5 else index.toDouble() / (count - 1)

    private companion object {
        const val GAP = .185
        const val TRACKS_PER_ROW = 3
    }
}
