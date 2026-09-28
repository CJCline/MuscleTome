package com.chy.muscletome.data.remote

import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.domain.model.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementFamiliesTest {
    @Test fun mapsSeedVariationsToFamiliesWithoutChangingExerciseIds() {
        assertEquals("squat", MovementFamilies.familyId("Bulgarian Split Squat"))
        assertEquals("squat", MovementFamilies.familyId("Single-leg Goblet Squat"))
        assertEquals("bench_press", MovementFamilies.familyId("Dumbbell Bench Press"))
        assertEquals("deadlift", MovementFamilies.familyId("Romanian Deadlift", MovementPattern.HINGE))
        assertNull(MovementFamilies.familyId("Reverse Pec Deck"))
        assertTrue(SeedCatalog.familyIdsByExerciseId.keys.all { id -> SeedCatalog.exercises.any { it.id == id } })
        assertTrue(SeedCatalog.familyIdsByExerciseId["squat"] == "squat")
    }

    @Test fun wgerNormalizedExerciseGetsFamilyWhenNameIsKnown() {
        val normalized = WgerImportAdapter.normalize(
            WgerExerciseInfo(
                id = 800,
                categoryName = "Legs",
                muscles = emptyList(),
                musclesSecondary = emptyList(),
                equipment = emptyList(),
                licenseAuthor = null,
                licenseName = null,
                englishName = "Bulgarian Split Squat",
                englishDescription = null,
                hasEnglish = true,
                mainImageUrl = null,
            ),
        )!!
        assertEquals("squat", normalized.exercise.movementFamilyId)
        assertEquals("wger_800", normalized.exercise.id)
    }
}
