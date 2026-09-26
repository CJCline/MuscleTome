package com.chy.muscletome.domain.routine

import com.chy.muscletome.domain.routine.SupersetGrouper.Assignment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Grouping rules for the day editor. Fixtures are (slotId, groupId) pairs
 * in day order; null groupId = standalone slot.
 */
class SupersetGrouperTest {

    private val s1 = "s1" to null
    private val s2 = "s2" to null

    @Test
    fun groupWithNextCreatesANewGroup() {
        val assignments = SupersetGrouper.groupWithNext(listOf(s1, s2), "s1")
        assertEquals(
            listOf(Assignment("s1", "s1"), Assignment("s2", "s1")),
            assignments,
        )
    }

    @Test
    fun groupWithNextJoinsTheNextSlotsExistingGroup() {
        // s2 is already in group G; s1 joins it.
        val assignments = SupersetGrouper.groupWithNext(
            listOf("s1" to null, "s2" to "G"),
            "s1",
        )
        assertEquals(
            listOf(Assignment("s1", "G"), Assignment("s2", "G")),
            assignments,
        )
    }

    @Test
    fun groupWithNextExtendsAChainWhenNextIsUngrouped() {
        // s1 is the LAST member of group G; s2 follows it ungrouped — the
        // classic circuit-extension tap: s2 joins G.
        val assignments = SupersetGrouper.groupWithNext(
            listOf("s1" to "G", "s2" to null),
            "s1",
        )
        assertEquals(
            listOf(Assignment("s1", "G"), Assignment("s2", "G")),
            assignments,
        )
    }

    @Test
    fun groupWithNextOnTheVeryLastSlotIsANoOp() {
        assertEquals(
            emptyList<Assignment>(),
            SupersetGrouper.groupWithNext(listOf(s1, s2), "s2"),
        )
    }

    @Test
    fun groupWithNextOnUnknownSlotIsANoOp() {
        assertEquals(
            emptyList<Assignment>(),
            SupersetGrouper.groupWithNext(listOf(s1, s2), "ghost"),
        )
    }

    @Test
    fun ungroupPullsTheSlotOutOfAnIntactGroup() {
        val assignments = SupersetGrouper.ungroup(
            listOf("s1" to "G", "s2" to "G", "s3" to "G"),
            "s2",
        )
        assertEquals(listOf(Assignment("s2", null)), assignments)
    }

    @Test
    fun ungroupDissolvesAStrandedPair() {
        // Pulling one member of a two-slot group strands the other — the
        // remainder dissolves entirely.
        val assignments = SupersetGrouper.ungroup(
            listOf("s1" to "G", "s2" to "G"),
            "s1",
        )
        assertEquals(
            listOf(Assignment("s1", null), Assignment("s2", null)),
            assignments,
        )
    }

    @Test
    fun ungroupOnStandaloneSlotIsANoOp() {
        assertEquals(
            emptyList<Assignment>(),
            SupersetGrouper.ungroup(listOf(s1, s2), "s1"),
        )
    }
}
