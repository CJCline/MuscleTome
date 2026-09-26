package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.session.SupersetFlow.followUpAfterSet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-robin matrix for superset navigation. Fixture ids: A, B, C are
 * group members in session order; `solo` is an ungrouped exercise.
 */
class SupersetFlowTest {

    private fun member(
        id: String,
        planned: Int,
        done: Int,
        rest: Int = 90,
    ) = SupersetMember(id, planned, done, rest)

    @Test
    fun ungroupedSetRestsAndStays() {
        val followUp = followUpAfterSet(listOf(member("solo", 3, 1)), "solo", 120)
        assertEquals(SupersetFollowUp.Stay(120), followUp)
    }

    @Test
    fun pairAdvancesToPartnerWithNoRestMidRound() {
        val members = listOf(member("A", 3, 1), member("B", 3, 0))
        // A just logged set 1 → hop to B, no rest yet.
        assertEquals(
            SupersetFollowUp.Advance("B", 0),
            followUpAfterSet(members, "A", 90),
        )
    }

    @Test
    fun pairWrapStartsRestOnTheNextRound() {
        val members = listOf(member("A", 3, 1), member("B", 3, 1))
        // B just logged set 1 → round wrapped → rest (B's own) before A.
        assertEquals(
            SupersetFollowUp.Advance("A", 90),
            followUpAfterSet(members, "B", 90),
        )
    }

    @Test
    fun wrapRestUsesTheJustCompletedMembersRest() {
        val members = listOf(
            member("A", 3, 1, rest = 120),
            member("B", 3, 1, rest = 45),
        )
        // B (rest 45) wrapped the round — not A's 120.
        assertEquals(
            SupersetFollowUp.Advance("A", 45),
            followUpAfterSet(members, "B", 45),
        )
    }

    @Test
    fun circuitChainsWithoutRestUntilTheRoundWraps() {
        // A → B → C mid-round: no rest anywhere.
        assertEquals(
            SupersetFollowUp.Advance("B", 0),
            followUpAfterSet(
                listOf(member("A", 3, 1), member("B", 3, 0), member("C", 3, 0)),
                "A",
                90,
            ),
        )
        assertEquals(
            SupersetFollowUp.Advance("C", 0),
            followUpAfterSet(
                listOf(member("A", 3, 1), member("B", 3, 1), member("C", 3, 0)),
                "B",
                90,
            ),
        )
        // C wrapped: rest, then the second round opens on A.
        assertEquals(
            SupersetFollowUp.Advance("A", 90),
            followUpAfterSet(
                listOf(member("A", 3, 1), member("B", 3, 1), member("C", 3, 1)),
                "C",
                90,
            ),
        )
    }

    @Test
    fun completedMembersAreSkipped() {
        // B is done; A's hop skips straight to C with no rest (no wrap).
        assertEquals(
            SupersetFollowUp.Advance("C", 0),
            followUpAfterSet(
                listOf(member("A", 3, 1), member("B", 2, 2), member("C", 3, 0)),
                "A",
                90,
            ),
        )
    }

    @Test
    fun wrapLandingOnSkippedMembersStillStartsTheRound() {
        // A is done. C just logged; the walk wraps, skips A, lands on B —
        // with rest, because a new round opens no matter who it lands on.
        assertEquals(
            SupersetFollowUp.Advance("B", 90),
            followUpAfterSet(
                listOf(member("A", 2, 2), member("B", 3, 0), member("C", 3, 1)),
                "C",
                90,
            ),
        )
    }

    @Test
    fun roundWrappingBackToTheSenderRestsInPlace() {
        // A still has sets, B is done. A's hop wraps to A itself → stay, rest.
        assertEquals(
            SupersetFollowUp.Stay(90),
            followUpAfterSet(
                listOf(member("A", 3, 2), member("B", 1, 1)),
                "A",
                90,
            ),
        )
    }

    @Test
    fun groupDoneRestsAndStays() {
        // Every member reached its planned count: rest on the spot; the
        // UI's normal "Next exercise" / "Finish" flow takes over.
        assertEquals(
            SupersetFollowUp.Stay(90),
            followUpAfterSet(
                listOf(member("A", 2, 2), member("B", 2, 2)),
                "B",
                90,
            ),
        )
    }

    @Test
    fun oneShotPartnerHandsBackToAnchorWithWrapRest() {
        // Ad-hoc "just this one set": partner B (planned 1) logs its only
        // set and wraps the round back to A — with rest, as any wrap does.
        assertEquals(
            SupersetFollowUp.Advance("A", 90),
            followUpAfterSet(
                listOf(member("A", 3, 1), member("B", 1, 1)),
                "B",
                90,
            ),
        )
    }

    @Test
    fun pairRemainingSetsFinishTogether() {
        // Anchor A planned 3 (1 done); partner B mirrors remaining 2 sets.
        // After A's set: straight to B.
        assertEquals(
            SupersetFollowUp.Advance("B", 0),
            followUpAfterSet(
                listOf(member("A", 3, 2), member("B", 2, 1)),
                "A",
                90,
            ),
        )
        // After B's last: wrap → rest → back to A for the final set.
        assertEquals(
            SupersetFollowUp.Advance("A", 90),
            followUpAfterSet(
                listOf(member("A", 3, 2), member("B", 2, 2)),
                "B",
                90,
            ),
        )
    }
}
