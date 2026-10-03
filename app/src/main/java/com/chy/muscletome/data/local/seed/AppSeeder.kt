package com.chy.muscletome.data.local.seed

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SeedExerciseJson(
    val name: String,
    val category: String,
    val muscleGroup: String,
    val isCustom: Boolean = false,
    val variations: List<String> = emptyList(),
)

@Singleton
class AppSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userDao: UserDao,
    private val catalogDao: CatalogDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy {
        context.getSharedPreferences("exercise_seeder", Context.MODE_PRIVATE)
    }

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        val isSeeded = prefs.getBoolean(KEY_IS_DB_SEEDED, false)
        val hasExercises = catalogDao.exerciseCount() > 0

        if (userDao.count() == 0) {
            userDao.insert(UserEntity(id = SeedCatalog.LOCAL_USER_ID, name = "You"))
            userDao.insertAvailableEquipment(
                SeedCatalog.equipment.map {
                    UserAvailableEquipmentCrossRef(SeedCatalog.LOCAL_USER_ID, it.id)
                },
            )
        }

        if (catalogDao.muscleCount() == 0) {
            catalogDao.insertMuscleGroups(SeedCatalog.muscleGroups)
        }

        catalogDao.insertEquipment(SeedCatalog.equipment)

        if (!isSeeded || !hasExercises) {
            val seedEntities = readAndParseSeedJson()
            if (seedEntities.isNotEmpty()) {
                catalogDao.insertExercises(seedEntities)
                catalogDao.upsertCanonicalMetadata(
                    seedEntities.map { exercise ->
                        CanonicalExerciseEntity(
                            exerciseId = exercise.id,
                            primaryMuscleGroupId = exercise.primaryMuscleGroupId,
                            movementFamilyId = SeedCatalog.familyIdsByExerciseId[exercise.id],
                            origin = "BUILT_IN",
                        )
                    },
                )
                prefs.edit { putBoolean(KEY_IS_DB_SEEDED, true) }
                Log.d(TAG, "Seeded ${seedEntities.size} exercises from exercises.json")
            } else {
                catalogDao.insertExercises(SeedCatalog.exercises)
                catalogDao.insertExerciseEquipment(SeedCatalog.exerciseEquipment)
                catalogDao.insertSecondaryMuscles(SeedCatalog.secondaryMuscles)
                catalogDao.upsertCanonicalMetadata(
                    SeedCatalog.exercises.map { exercise ->
                        CanonicalExerciseEntity(
                            exerciseId = exercise.id,
                            primaryMuscleGroupId = exercise.primaryMuscleGroupId,
                            movementFamilyId = SeedCatalog.familyIdsByExerciseId[exercise.id],
                            origin = "BUILT_IN",
                        )
                    },
                )
                prefs.edit { putBoolean(KEY_IS_DB_SEEDED, true) }
                Log.d(TAG, "Seeded static catalog fallback")
            }
        }
    }

    private fun readAndParseSeedJson(): List<ExerciseEntity> = try {
        val content = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString<List<SeedExerciseJson>>(content)
        val result = mutableListOf<ExerciseEntity>()

        parsed.forEach { item ->
            val canonicalName = item.name.uppercase(Locale.US).trim()
            val category = item.category.uppercase(Locale.US).trim()
            val muscleGroup = item.muscleGroup.uppercase(Locale.US).trim()
            val primaryMuscleId = category.lowercase(Locale.US)
            val parentId = "seed_${slugify(canonicalName)}"

            val parentEntity = ExerciseEntity(
                id = parentId,
                name = canonicalName,
                category = category,
                muscleGroup = muscleGroup,
                description = "",
                movementPattern = inferMovementPattern(category),
                movementType = MovementType.COMPOUND,
                primaryMuscleGroupId = primaryMuscleId,
                difficulty = Difficulty.INTERMEDIATE,
                parentExerciseId = null,
                isCustom = item.isCustom,
                isDefault = true,
                source = ExerciseSource.SEED,
            )
            result.add(parentEntity)

            item.variations.forEach { variationName ->
                val canonicalVarName = variationName.uppercase(Locale.US).trim()
                val childId = "seed_${slugify(canonicalVarName)}"
                val childEntity = ExerciseEntity(
                    id = childId,
                    name = canonicalVarName,
                    category = category,
                    muscleGroup = muscleGroup,
                    description = "",
                    movementPattern = inferMovementPattern(category),
                    movementType = MovementType.ISOLATION,
                    primaryMuscleGroupId = primaryMuscleId,
                    difficulty = Difficulty.INTERMEDIATE,
                    parentExerciseId = parentId,
                    isCustom = item.isCustom,
                    isDefault = true,
                    source = ExerciseSource.SEED,
                )
                result.add(childEntity)
            }
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read exercises.json from assets", e)
        emptyList()
    }

    private fun slugify(input: String): String {
        return input.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun inferMovementPattern(category: String): MovementPattern {
        return when (category) {
            "CHEST", "SHOULDERS" -> MovementPattern.PUSH
            "BACK" -> MovementPattern.PULL
            "LEGS" -> MovementPattern.SQUAT
            else -> MovementPattern.OTHER
        }
    }

    private companion object {
        const val TAG = "AppSeeder"
        const val KEY_IS_DB_SEEDED = "is_db_seeded"
    }
}
