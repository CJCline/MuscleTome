package com.chy.muscletome.domain.session

/**
 * Personal-record logic: one canonical Epley e1RM plus the PR rule.
 *
 * A set is a PR when its e1RM strictly beat the best e1RM seen *before* it
 * (chronologically). First-ever set counts; matching the record does not —
 * ties never re-tag. Sets with no meaningful load (weight ≤ 0, e.g.
 * bodyweight at 0 kg, or zero reps) never tag: their e1RM is 0 and PRs are
 * about load progression. Derived, never stored: edits and deletes simply
 * recompute.
 */
object PersonalRecords {

    /** Epley estimated 1RM. Degenerate reps can't earn a record. */
    fun epley1Rm(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return weight
        return weight * (1.0 + reps / 30.0)
    }

    /**
     * The ids of sets that were all-time PRs at the moment they were
     * logged. [sets] may arrive in any order — it is sorted by completion
     * time first, matching the ledger's chronology.
     */
    fun prSetIds(sets: List<PrCandidate>): Set<String> {
        var best = 0.0
        val prs = mutableSetOf<String>()
        for (set in sets.sortedBy { it.completedAtEpochMs }) {
            val e1rm = epley1Rm(set.weight, set.reps)
            if (e1rm > best) {
                prs.add(set.id)
                best = e1rm
            }
        }
        return prs
    }
}

/** One set under PR consideration. */
data class PrCandidate(
    val id: String,
    val completedAtEpochMs: Long,
    val weight: Double,
    val reps: Int,
)
