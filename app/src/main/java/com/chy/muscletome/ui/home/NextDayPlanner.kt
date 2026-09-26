package com.chy.muscletome.ui.home

import com.chy.muscletome.data.local.entity.RoutineDayEntity

/**
 * Pure "Up next" selection, extracted so it can be unit-tested:
 * Home's next day is scoped to the ONE active routine, advancing from the
 * last completed day of that routine with wrap-around. A completed session
 * from a *different* routine never advances the pointer — programs don't
 * interleave.
 */
object NextDayPlanner {

    fun nextDay(
        activeRoutineId: String?,
        lastCompletedDayId: String?,
        days: List<RoutineDayEntity>,
    ): RoutineDayEntity? {
        if (activeRoutineId == null) return null
        val routineDays = days
            .filter { it.routineId == activeRoutineId }
            .sortedBy { it.orderIndex }
        if (routineDays.isEmpty()) return null
        val index = routineDays.indexOfFirst { it.id == lastCompletedDayId }
        // Not found = no history in this routine yet (or the pointer landed
        // here from another program) — start from the first day.
        if (index < 0) return routineDays.first()
        return routineDays[(index + 1) % routineDays.size]
    }
}
