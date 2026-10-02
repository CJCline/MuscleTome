package com.chy.muscletome.ui.workout

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSelectionTest {

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
    ): ActiveSlot {
        return ActiveSlot(
            result = SessionSlotResultEntity(
                id = resultId,
                sessionId = "s1",
                routineSlotId = "slot-$resultId",
                resolvedExerciseId = exercise.id,
                selectionReason = SelectionReason.FIXED,
                sortOrder = sortOrder,
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
    fun selectingExerciseEarly_updatesCurrentIndexAndActiveSlot() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")
        val ex3 = createExercise("ex3", "Cable Flyes")

        val slots = listOf(
            createSlot("r1", ex1, 0),
            createSlot("r2", ex2, 1),
            createSlot("r3", ex3, 2),
        )

        val initialState = ActiveWorkoutUiState(slots = slots, currentIndex = 0)
        assertEquals("Bench Press", initialState.current?.exercise?.name)
        assertEquals(0, initialState.currentIndex)

        // User jumps straight to exercise 3 (Cable Flyes)
        val selectedResultId = "r3"
        val resolvedIndex = slots.indexOfFirst { it.result.id == selectedResultId }
        val updatedState = initialState.copy(currentIndex = resolvedIndex)

        assertEquals("Cable Flyes", updatedState.current?.exercise?.name)
        assertEquals(2, updatedState.currentIndex)
    }

    @Test
    fun selectingExerciseEarly_preservesProgressAndDoesNotMarkSkippedExercisesComplete() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")
        val ex3 = createExercise("ex3", "Cable Flyes")

        // Exercise 1 has 1 set logged out of 3 planned
        val set1 = createSet("s1", "r1", 1, weight = 100.0, reps = 8)
        val slot1 = createSlot("r1", ex1, 0, plannedSets = 3, sets = listOf(set1))

        // Exercise 2 has 0 sets logged out of 3 planned
        val slot2 = createSlot("r2", ex2, 1, plannedSets = 3, sets = emptyList())

        // Exercise 3 has 0 sets logged out of 3 planned
        val slot3 = createSlot("r3", ex3, 2, plannedSets = 3, sets = emptyList())

        val slots = listOf(slot1, slot2, slot3)

        // Currently on Exercise 1 (1/3 sets done, not complete)
        val stateAtEx1 = ActiveWorkoutUiState(slots = slots, currentIndex = 0)
        assertEquals("Bench Press", stateAtEx1.current?.exercise?.name)
        assertFalse(slot1.isComplete)
        assertEquals(1, slot1.sets.size)

        // User selects Exercise 3 early (skipping rest of Ex1 and all of Ex2)
        val stateAtEx3 = ActiveWorkoutUiState(slots = slots, currentIndex = 2)

        // Verify Exercise 3 is current
        assertEquals("Cable Flyes", stateAtEx3.current?.exercise?.name)

        // Verify skipped Exercise 1 and Exercise 2 remain in slots and are NOT marked complete
        assertEquals(3, stateAtEx3.slots.size)

        val preservedEx1 = stateAtEx3.slots[0]
        assertEquals("Bench Press", preservedEx1.exercise?.name)
        assertFalse(preservedEx1.isComplete)
        assertEquals(1, preservedEx1.sets.size)
        assertEquals(100.0, preservedEx1.sets[0].weight, 0.01)

        val preservedEx2 = stateAtEx3.slots[1]
        assertEquals("Incline Dumbbell Press", preservedEx2.exercise?.name)
        assertFalse(preservedEx2.isComplete)
        assertTrue(preservedEx2.sets.isEmpty())
    }

    @Test
    fun returningToPreviouslySkippedExercise_restoresItsProgressAndCurrentSetNumber() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")

        val set1 = createSet("s1", "r1", 1, weight = 100.0, reps = 8)
        val slot1 = createSlot("r1", ex1, 0, plannedSets = 3, sets = listOf(set1))
        val slot2 = createSlot("r2", ex2, 1, plannedSets = 3, sets = emptyList())

        val slots = listOf(slot1, slot2)

        // User is currently on Exercise 2
        val stateAtEx2 = ActiveWorkoutUiState(slots = slots, currentIndex = 1)
        assertEquals("Incline Dumbbell Press", stateAtEx2.current?.exercise?.name)

        // User navigates back to Exercise 1
        val stateReturnedToEx1 = ActiveWorkoutUiState(slots = slots, currentIndex = 0)

        assertEquals("Bench Press", stateReturnedToEx1.current?.exercise?.name)
        assertEquals(0, stateReturnedToEx1.currentIndex)

        // Set 1 is present, so current set to be logged is Set 2
        assertEquals(1, stateReturnedToEx1.current?.sets?.size)
        assertEquals(2, stateReturnedToEx1.currentSetNumber)
        assertFalse(stateReturnedToEx1.isCurrentComplete)
    }

    @Test
    fun completingTargetExercise_marksOnlyTargetCompleteWhileSkippedExercisesRemainIncomplete() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")

        val slot1 = createSlot("r1", ex1, 0, plannedSets = 3, sets = emptyList())

        // Exercise 2 has 3 sets logged out of 3 planned (complete)
        val setsEx2 = listOf(
            createSet("s2_1", "r2", 1),
            createSet("s2_2", "r2", 2),
            createSet("s2_3", "r2", 3),
        )
        val slot2 = createSlot("r2", ex2, 1, plannedSets = 3, sets = setsEx2)

        val slots = listOf(slot1, slot2)
        val state = ActiveWorkoutUiState(slots = slots, currentIndex = 1)

        // Exercise 2 is complete
        assertTrue(state.slots[1].isComplete)

        // Skipped Exercise 1 is still incomplete and preserved
        assertFalse(state.slots[0].isComplete)
        assertEquals(2, state.slots.size)
    }
}
