package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class CreateExerciseResult { SUCCESS, NAME_TAKEN }

@Singleton
class CatalogRepository @Inject constructor(
    private val catalogDao: CatalogDao,
) {
    fun observeExercises(): Flow<List<ExerciseEntity>> = catalogDao.observeExercises()
    fun observeMuscleGroups(): Flow<List<MuscleGroupEntity>> = catalogDao.observeMuscleGroups()
    fun observeEquipment(): Flow<List<EquipmentEntity>> = catalogDao.observeEquipment()

    fun searchExercises(query: String, muscleGroupId: String?): Flow<List<ExerciseEntity>> =
        catalogDao.searchExercises(escapeLike(query), muscleGroupId)

    fun observeExercise(id: String): Flow<ExerciseEntity?> = catalogDao.observeExercise(id)

    fun observeEquipmentForExercise(id: String): Flow<List<EquipmentEntity>> =
        catalogDao.observeEquipmentForExercise(id)

    fun observeSecondaryMusclesForExercise(id: String): Flow<List<MuscleGroupEntity>> =
        catalogDao.observeSecondaryMusclesForExercise(id)

    fun observeNameTaken(name: String): Flow<Boolean> = catalogDao.observeNameTaken(name.trim())

    suspend fun getExerciseByName(name: String): ExerciseEntity? =
        catalogDao.getExerciseByName(name.trim())

    suspend fun createCustomExercise(
        name: String,
        description: String,
        primaryMuscleGroupId: String,
        movementType: MovementType,
        movementPattern: MovementPattern,
        difficulty: Difficulty,
        equipmentIds: List<String>,
        secondaryMuscleGroupIds: List<String>,
    ): CreateExerciseResult {
        val trimmedName = name.trim()
        if (catalogDao.getExerciseByName(trimmedName) != null) {
            return CreateExerciseResult.NAME_TAKEN
        }
        val id = "user_${UUID.randomUUID()}"
        catalogDao.insertExercises(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = name.trim(),
                    description = description.trim(),
                    movementPattern = movementPattern,
                    movementType = movementType,
                    primaryMuscleGroupId = primaryMuscleGroupId,
                    difficulty = difficulty,
                    isCustom = true,
                    createdByUserId = SeedCatalog.LOCAL_USER_ID,
                    source = ExerciseSource.USER_CREATED,
                ),
            ),
        )
        catalogDao.insertExerciseEquipment(
            equipmentIds.map { ExerciseEquipmentCrossRef(id, it) },
        )
        catalogDao.insertSecondaryMuscles(
            secondaryMuscleGroupIds.map { ExerciseSecondaryMuscleCrossRef(id, it) },
        )
        return CreateExerciseResult.SUCCESS
    }

    /** Escapes SQL LIKE wildcards so user input matches literally (DAO uses ESCAPE '\\'). */
    private fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}