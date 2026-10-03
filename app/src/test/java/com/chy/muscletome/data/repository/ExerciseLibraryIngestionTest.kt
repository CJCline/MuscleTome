package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

@Serializable
private data class SeedExerciseJson(
    val name: String,
    val category: String,
    val muscleGroup: String,
    val isCustom: Boolean = false,
    val variations: List<String> = emptyList(),
)

class ExerciseLibraryIngestionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun seedJsonParsesParentAndChildVariationsCorrectly() {
        val sampleJson = """
            [
              {
                "name": "BARBELL BENCH PRESS",
                "category": "CHEST",
                "muscleGroup": "PECTORALIS",
                "isCustom": false,
                "variations": [
                  "CLOSE GRIP BENCH PRESS",
                  "PAUSE BENCH PRESS",
                  "INCLINE BENCH PRESS"
                ]
              }
            ]
        """.trimIndent()

        val parsed = json.decodeFromString<List<SeedExerciseJson>>(sampleJson)
        assertEquals(1, parsed.size)

        val parent = parsed.first()
        assertEquals("BARBELL BENCH PRESS", parent.name)
        assertEquals("CHEST", parent.category)
        assertEquals("PECTORALIS", parent.muscleGroup)
        assertFalse(parent.isCustom)
        assertEquals(3, parent.variations.size)

        val parentId = "seed_barbell_bench_press"
        val parentEntity = ExerciseEntity(
            id = parentId,
            name = parent.name.uppercase(Locale.US),
            category = parent.category.uppercase(Locale.US),
            muscleGroup = parent.muscleGroup.uppercase(Locale.US),
            primaryMuscleGroupId = parent.category.lowercase(Locale.US),
            movementType = MovementType.COMPOUND,
            parentExerciseId = null,
            isCustom = parent.isCustom,
            isDefault = true,
            source = ExerciseSource.SEED,
        )

        assertEquals("BARBELL BENCH PRESS", parentEntity.name)
        assertEquals("CHEST", parentEntity.category)
        assertEquals("PECTORALIS", parentEntity.muscleGroup)
        assertNull(parentEntity.parentExerciseId)
        assertFalse(parentEntity.isCustom)
        assertTrue(parentEntity.isDefault)

        val childEntity = ExerciseEntity(
            id = "seed_close_grip_bench_press",
            name = "CLOSE GRIP BENCH PRESS",
            category = "CHEST",
            muscleGroup = "PECTORALIS",
            primaryMuscleGroupId = "chest",
            movementType = MovementType.ISOLATION,
            parentExerciseId = parentId,
            isCustom = false,
            isDefault = true,
            source = ExerciseSource.SEED,
        )

        assertEquals("CLOSE GRIP BENCH PRESS", childEntity.name)
        assertEquals(parentId, childEntity.parentExerciseId)
        assertFalse(childEntity.isCustom)
        assertTrue(childEntity.isDefault)
    }

    @Test
    fun customExerciseCreationBuildsExpectedEntity() {
        val custom = ExerciseEntity(
            id = "user_custom_1",
            name = "INCLINE DUMBBELL FLY".uppercase(Locale.US),
            category = "CHEST".uppercase(Locale.US),
            muscleGroup = "PECTORALIS".uppercase(Locale.US),
            primaryMuscleGroupId = "chest",
            movementType = MovementType.ISOLATION,
            parentExerciseId = "seed_dumbbell_bench_press",
            isCustom = true,
            isDefault = false,
            source = ExerciseSource.USER_CREATED,
        )

        assertEquals("INCLINE DUMBBELL FLY", custom.name)
        assertEquals("CHEST", custom.category)
        assertEquals("PECTORALIS", custom.muscleGroup)
        assertEquals("seed_dumbbell_bench_press", custom.parentExerciseId)
        assertTrue(custom.isCustom)
        assertFalse(custom.isDefault)
    }
}
