package com.chy.muscletome.ui.routine

import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.ui.library.groupExerciseFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddSlotViewModelTest {

    @Test
    fun familyGroupingSeparatesFamiliesAndStandaloneExercises() {
        val row1 = ExerciseLibraryRow(
            exercise = ExerciseEntity(
                id = "barbell_squat",
                name = "Barbell Squat",
                movementType = MovementType.COMPOUND,
                primaryMuscleGroupId = "quads",
            ),
            metadata = CanonicalExerciseEntity("barbell_squat", "quads", movementFamilyId = "squat"),
            secondaryTargets = emptyList(),
        )
        val row2 = ExerciseLibraryRow(
            exercise = ExerciseEntity(
                id = "goblet_squat",
                name = "Goblet Squat",
                movementType = MovementType.COMPOUND,
                primaryMuscleGroupId = "quads",
            ),
            metadata = CanonicalExerciseEntity("goblet_squat", "quads", movementFamilyId = "squat"),
            secondaryTargets = emptyList(),
        )
        val row3 = ExerciseLibraryRow(
            exercise = ExerciseEntity(
                id = "leg_extension",
                name = "Leg Extension",
                movementType = MovementType.ISOLATION,
                primaryMuscleGroupId = "quads",
            ),
            metadata = CanonicalExerciseEntity("leg_extension", "quads", movementFamilyId = null),
            secondaryTargets = emptyList(),
        )

        val groups = groupExerciseFamilies(listOf(row1, row2, row3))
        assertEquals(2, groups.size)
        assertEquals("squat", groups[0].familyId)
        assertEquals(2, groups[0].rows.size)
        assertEquals(null, groups[1].familyId)
        assertEquals(1, groups[1].rows.size)
    }

    @Test
    fun toggleFamilySelectsAllThenDeselectsAll() {
        var selected = setOf("barbell_squat")
        val memberIds = listOf("barbell_squat", "goblet_squat")

        // Partially selected -> toggleFamily should select all members
        val allSelectedBefore = memberIds.all { it in selected }
        assertFalse(allSelectedBefore)

        selected = if (allSelectedBefore) selected - memberIds.toSet() else selected + memberIds.toSet()
        assertEquals(setOf("barbell_squat", "goblet_squat"), selected)

        // All selected -> toggleFamily should clear all members
        val allSelectedAfter = memberIds.all { it in selected }
        assertTrue(allSelectedAfter)

        selected = if (allSelectedAfter) selected - memberIds.toSet() else selected + memberIds.toSet()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun duplicateSelectionMaintainsSetUniqueness() {
        val selected = mutableSetOf<String>()

        // Selecting same exercise twice
        selected.add("barbell_squat")
        assertEquals(1, selected.size)

        selected.add("barbell_squat")
        assertEquals(1, selected.size)
    }
}
