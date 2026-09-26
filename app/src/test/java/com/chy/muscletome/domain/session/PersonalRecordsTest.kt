package com.chy.muscletome.domain.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** PR rule matrix: first-ever counts, ties never re-tag, order is chronological. */
class PersonalRecordsTest {

    private fun set(id: String, atMs: Long, weight: Double, reps: Int) =
        PrCandidate(id, atMs, weight, reps)

    @Test
    fun epley1RmHandlesDegenerateAndNormalCases() {
        // Degenerate reps earn nothing — weight × 0 must not fake a record.
        assertEquals(0.0, PersonalRecords.epley1Rm(100.0, 0), 1e-9)
        // Single reps are the weight itself.
        assertEquals(140.0, PersonalRecords.epley1Rm(140.0, 1), 1e-9)
        // Epley: weight × (1 + reps / 30).
        assertEquals(133.33, PersonalRecords.epley1Rm(100.0, 10), 0.01)
        assertEquals(126.67, PersonalRecords.epley1Rm(95.0, 10), 0.01)
    }

    @Test
    fun firstEverSetIsAPr() {
        val prs = PersonalRecords.prSetIds(
            listOf(set("s1", 1000L, 60.0, 5)),
        )
        assertEquals(setOf("s1"), prs)
    }

    @Test
    fun strictlyBetterSetsTagAndWeakerOnesDoNot() {
        val prs = PersonalRecords.prSetIds(
            listOf(
                set("s1", 1000L, 60.0, 8), // PR: first ever
                set("s2", 2000L, 50.0, 8), // weaker — no tag
                set("s3", 3000L, 65.0, 8), // better — PR
                set("s4", 4000L, 70.0, 5), // e1RM 81.7 vs s3's 82.3 — no tag
            ),
        )
        assertEquals(setOf("s1", "s3"), prs)
    }

    @Test
    fun tiesDoNotRetag() {
        // Matching the record is not breaking it — only strictly better sets.
        val prs = PersonalRecords.prSetIds(
            listOf(
                set("s1", 1000L, 60.0, 8),
                set("s2", 2000L, 60.0, 8), // identical e1RM — not a PR
            ),
        )
        assertEquals(setOf("s1"), prs)
    }

    @Test
    fun equalE1RmDifferentSetupsTagOnlyTheFirst() {
        // 60×15 → e1RM 90; 90×5 → e1RM 105 vs... compute: 60*(1+15/30)=90,
        // 75*(1+6/30)=90 — a tie across different weight/rep combos.
        val prs = PersonalRecords.prSetIds(
            listOf(
                set("s1", 1000L, 60.0, 15), // e1RM 90 — PR
                set("s2", 2000L, 75.0, 6),  // e1RM 90 — tie, not a PR
            ),
        )
        assertEquals(setOf("s1"), prs)
    }

    @Test
    fun inputOrderDoesNotMatter() {
        // Sets arrive sorted by completion time regardless of list order.
        val shuffled = listOf(
            set("s3", 3000L, 65.0, 8),
            set("s1", 1000L, 60.0, 8),
            set("s2", 2000L, 50.0, 8),
        )
        val prs = PersonalRecords.prSetIds(shuffled)
        assertEquals(setOf("s1", "s3"), prs)
    }

    @Test
    fun zeroRepAndZeroWeightSetsNeverTag() {
        // e1RM 0 is not a record — a degenerate first set must not tag, so a
        // later ordinary set tags as the first real PR.
        val prs = PersonalRecords.prSetIds(
            listOf(
                set("s1", 1000L, 100.0, 0), // e1RM 0 — no tag
                set("s2", 2000L, 0.0, 8),   // e1RM 0 — no tag
                set("s3", 3000L, 50.0, 1),  // e1RM 50 — PR
            ),
        )
        assertEquals(setOf("s3"), prs)
    }
}
