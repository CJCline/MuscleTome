package com.chy.muscletome.ui.library

import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFamilyGroupingTest {
    @Test fun groupsVariantsByFamilyAndKeepsStandaloneRows() {
        fun row(id: String, family: String?) = ExerciseLibraryRow(
            exercise = ExerciseEntity(id = id, name = id, movementType = MovementType.COMPOUND, primaryMuscleGroupId = "chest"),
            metadata = family?.let { CanonicalExerciseEntity(id, "chest", movementFamilyId = it) },
            secondaryTargets = emptyList(),
        )
        val grouped = groupExerciseFamilies(listOf(row("barbell", "squat"), row("goblet", "squat"), row("pec_deck", null)))
        assertEquals(listOf("squat", null), grouped.map { it.familyId })
        assertEquals(listOf("barbell", "goblet"), grouped.first().rows.map { it.exercise.id })
        assertEquals(listOf("pec_deck"), grouped.last().rows.map { it.exercise.id })
    }
}
