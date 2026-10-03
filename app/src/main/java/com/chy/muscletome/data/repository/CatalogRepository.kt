package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.WorkoutDao
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

/** [createCustomExerciseId] outcome: the new exercise id, or the failure reason. */
sealed interface CreateCustomExerciseResult {
    data class Success(val exerciseId: String) : CreateCustomExerciseResult
    data class Failed(val reason: CreateExerciseResult) : CreateCustomExerciseResult
}

@Singleton
class CatalogRepository @Inject constructor(
    private val catalogDao: CatalogDao,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val familyRepository: FamilyRepository,
) {
    fun observeExercises(): Flow<List<ExerciseEntity>> = catalogDao.observeExercises()
    fun observeAllExercises(query: String): Flow<List<ExerciseEntity>> = catalogDao.observeAllExercises(escapeLike(query))
    fun observeParentExercises(): Flow<List<ExerciseEntity>> = catalogDao.observeParentExercises()
    suspend fun getParentExercises(): List<ExerciseEntity> = catalogDao.getParentExercises()
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

    /** True when an exercise is safe to hard-delete (no routine slots or logged sessions). */
    suspend fun isReferenced(exerciseId: String): Boolean =
        exerciseId in routineDao.getFixedSlotExerciseIds() ||
            exerciseId in workoutDao.getResolvedExerciseIds()

    /** Persists the user's personal note for an exercise across sessions. */
    suspend fun updateExerciseNotes(exerciseId: String, notes: String) {
        catalogDao.updateExerciseNotes(exerciseId, notes)
    }

    suspend fun getExerciseByName(name: String): ExerciseEntity? =
        catalogDao.getExerciseByName(name.trim())

    suspend fun getCanonicalExercise(id: String): ExerciseWithCanonicalRelations? =
        catalogDao.getCanonicalExercise(id)

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
        movementFamilyId: String? = null,
        media: List<ExerciseMedia> = emptyList(),
        category: String = "",
        muscleGroup: String = "",
        parentExerciseId: String? = null,
    ): CreateExerciseResult =
        when (createCustomExerciseId(
            name, description, primaryMuscleGroupId, movementType, movementPattern,
            difficulty, equipmentIds, secondaryMuscleGroupIds, instructions,
            unilateral, movementFamilyId, media, category, muscleGroup, parentExerciseId,
        )) {
            is CreateCustomExerciseResult.Success -> CreateExerciseResult.SUCCESS
            is CreateCustomExerciseResult.Failed -> CreateExerciseResult.NAME_TAKEN
        }

    /**
     * Same write path as [createCustomExercise] but returns the new exercise
     * id — lets callers (e.g. exercise picking during routine setup) navigate
     * straight to what was just created.
     */
    suspend fun createCustomExerciseId(
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
        movementFamilyId: String? = null,
        media: List<ExerciseMedia> = emptyList(),
        category: String = "",
        muscleGroup: String = "",
        parentExerciseId: String? = null,
    ): CreateCustomExerciseResult {
        val trimmedName = name.trim().uppercase(java.util.Locale.US)
        if (catalogDao.getExerciseByName(trimmedName) != null) {
            return CreateCustomExerciseResult.Failed(CreateExerciseResult.NAME_TAKEN)
        }
        val id = "user_${UUID.randomUUID()}"
        val computedCategory = category.ifBlank { primaryMuscleGroupId }.uppercase(java.util.Locale.US)
        val computedMuscleGroup = muscleGroup.ifBlank { primaryMuscleGroupId }.uppercase(java.util.Locale.US)
        catalogDao.insertExercises(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = trimmedName,
                    category = computedCategory,
                    muscleGroup = computedMuscleGroup,
                    description = description.trim(),
                    movementPattern = movementPattern,
                    movementType = movementType,
                    primaryMuscleGroupId = primaryMuscleGroupId,
                    difficulty = difficulty,
                    parentExerciseId = parentExerciseId,
                    isCustom = true,
                    isDefault = false,
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
        catalogDao.upsertCanonicalMetadata(
            listOf(CanonicalExerciseEntity(
                exerciseId = id,
                primaryMuscleGroupId = primaryMuscleGroupId,
                instructions = instructions.joinToString("\n"),
                movementFamilyId = movementFamilyId?.takeIf { catalogDao.getMovementFamily(it) != null },
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
        return CreateCustomExerciseResult.Success(id)
    }

    /**
     * Phase 5B: edits a user-created exercise in place. The canonical ID and
     * any `(sourceKey, externalExerciseId)` provenance stay untouched; only the
     * editable fields are rewritten and `isUserEdited` is forced on so later
     * imports never overwrite the user's values.
     */
    suspend fun updateCustomExercise(
        exerciseId: String,
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
        movementFamilyId: String? = null,
        media: List<ExerciseMedia> = emptyList(),
    ): CreateExerciseResult {
        val existing = catalogDao.getExercise(exerciseId) ?: return CreateExerciseResult.NAME_TAKEN
        val trimmedName = name.trim()
        val clash = catalogDao.getExerciseByName(trimmedName)
        if (clash != null && clash.id != exerciseId) return CreateExerciseResult.NAME_TAKEN

        catalogDao.upsertExercises(
            listOf(
                existing.copy(
                    name = trimmedName,
                    description = description.trim(),
                    movementPattern = movementPattern,
                    movementType = movementType,
                    primaryMuscleGroupId = primaryMuscleGroupId,
                    difficulty = difficulty,
                    unilateral = unilateral,
                    demoUri = media.firstOrNull { it.type.name == "IMAGE" }?.uri ?: existing.demoUri,
                ),
            ),
        )

        val existingMetadata = catalogDao.getCanonicalExercise(exerciseId)?.metadata
            ?: CanonicalExerciseEntity(
                exerciseId = exerciseId,
                primaryMuscleGroupId = primaryMuscleGroupId,
                instructions = instructions.joinToString("\n"),
                origin = ExerciseOrigin.USER_CREATED.name,
                isUserEdited = true,
            )
        val targetFamily = movementFamilyId?.takeIf { catalogDao.getMovementFamily(it) != null }
        catalogDao.upsertCanonicalMetadata(
            listOf(
                existingMetadata.copy(
                    primaryMuscleGroupId = primaryMuscleGroupId,
                    instructions = instructions.joinToString("\n"),
                    movementFamilyId = targetFamily,
                    origin = ExerciseOrigin.USER_CREATED.name,
                    isUserEdited = true,
                ),
            ),
        )
        familyRepository.assignFamily(exerciseId, targetFamily)

        // Rewrite editable relation tables in place (same shape as create).
        catalogDao.deleteLegacyEquipment(exerciseId)
        catalogDao.deleteLegacySecondaryTargets(exerciseId)
        catalogDao.deleteInstructions(exerciseId)
        catalogDao.deleteCanonicalEquipment(exerciseId)
        catalogDao.deleteSecondaryTargets(exerciseId)
        catalogDao.deleteExerciseMedia(exerciseId)
        catalogDao.insertExerciseEquipment(equipmentIds.map { ExerciseEquipmentCrossRef(exerciseId, it) })
        catalogDao.insertSecondaryMuscles(secondaryMuscleGroupIds.map { ExerciseSecondaryMuscleCrossRef(exerciseId, it) })
        catalogDao.upsertInstructions(instructions.mapIndexed { index, text -> ExerciseInstructionEntity(exerciseId, index, text) })
        catalogDao.upsertSecondaryTargets(secondaryMuscleGroupIds.map { ExerciseSecondaryTargetEntity(exerciseId, it) })
        catalogDao.upsertCanonicalEquipment(equipmentIds.map { ExerciseEquipmentLinkEntity(exerciseId, it) })
        catalogDao.upsertExerciseMedia(media.mapIndexed { index, row ->
            ExerciseMediaEntity(
                id = "${exerciseId}_media_${row.uri?.substringAfterLast('/') ?: index}",
                exerciseId = exerciseId,
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

    /**
     * Phase 5B delete policy (documented decision): hard-delete is blocked when
     * the exercise is referenced by routine slots or logged sessions, so no
     * reference can be orphaned. Callers must check [isReferenced] first.
     */
    suspend fun deleteCustomExercise(exerciseId: String): Boolean {
        if (isReferenced(exerciseId)) return false
        catalogDao.deleteInstructions(exerciseId)
        catalogDao.deleteExerciseMedia(exerciseId)
        catalogDao.deleteSourceIdentities(exerciseId)
        catalogDao.deleteSecondaryTargets(exerciseId)
        catalogDao.deleteCanonicalEquipment(exerciseId)
        catalogDao.deleteLegacyEquipment(exerciseId)
        catalogDao.deleteLegacySecondaryTargets(exerciseId)
        // exercises row CASCADE clears canonical metadata, media cache, x-refs
        catalogDao.deleteExercise(exerciseId)
        return true
    }

    /** Escapes SQL LIKE wildcards so user input matches literally (DAO uses ESCAPE '\\'). */
    private fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}