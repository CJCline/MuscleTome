package com.chy.muscletome.data.remote

import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 0.4 acceptance fixtures for free-exercise-db: normal record, sparse
 * record, unmapped taxonomy values, variation pair, unusable/invalid record,
 * multi-primary record, and deterministic repeat normalization.
 */
class FreeExerciseDbImportAdapterTest {

    @Test
    fun normalRecordMapsAllCuratedFields() {
        val normalized = FreeExerciseDbImportAdapter.normalize(
            record(
                id = "Barbell_Squat",
                name = "Barbell Squat",
                force = "push",
                level = "intermediate",
                mechanic = "compound",
                equipment = "barbell",
                primaryMuscles = listOf("quadriceps"),
                secondaryMuscles = listOf("glutes", "hamstrings"),
                instructions = listOf("Descend.", "Rise."),
                images = listOf("Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"),
            ),
            importedAtEpochMs = 123L,
        ) ?: error("Valid record should be importable")

        // `force` is push, but the shared name heuristics encode the squat
        // taxonomy, which force-direction cannot express — name wins.
        assertEquals(MovementPattern.SQUAT, normalized.exercise.movementPattern)

        assertEquals("fedb_Barbell_Squat", normalized.exercise.id)
        assertEquals("Barbell Squat", normalized.exercise.displayName)
        assertEquals("quads", normalized.exercise.primaryMuscleGroupId)
        assertEquals(setOf("glutes", "hamstrings"), normalized.exercise.secondaryMuscleGroupIds)
        assertEquals(setOf("barbell"), normalized.exercise.equipmentIds)
        assertEquals(MovementType.COMPOUND, normalized.exercise.movementType)
        assertEquals(Difficulty.INTERMEDIATE, normalized.exercise.difficulty)
        assertEquals(listOf("Descend.", "Rise."), normalized.exercise.instructions)
        assertNull(normalized.exercise.unilateral)
        assertEquals("free_exercise_db", normalized.sourceIdentity?.sourceKey)
        assertEquals("Barbell_Squat", normalized.sourceIdentity?.externalExerciseId)
        assertEquals(123L, normalized.sourceIdentity?.importedAtEpochMs)
        assertEquals(2, normalized.media.size)
        assertEquals(
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Squat/0.jpg",
            normalized.media[0].uri,
        )
        assertEquals("Unlicense", normalized.media[0].licenseName)
        assertEquals("https://unlicense.org/", normalized.media[0].licenseUrl)
        assertEquals("free-exercise-db (yuhonas)", normalized.media[0].attribution)
        assertTrue(normalized.diagnostics.isEmpty())
    }

    @Test
    fun sparseRecordKeepsOptionalValuesAbsentWithoutRejecting() {
        val normalized = FreeExerciseDbImportAdapter.normalize(
            record(
                id = "Odd_Move",
                name = "Odd Move",
                force = null,
                level = null,
                mechanic = null,
                equipment = null,
                primaryMuscles = emptyList(),
                secondaryMuscles = emptyList(),
                instructions = emptyList(),
                images = emptyList(),
            ),
        ) ?: error("Named record should still be representable")

        assertEquals("fedb_Odd_Move", normalized.exercise.id)
        assertNull(normalized.exercise.primaryMuscleGroupId)
        assertTrue(normalized.exercise.secondaryMuscleGroupIds.isEmpty())
        assertTrue(normalized.exercise.equipmentIds.isEmpty())
        assertEquals(MovementType.COMPOUND, normalized.exercise.movementType)
        assertEquals(Difficulty.INTERMEDIATE, normalized.exercise.difficulty)
        assertTrue(normalized.exercise.instructions.isEmpty())
        assertTrue(normalized.media.isEmpty())
        assertTrue(normalized.diagnostics.any { it.code == "unmapped_muscle" })
        assertTrue(normalized.diagnostics.any { it.code == "unspecified_equipment" })
        assertTrue(normalized.diagnostics.any { it.code == "missing_instructions" })
    }

    @Test
    fun unmappedTaxonomyValuesEmitDiagnosticsAndStayAbsent() {
        val normalized = FreeExerciseDbImportAdapter.normalize(
            record(
                id = "Wrist_Curl",
                name = "Wrist Curl",
                equipment = "other",
                primaryMuscles = listOf("forearms"),
                secondaryMuscles = listOf("neck"),
                images = emptyList(),
            ),
        ) ?: error("Valid name should be importable")

        assertNull(normalized.exercise.primaryMuscleGroupId)
        assertTrue(normalized.exercise.secondaryMuscleGroupIds.isEmpty())
        assertTrue(normalized.exercise.equipmentIds.isEmpty())
        assertEquals(
            "forearms",
            normalized.diagnostics.single { it.code == "unmapped_muscle" && it.field == "primaryMuscles" }.sourceValue,
        )
        assertEquals(
            "neck",
            normalized.diagnostics.single { it.code == "unmapped_muscle" && it.field == "secondaryMuscles" }.sourceValue,
        )
        assertEquals(
            "other",
            normalized.diagnostics.single { it.code == "unmapped_equipment" }.sourceValue,
        )
    }

