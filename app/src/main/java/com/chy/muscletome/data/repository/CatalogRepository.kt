package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MovementFamilies
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

    fun searchLibraryRows(query: String, muscleGroupId: String?): Flow<List<ExerciseLibraryRow>> =
        catalogDao.searchLibraryRows(escapeLike(query), muscleGroupId)

    fun observeExercise(id: String): Flow<ExerciseEntity?> = catalogDao.observeExercise(id)

    fun observeCanonicalExercise(id: String) = catalogDao.observeCanonicalExercise(id)

    fun observeEquipmentForExercise(id: String): Flow<List<EquipmentEntity>> =
        catalogDao.observeEquipmentForExercise(id)

    fun observeSecondaryMusclesForExercise(id: String): Flow<List<MuscleGroupEntity>> =
        catalogDao.observeSecondaryMusclesForExercise(id)

    fun observeNameTaken(name: String): Flow<Boolean> = catalogDao.observeNameTaken(name.trim())

    /** Persists the user's personal note for an exercise across sessions. */
    suspend fun updateExerciseNotes(exerciseId: String, notes: String) {
        catalogDao.updateExerciseNotes(exerciseId, notes)
    }

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
        instructions: List<String> = emptyList(),
        unilateral: Boolean = false,
        media: List<ExerciseMedia> = emptyList(),
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
                    unilateral = unilateral,
                    demoUri = media.firstOrNull { it.type.name == "IMAGE" }?.uri,
                ),
            ),
        )
        catalogDao.insertExerciseEquipment(
            equipmentIds.map { ExerciseEquipmentCrossRef(id, it) },
        )
        catalogDao.insertSecondaryMuscles(
            secondaryMuscleGroupIds.map { ExerciseSecondaryMuscleCrossRef(id, it) },
        )
        val familyId: String? = null
        catalogDao.upsertCanonicalMetadata(
            listOf(CanonicalExerciseEntity(
                exerciseId = id,
                primaryMuscleGroupId = primaryMuscleGroupId,
                instructions = instructions.joinToString("\n"),
                movementFamilyId = familyId,
                origin = ExerciseOrigin.USER_CREATED.name,
                isUserEdited = true,
            )),
        )
        catalogDao.upsertInstructions(instructions.mapIndexed { index, text -> ExerciseInstructionEntity(id, index, text) })
        catalogDao.upsertSecondaryTargets(secondaryMuscleGroupIds.map { ExerciseSecondaryTargetEntity(id, it) })
        catalogDao.upsertCanonicalEquipment(equipmentIds.map { ExerciseEquipmentLinkEntity(id, it) })
        catalogDao.upsertExerciseMedia(media.mapIndexed { index, row ->
            ExerciseMediaEntity(
                id = "${id}_media_${row.uri?.substringAfterLast('/') ?: index}",
                exerciseId = id,
                type = row.type.name,
                uri = row.uri.orEmpty(),
                sourceKey = row.sourceKey,
                attribution = row.attribution,
                creator = row.creator,
                licenseName = row.licenseName,
                licenseUrl = row.licenseUrl,
                sortOrder = index,
            )
        })
        return CreateExerciseResult.SUCCESS
    }

    /** Escapes SQL LIKE wildcards so user input matches literally (DAO uses ESCAPE '\\'). */
    private fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}