package com.chy.muscletome.ui.workout

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextExercisePreviewTest {

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
        supersetGroupId: String? = null,
    ): ActiveSlot {
        return ActiveSlot(
            result = SessionSlotResultEntity(
                id = resultId,
                sessionId = "s1",
                routineSlotId = "slot-$resultId",
                resolvedExerciseId = exercise.id,
                selectionReason = SelectionReason.FIXED,
                supersetGroupId = supersetGroupId,
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
                restSeconds = 90,
            ),
            exercise = exercise,
            sets = emptyList(),
        )
    }

    @Test
    fun nextSlot_returnsItemAfterCurrentIndex() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")
        val ex3 = createExercise("ex3", "Cable Flyes")

        val slots = listOf(
            createSlot("r1", ex1, 0),
            createSlot("r2", ex2, 1),
            createSlot("r3", ex3, 2),
        )

        val stateIndex0 = ActiveWorkoutUiState(slots = slots, currentIndex = 0)
        assertEquals("Incline Dumbbell Press", stateIndex0.nextSlot?.exercise?.name)

        // As user advances to index 1
        val stateIndex1 = ActiveWorkoutUiState(slots = slots, currentIndex = 1)
        assertEquals("Cable Flyes", stateIndex1.nextSlot?.exercise?.name)

        // As user advances to index 2 (last exercise)
        val stateIndex2 = ActiveWorkoutUiState(slots = slots, currentIndex = 2)
        assertNull(stateIndex2.nextSlot)
    }

    @Test
    fun nextSlot_updatesAccuratelyWhenSlotsAreReordered() {
        val ex1 = createExercise("ex1", "Bench Press")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")
        val ex3 = createExercise("ex3", "Cable Flyes")

        // Initial order: Bench Press (0), Incline Press (1), Cable Flyes (2)
        val initialSlots = listOf(
            createSlot("r1", ex1, 0),
            createSlot("r2", ex2, 1),
            createSlot("r3", ex3, 2),
        )
        val stateInitial = ActiveWorkoutUiState(slots = initialSlots, currentIndex = 0)
        assertEquals("Incline Dumbbell Press", stateInitial.nextSlot?.exercise?.name)

        // User reorders slots so Cable Flyes is moved before Incline Press
        val reorderedSlots = listOf(
            createSlot("r1", ex1, 0),
            createSlot("r3", ex3, 1),
            createSlot("r2", ex2, 2),
        )
        val stateReordered = ActiveWorkoutUiState(slots = reorderedSlots, currentIndex = 0)
        assertEquals("Cable Flyes", stateReordered.nextSlot?.exercise?.name)
    }

    @Test
    fun nextSlot_updatesWhenSupersetPartnerIsSpliced() {
        val ex1 = createExercise("ex1", "Bench Press")
        val partner = createExercise("exPartner", "Pushups")
        val ex2 = createExercise("ex2", "Incline Dumbbell Press")

        // Before superset partner added
        val slotsBefore = listOf(
            createSlot("r1", ex1, 0),
            createSlot("r2", ex2, 1),
        )
        val stateBefore = ActiveWorkoutUiState(slots = slotsBefore, currentIndex = 0)
        assertEquals("Incline Dumbbell Press", stateBefore.nextSlot?.exercise?.name)

        // Ad-hoc superset partner spliced right after anchor
        val slotsAfter = listOf(
            createSlot("r1", ex1, 0, supersetGroupId = "g1"),
            createSlot("rPartner", partner, 1, supersetGroupId = "g1"),
            createSlot("r2", ex2, 2),
        )
        val stateAfter = ActiveWorkoutUiState(slots = slotsAfter, currentIndex = 0)
        assertEquals("Pushups", stateAfter.nextSlot?.exercise?.name)
    }
}
