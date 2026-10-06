package com.chy.muscletome.domain.search

import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchEngineTest {

    private fun exercise(
        id: String,
        name: String,
        primaryMuscle: String = "traps",
        category: String = "back",
    ) = ExerciseEntity(
        id = id,
        name = name,
        category = category,
        primaryMuscleGroupId = primaryMuscle,
        movementPattern = MovementPattern.PULL,
        movementType = MovementType.ISOLATION,
        difficulty = Difficulty.BEGINNER,
        source = ExerciseSource.SEED,
    )

    @Test
    fun stemNormalizesPluralsCorrectly() {
        assertEquals("shrug", ExerciseSearchEngine.stem("shrugs"))
        assertEquals("press", ExerciseSearchEngine.stem("presses"))
        assertEquals("curl", ExerciseSearchEngine.stem("curls"))
        assertEquals("fly", ExerciseSearchEngine.stem("flies"))
        assertEquals("cable", ExerciseSearchEngine.stem("cables"))
        assertEquals("dumbbell", ExerciseSearchEngine.stem("dumbbells"))
        assertEquals("barbell", ExerciseSearchEngine.stem("barbells"))
        assertEquals("extension", ExerciseSearchEngine.stem("extensions"))
    }

    @Test
    fun matchesCableShrugsWhenExerciseIsNamedCableShrug() {
        val ex = exercise("cable_shrug", "Cable Shrug")
        assertTrue(ExerciseSearchEngine.matches(ex, "cable shrugs"))
        assertTrue(ExerciseSearchEngine.matches(ex, "shrugs cable"))
        assertTrue(ExerciseSearchEngine.matches(ex, "cable shrug"))
        assertTrue(ExerciseSearchEngine.matches(ex, "shrug cable"))
    }

    @Test
    fun matchesPluralBenchPresses() {
        val ex = exercise("barbell_bench", "Barbell Bench Press")
        assertTrue(ExerciseSearchEngine.matches(ex, "bench presses"))
        assertTrue(ExerciseSearchEngine.matches(ex, "barbell press"))
        assertTrue(ExerciseSearchEngine.matches(ex, "press bench"))
    }

    @Test
    fun matchesAcrossEquipmentAndMuscleGroups() {
        val ex = exercise("cable_shrug", "Shrug", primaryMuscle = "traps")
        assertTrue(ExerciseSearchEngine.matches(ex, "traps shrug", equipmentNames = listOf("Cable")))
        assertTrue(ExerciseSearchEngine.matches(ex, "cable shrug", equipmentNames = listOf("Cable")))
    }

    @Test
    fun filterAndRankRanksExactMatchFirst() {
        val cableShrug = exercise("cable_shrug", "Cable Shrug")
        val dumbbellShrug = exercise("db_shrug", "Dumbbell Shrug")
        val shrugRow = exercise("shrug_row", "Shrug Row Cable")

        val results = ExerciseSearchEngine.filterAndRank(
            listOf(dumbbellShrug, shrugRow, cableShrug),
            "cable shrug",
        )

        assertEquals(2, results.size)
        assertEquals("cable_shrug", results[0].id)
        assertEquals("shrug_row", results[1].id)
    }

    @Test
    fun matchesRowWithCanonicalFamilyAndSecondaryTargets() {
        val ex = exercise("cable_shrug", "Cable Shrug")
        val row = ExerciseLibraryRow(
            exercise = ex,
            metadata = CanonicalExerciseEntity(exerciseId = "cable_shrug", movementFamilyId = "shrug"),
            secondaryTargets = listOf(MuscleGroupEntity("upper_back", "Upper Back")),
        )

        assertTrue(ExerciseSearchEngine.matchesRow(row, "cable shrugs"))
        assertTrue(ExerciseSearchEngine.matchesRow(row, "upper back shrug"))
        assertFalse(ExerciseSearchEngine.matchesRow(row, "squats"))
    }
}