    @Test
    fun variationRecordsStayDistinctWithStableIdentities() {
        val barbell = FreeExerciseDbImportAdapter.normalize(
            record(id = "Barbell_Curl", name = "Barbell Curl", equipment = "barbell", primaryMuscles = listOf("biceps")),
        ) ?: error("Valid record should be importable")
        val dumbbell = FreeExerciseDbImportAdapter.normalize(
            record(id = "Dumbbell_Curl", name = "Dumbbell Curl", equipment = "dumbbell", primaryMuscles = listOf("biceps")),
        ) ?: error("Valid record should be importable")

        assertTrue(barbell.exercise.id != dumbbell.exercise.id)
        assertTrue(barbell.sourceIdentity != dumbbell.sourceIdentity)
        assertEquals("fedb_Barbell_Curl", barbell.exercise.id)
        assertEquals("fedb_Dumbbell_Curl", dumbbell.exercise.id)
    }

    @Test
    fun blankNameIsRejectedAsUnusableIdentity() {
        assertNull(
            FreeExerciseDbImportAdapter.normalize(
                record(id = "Blank", name = "   "),
            ),
        )
    }

    @Test
    fun multiPrimaryRecordAssignsFirstToPrimaryAndRestToSecondary() {
        val normalized = FreeExerciseDbImportAdapter.normalize(
            record(
                id = "Kettlebell_Halo_With_Overhead_Extension",
                name = "Kettlebell Halo with Overhead Extension",
                equipment = "kettlebells",
                primaryMuscles = listOf("shoulders", "triceps"),
                secondaryMuscles = emptyList(),
            ),
        ) ?: error("Valid record should be importable")

        assertEquals("shoulders", normalized.exercise.primaryMuscleGroupId)
        assertEquals(setOf("triceps"), normalized.exercise.secondaryMuscleGroupIds)
        assertEquals(setOf("kettlebell"), normalized.exercise.equipmentIds)
    }

    @Test
    fun repeatedNormalizationIsDeterministicWithStableIdentity() {
        val source = record(
            id = "Pull_Up",
            name = "Pull-up",
            force = "pull",
            level = "intermediate",
            mechanic = "compound",
            equipment = "body only",
            primaryMuscles = listOf("lats"),
            secondaryMuscles = listOf("biceps"),
            instructions = listOf("Hang.", "Pull."),
            images = listOf("Pull_Up/0.jpg"),
        )

        val first = FreeExerciseDbImportAdapter.normalize(source, importedAtEpochMs = 5L)
        val second = FreeExerciseDbImportAdapter.normalize(source.copy(instructions = listOf("Hang.", "Pull.")), importedAtEpochMs = 5L)

        assertEquals(first, second)
        assertEquals(first?.sourceIdentity, second?.sourceIdentity)
    }

    @Test
    fun staticForceAndUnknownNamesFallBackToForceOrOther() {
        val normalized = FreeExerciseDbImportAdapter.normalize(
            record(id = "Plank", name = "Plank", force = "static", primaryMuscles = listOf("abdominals")),
        ) ?: error("Valid record should be importable")

        assertEquals(MovementPattern.OTHER, normalized.exercise.movementPattern)
        assertEquals("plank", normalized.exercise.movementFamilyId)

        // Unknown name + pull force → PULL via fallback, never a guessed
        // pattern from muscles alone.
        val pullFallback = FreeExerciseDbImportAdapter.normalize(
            record(id = "Face_Pull_Thing", name = "Face Pull Thing", force = "pull", primaryMuscles = listOf("shoulders")),
        ) ?: error("Valid record should be importable")
        assertEquals(MovementPattern.PULL, pullFallback.exercise.movementPattern)
    }

    @Test
    fun ezwCurveBarEquipmentMapsToBarbell() {
        val normalized = FreeExerciseDbImportAdapter.normalize(
            record(id = "EZ_Curl", name = "EZ Bar Curl", equipment = "e-z curl bar", primaryMuscles = listOf("biceps")),
        ) ?: error("Valid record should be importable")

        assertEquals(setOf("barbell"), normalized.exercise.equipmentIds)
    }

    private fun record(
        id: String,
        name: String,
        force: String? = null,
        level: String? = null,
        mechanic: String? = null,
        equipment: String? = null,
        primaryMuscles: List<String> = emptyList(),
        secondaryMuscles: List<String> = emptyList(),
        instructions: List<String> = emptyList(),
        images: List<String> = emptyList(),
    ) = FreeExerciseDbRecord(
        id = id,
        name = name,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        instructions = instructions,
        category = "strength",
        images = images,
    )
}
