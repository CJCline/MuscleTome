package com.chy.muscletome.ui.home

import com.chy.muscletome.data.local.entity.RoutineDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Up next" must be scoped to the active routine and never interleave programs. */
class NextDayPlannerTest {

    private fun day(id: String, routineId: String = "r1", orderIndex: Int) =
        RoutineDayEntity(id = id, routineId = routineId, name = id, orderIndex = orderIndex)

    private val pplDays = listOf(
        day("push", orderIndex = 0),
        day("pull", orderIndex = 2),
        day("legs", orderIndex = 1),
    )

    @Test
    fun noActiveRoutineIsNull() {
        assertNull(NextDayPlanner.nextDay(null, null, pplDays))
    }

    @Test
    fun noDaysIsNull() {
        assertNull(NextDayPlanner.nextDay("r1", null, emptyList()))
    }

    @Test
    fun noHistoryStartsAtFirstDayByOrderIndex() {
        // Insertion order is scrambled (push, pull, legs) — orderIndex rules.
        assertEquals("push", NextDayPlanner.nextDay("r1", null, pplDays)?.id)
    }

    @Test
    fun advancesToNextDayInOrder() {
        assertEquals("legs", NextDayPlanner.nextDay("r1", "push", pplDays)?.id)
        assertEquals("pull", NextDayPlanner.nextDay("r1", "legs", pplDays)?.id)
    }

    @Test
    fun wrapsAfterLastDay() {
        assertEquals("push", NextDayPlanner.nextDay("r1", "pull", pplDays)?.id)
    }

    @Test
    fun lastCompletedInAnotherRoutineDoesNotInterleave() {
        // A finished session from a different program must not advance this
        // routine's pointer — it starts (or stays) at its first day.
        val days = pplDays + day("fb-a", routineId = "r2", orderIndex = 0)
        assertEquals("push", NextDayPlanner.nextDay("r1", "fb-a", days)?.id)
    }

    @Test
    fun stalePointerFallsBackToFirstDay() {
        // The routine was deleted and recreated; old day ids no longer exist.
        assertEquals("push", NextDayPlanner.nextDay("r1", "deleted-day", pplDays)?.id)
    }
}
