package com.lifeskill.app

import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.SkillMapLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SkillMapLayoutTest {
    @Test fun reordersTheRowInsteadOfCrossingTheTwoBranches() {
        val nodes = listOf("start", "left", "right", "rightLesson", "leftLesson")
        val edges = listOf(
            Edge("left", "start"), Edge("right", "start"),
            Edge("leftLesson", "left"), Edge("rightLesson", "right"),
        )
        val map = SkillMapLayout.arrange(nodes, edges)
        assertTrue(map.references.isEmpty())
        assertTrue(map.nodes.getValue("leftLesson").column < map.nodes.getValue("rightLesson").column)
        assertSeparated(map, edges)
    }

    @Test fun showsOldCrossTierEdgesOnlyAsReferences() {
        val nodes = listOf("a", "b", "c", "d")
        val longEdge = Edge("d", "a")
        val edges = listOf(Edge("b", "a"), Edge("c", "b"), Edge("d", "c"), longEdge)
        val map = SkillMapLayout.arrange(nodes, edges)
        assertEquals(listOf(longEdge), map.references)
        assertEquals(3, map.connections.size)
        assertSeparated(map, edges)
    }

    @Test fun adjacentTiersCanStillConnectAcrossWrappedRows() {
        val nodes = listOf("first", "second", "third", "fourth", "child")
        val edge = Edge("child", "first")
        val map = SkillMapLayout.arrange(nodes, listOf(edge))
        assertEquals(listOf(0, 1, 2), map.connections.single().points.map { it.row })
        assertTrue(map.references.isEmpty())
    }

    @Test fun keepsConflictingDependenciesAsReferences() {
        val edges = listOf(Edge("c", "a"), Edge("d", "a"), Edge("c", "b"), Edge("d", "b"))
        val map = SkillMapLayout.arrange(listOf("a", "b", "c", "d"), edges)
        assertEquals(3, map.connections.size)
        assertEquals(1, map.references.size)
        assertSeparated(map, edges)
    }

    @Test fun wrapsWideTiersAndShowsOnlyTwoOutgoingLinesForLegacyBranches() {
        val nodes = listOf("root", "a", "b", "c", "d", "e")
        val edges = nodes.drop(1).map { Edge(it, "root") }
        val map = SkillMapLayout.arrange(nodes, edges)
        assertEquals(4, map.rowCount)
        assertEquals(2, map.connections.size)
        assertSeparated(map, edges)
    }

    @Test fun keepsAllDependenciesAndNeverCrossesAcrossVariedGraphs() {
        val random = Random(15)
        val ids = (0..19).map { it.toString() }
        repeat(24) {
            val edges = ids.flatMapIndexed { index, id ->
                ids.take(index).filter { random.nextInt(6) == 0 }.map { Edge(id, it) }
            }
            assertSeparated(SkillMapLayout.arrange(ids.shuffled(random), edges), edges)
        }
    }

    private fun assertSeparated(map: SkillMapLayout, edges: List<Edge>) {
        assertTrue(map.nodes.values.groupingBy { it.row }.eachCount().values.all { it <= 2 })
        assertTrue(map.width <= 3.11f)
        val represented = map.connections.map { it.edge } + map.references
        assertEquals(edges.size, represented.size)
        assertEquals(edges.toSet(), represented.toSet())
        val segments = map.connections.flatMap { route ->
            for (track in route.points.drop(1).dropLast(1)) {
                assertTrue(map.nodes.values.none { it.row == track.row && abs(it.column - track.column) <= .5f })
            }
            route.points.zipWithNext()
        }
        for ((index, first) in segments.withIndex()) {
            val (start, end) = first
            assertEquals(start.row + 1, end.row)
            for ((otherStart, otherEnd) in segments.drop(index + 1)) {
                if (otherStart.row == start.row) {
                    assertTrue((start.column - otherStart.column) * (end.column - otherEnd.column) >= 0f)
                }
            }
        }
    }
}
