package com.chy.muscletome.domain.session

/**
 * Pure superset/circuit navigation, extracted from the ViewModel so the
 * round-robin rules are unit-testable without Android.
 *
 * One superset group = slot results sharing a `supersetGroupId`, trained
 * round-robin: every member logs one set before anyone logs their next.
 * Members that hit their planned-set count are skipped; when the walk wraps
 * past the end of the group, the round is over and the just-completed
 * member's rest starts.
 */
data class SupersetMember(
    val resultId: String,
    /** Effective planned sets (ad-hoc override or routine slot's `sets`). */
    val plannedSets: Int,
    val completedSets: Int,
    /** Rest that fires after a round wraps on THIS member. */
    val restSeconds: Int,
)

/** What happens after a set is logged. */
sealed interface SupersetFollowUp {
    /** Stay on the current exercise; [restSeconds] counts down. */
    data class Stay(val restSeconds: Int) : SupersetFollowUp

    /**
     * Move to [targetResultId]; [restSeconds] counts down first (0 = move
     * immediately — the no-rest hop between exercises within a round).
     */
    data class Advance(val targetResultId: String, val restSeconds: Int) : SupersetFollowUp
}

object SupersetFlow {

    /**
     * Decides where the workout goes after one set of [currentResultId] is
     * logged. [members] must contain every result of the group **in session
     * order**, with post-log `completedSets` (i.e. the logged set already
     * counted). Ungrouped callers pass the lone result; it behaves exactly
     * like today's solo flow (rest, stay).
     */
    fun followUpAfterSet(
        members: List<SupersetMember>,
        currentResultId: String,
        currentRestSeconds: Int,
    ): SupersetFollowUp {
        val current = members.indexOfFirst { it.resultId == currentResultId }
        // Lone/unknown member: rest on the spot — today's ungrouped behavior.
        if (members.size <= 1 || (current < 0)) {
            return SupersetFollowUp.Stay(currentRestSeconds)
        }

        // Walk the ring starting right after the current member, hunting for
        // the next incomplete one. `steps` counts hops; the moment we step past
        // the ring's end we know the round wrapped — the rest that follows a
        // wrap is the just-completed member's [currentRestSeconds].
        val size = members.size
        var steps = 0
        while (steps < size) {
            val i = (current + 1 + steps) % size
            val candidate = members[i]
            if (candidate.completedSets < candidate.plannedSets) {
                if (candidate.resultId == currentResultId) {
                    // The round wrapped back to the sender: rest in place.
                    return SupersetFollowUp.Stay(currentRestSeconds)
                }
                val wrapped = (current + 1 + steps) > (size - 1)
                return if (wrapped) {
                    // The hop crossed the ring's end: full round done.
                    SupersetFollowUp.Advance(candidate.resultId, currentRestSeconds)
                } else {
                    // Mid-round hop: straight to the partner, no rest.
                    SupersetFollowUp.Advance(candidate.resultId, 0)
                }
            }
            steps++
        }
        // Full lap without an incomplete member: the group is done. Rest on
        // the spot; the UI's normal completion flow takes over.
        return SupersetFollowUp.Stay(currentRestSeconds)
    }
}
