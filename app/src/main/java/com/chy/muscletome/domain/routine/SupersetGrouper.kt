package com.chy.muscletome.domain.routine

/**
 * Pure routine-slot grouping rules, extracted so group/ungroup are
 * unit-testable without a database.
 *
 * A group is identified by the id stored on its members (`supersetGroupId`),
 * never by adjacency — reordering rows in the day editor never breaks a
 * circuit. `groupWithNext` links a slot with the slot that follows it in
 * day order; invoking it on the *last* slot of an existing group extends
 * the chain (that is how circuits of 3+ are built).
 */
object SupersetGrouper {

    /** One slot's new group id, or null to leave it standalone. */
    data class Assignment(val slotId: String, val groupId: String?)

    /**
     * Returns the [Assignment]s needed to group [slotId] with the next slot
     * in day order (which, if already grouped, extends/merges into that
     * group). Slots not mentioned keep their current membership.
     */
    fun groupWithNext(slots: List<Pair<String, String?>>, slotId: String): List<Assignment> {
        val index = slots.indexOfFirst { it.first == slotId }
        if (index < 0 || (index == slots.lastIndex)) return emptyList()
        val (id, currentGroup) = slots[index]
        val (nextId, nextGroup) = slots[index + 1]
        val groupId = nextGroup ?: currentGroup ?: id
        return listOf(Assignment(id, groupId), Assignment(nextId, groupId))
    }

    /**
     * Returns the [Assignment]s needed to pull [slotId] out of its group.
     * A group left with a single member by the removal is dissolved —
     * the stranded member becomes standalone too.
     */
    fun ungroup(slots: List<Pair<String, String?>>, slotId: String): List<Assignment> {
        val (id, currentGroup) = slots.find { it.first == slotId } ?: return emptyList()
        if (currentGroup == null) return emptyList()
        val remaining = slots.count { it.second == currentGroup && it.first != slotId }
        if (remaining == 1) {
            // Dissolve: pull the slot out and unstrand its only former mate.
            val stranded = slots.first { it.second == currentGroup && it.first != slotId }
            return listOf(Assignment(id, null), Assignment(stranded.first, null))
        }
        return listOf(Assignment(id, null))
    }
}
