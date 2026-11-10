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

    @Test fun routesLongEdgesThroughTheirOwnTrack() {
        val nodes = listOf("a", "b", "c", "d")
        val longEdge = Edge("d", "a")
        val edges = listOf(Edge("b", "a"), Edge("c", "b"), Edge("d", "c"), longEdge)
        val map = SkillMapLayout.arrange(nodes, edges)
        val route = map.connections.single { it.edge == longEdge }
        assertEquals(listOf(0, 1, 2, 3), route.points.map { it.row })
        assertTrue(map.references.isEmpty())
        assertSeparated(map, edges)
    }

    @Test fun keepsConflictingDependenciesAsReferences() {
        val edges = listOf(Edge("c", "a"), Edge("d", "a"), Edge("c", "b"), Edge("d", "b"))
        val map = SkillMapLayout.arrange(listOf("a", "b", "c", "d"), edges)
        assertEquals(3, map.connections.size)
        assertEquals(1, map.references.size)
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
