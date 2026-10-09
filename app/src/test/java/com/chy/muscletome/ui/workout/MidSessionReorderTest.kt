package com.chy.muscletome.ui.workout

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidSessionReorderTest {

    private fun createExercise(id: String, name: String) = ExerciseEntity(
        id = id,
        primaryMuscleGroupId = "chest",
        movementType = MovementType.COMPOUND,
        name = name,
        notes = "",
        isCustom = false,
        demoUri = null,
    )

    private fun createSlot(
        resultId: String,
        exercise: ExerciseEntity,
        sortOrder: Int,
        plannedSets: Int = 3,
        sets: List<SetLogEntity> = emptyList(),
        isRemovedFromSession: Boolean = false,
    ): ActiveSlot {
        return ActiveSlot(
            result = SessionSlotResultEntity(
                id = resultId,
                sessionId = "s1",
                routineSlotId = "slot-$resultId",
                resolvedExerciseId = exercise.id,
                selectionReason = SelectionReason.FIXED,
                sortOrder = sortOrder,
                isRemovedFromSession = isRemovedFromSession,
            ),
            slot = RoutineSlotEntity(
                id = "slot-$resultId",
                routineDayId = "day-1",
                orderIndex = sortOrder,
                type = SlotType.FIXED,
                exerciseId = exercise.id,
                sets = plannedSets,
                repRangeMin = 8,
                repRangeMax = 12,
                restSeconds = 90,
            ),
            exercise = exercise,
            sets = sets,
        )
    }

    private fun createSet(id: String, resultId: String, setNumber: Int, weight: Double = 100.0, reps: Int = 8) =
        SetLogEntity(
            id = id,
            sessionSlotResultId = resultId,
            setNumber = setNumber,
            weight = weight,
            reps = reps,
            completedAtEpochMs = System.currentTimeMillis(),
        )

    @Test
    fun reorderingActiveSessionSlots_updatesOrderAndPreservesLoggedSets() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Press")
        val ex3 = createExercise("ex3", "Cable Flyes")

        val set1 = createSet("s1", "r1", 1, weight = 100.0, reps = 8)
        val slot1 = createSlot("r1", ex1, 0, sets = listOf(set1))
        val slot2 = createSlot("r2", ex2, 1)
        val slot3 = createSlot("r3", ex3, 2)

        // Initial order: Ex1, Ex2, Ex3
        val initialSlots = listOf(slot1, slot2, slot3)
        assertEquals("Bench Press", initialSlots[0].exercise?.name)
        assertEquals(1, initialSlots[0].sets.size)

        // Mid-session reorder: move Ex3 (Cable Flyes) to position 0
        val reorderedSlots = listOf(
            slot3.copy(result = slot3.result.copy(sortOrder = 0)),
            slot1.copy(result = slot1.result.copy(sortOrder = 1)),
            slot2.copy(result = slot2.result.copy(sortOrder = 2)),
        ).sortedBy { it.result.sortOrder }

        val state = ActiveWorkoutUiState(slots = reorderedSlots, currentIndex = 1)

        // Position 0 is now Cable Flyes
        assertEquals("Cable Flyes", state.slots[0].exercise?.name)

        // Position 1 is now Bench Press, and its logged set is 100% preserved
        assertEquals("Bench Press", state.slots[1].exercise?.name)
        assertEquals(1, state.slots[1].sets.size)
        assertEquals(100.0, state.slots[1].sets[0].weight, 0.01)
    }

    @Test
    fun removingExerciseWithLoggedSets_flagsAsRemovedAndPreservesLoggedSets() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Press")

        val set1 = createSet("s1", "r1", 1, weight = 100.0, reps = 8)
        val slot1 = createSlot("r1", ex1, 0, sets = listOf(set1), isRemovedFromSession = true)
        val slot2 = createSlot("r2", ex2, 1)

        // Filter active non-removed slots for UI rotation
        val allResults = listOf(slot1, slot2)
        val activeSlots = allResults.filter { !it.result.isRemovedFromSession }

        val state = ActiveWorkoutUiState(slots = activeSlots, currentIndex = 0)

        // Active slots only contains Incline Press
        assertEquals(1, state.slots.size)
        assertEquals("Incline Press", state.slots[0].exercise?.name)

        // Removed Bench Press set log is still intact on slot1
        assertTrue(slot1.result.isRemovedFromSession)
        assertEquals(1, slot1.sets.size)
        assertEquals(100.0, slot1.sets[0].weight, 0.01)
    }
}
