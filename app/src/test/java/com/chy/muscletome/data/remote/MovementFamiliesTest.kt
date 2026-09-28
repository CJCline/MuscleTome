package com.chy.muscletome.data.remote

import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.domain.model.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementFamiliesTest {
    @Test fun mapsCuratedAliasesAndVariationsWithoutBroadFalsePositives() {
        assertEquals("squat", MovementFamilies.familyId("Bulgarian Split Squat"))
        assertEquals("squat", MovementFamilies.familyId("Single-leg Goblet Squat"))
        assertEquals("bench_press", MovementFamilies.familyId("Dumbbell Bench Press"))
        assertEquals("bench_press", MovementFamilies.familyId("Push-up"))
        assertEquals("row", MovementFamilies.familyId("Pull-Up"))
        assertEquals("row", MovementFamilies.familyId("Lat Pulldown"))
        assertEquals("deadlift", MovementFamilies.familyId("Romanian Deadlift", MovementPattern.HINGE))
        assertEquals("overhead_press", MovementFamilies.familyId("Dumbbell Shoulder Press"))
        assertEquals("curl", MovementFamilies.familyId("Cable Curl"))
        assertEquals("lunge", MovementFamilies.familyId("Reverse Lunge"))
        assertEquals("plank", MovementFamilies.familyId("Side Plank"))
        assertNull(MovementFamilies.familyId("Leg Press"))
        assertNull(MovementFamilies.familyId("Unknown standalone movement", MovementPattern.SQUAT))
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
