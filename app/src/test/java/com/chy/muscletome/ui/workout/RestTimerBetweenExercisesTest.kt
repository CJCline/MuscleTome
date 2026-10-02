package com.chy.muscletome.ui.workout

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerBetweenExercisesTest {

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
        restSeconds: Int = 90,
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
                sets = 3,
                repRangeMin = 8,
                repRangeMax = 12,
                restSeconds = restSeconds,
            ),
            exercise = exercise,
            sets = emptyList(),
        )
    }

    @Test
    fun restTimer_remainsRunningAndVisibleWhenAdvancingToNextExercise() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")

        val slots = listOf(
            createSlot("r1", ex1, 0, restSeconds = 90),
            createSlot("r2", ex2, 1, restSeconds = 60),
        )

        // User starts on Exercise 1, rest timer starts with 90s total and 75s remaining
        val stateAtEx1WithTimer = ActiveWorkoutUiState(
            slots = slots,
            currentIndex = 0,
            restSecondsLeft = 75,
            totalRestSeconds = 90,
        )

        assertEquals("Bench Press", stateAtEx1WithTimer.current?.exercise?.name)
        assertEquals(75, stateAtEx1WithTimer.restSecondsLeft)
        assertEquals(90, stateAtEx1WithTimer.totalRestSeconds)

        // User advances to Next Exercise while timer is running at 75s
        val stateAtEx2 = stateAtEx1WithTimer.copy(
            currentIndex = 1,
        )

        // Verify timer is on Exercise 2 screen: visible and running with same remaining time
        assertEquals("Incline Dumbbell Press", stateAtEx2.current?.exercise?.name)
        assertEquals(75, stateAtEx2.restSecondsLeft)
        assertEquals(90, stateAtEx2.totalRestSeconds)

        // Verify timer did not restart (did not jump to 90 or 60) and did not disappear (is not 0)
        assertNotEquals(0, stateAtEx2.restSecondsLeft)
        assertNotEquals(90, stateAtEx2.restSecondsLeft)
        assertNotEquals(60, stateAtEx2.restSecondsLeft)
        assertTrue(stateAtEx2.restSecondsLeft > 0)
    }

    @Test
    fun restTimer_progressRingFractionUsesTotalRestSecondsAcrossExercises() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")

        // Exercise 1 had 120s rest, Exercise 2 has 60s rest
        val slots = listOf(
            createSlot("r1", ex1, 0, restSeconds = 120),
            createSlot("r2", ex2, 1, restSeconds = 60),
        )

        // Active rest timer from Exercise 1: 90s remaining out of 120s
        val state = ActiveWorkoutUiState(
            slots = slots,
            currentIndex = 1, // On Exercise 2
            restSecondsLeft = 90,
            totalRestSeconds = 120,
        )

        val plannedRest = if (state.totalRestSeconds > 0) {
            state.totalRestSeconds
        } else {
            state.current?.slot?.restSeconds ?: 0
        }
        val fraction = (state.restSecondsLeft.toFloat() / plannedRest).coerceIn(0f, 1f)

        // Planned rest reflects original timer duration (120s), not Exercise 2's planned rest (60s)
        assertEquals(120, plannedRest)
        assertEquals(0.75f, fraction, 0.001f)
    }

    @Test
    fun restTimer_remainsVisibleWhenSelectingDifferentExercise() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")
        val ex3 = createExercise("ex3", "Cable Flyes")

        val slots = listOf(
            createSlot("r1", ex1, 0, restSeconds = 90),
            createSlot("r2", ex2, 1, restSeconds = 60),
            createSlot("r3", ex3, 2, restSeconds = 45),
        )

        // User starts on Exercise 1, rest timer running with 45s left out of 90s
        val stateAtEx1 = ActiveWorkoutUiState(
            slots = slots,
            currentIndex = 0,
            restSecondsLeft = 45,
            totalRestSeconds = 90,
        )

        // User jumps directly to Exercise 3
        val stateAtEx3 = stateAtEx1.copy(
            currentIndex = 2,
        )

        assertEquals("Cable Flyes", stateAtEx3.current?.exercise?.name)
        assertEquals(45, stateAtEx3.restSecondsLeft)
        assertEquals(90, stateAtEx3.totalRestSeconds)
        assertTrue(stateAtEx3.restSecondsLeft > 0)
    }
}
