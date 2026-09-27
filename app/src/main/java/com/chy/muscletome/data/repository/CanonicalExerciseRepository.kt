package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseImportIdentityDecision
import com.chy.muscletome.domain.model.ExerciseImportIdentityPolicy
import com.chy.muscletome.domain.model.ExerciseMatchCandidate
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.ExistingCanonicalExercise
import com.chy.muscletome.domain.model.ImportDiagnostic
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CanonicalImportResult {
    data class Created(val exerciseId: String, val diagnostics: List<ImportDiagnostic>) : CanonicalImportResult
    data class Updated(val exerciseId: String, val diagnostics: List<ImportDiagnostic>) : CanonicalImportResult
    data class Protected(val exerciseId: String, val diagnostics: List<ImportDiagnostic>) : CanonicalImportResult
    data class ReviewRequired(
        val candidates: List<ExerciseMatchCandidate>,
        val diagnostics: List<ImportDiagnostic>,
    ) : CanonicalImportResult
}

@Singleton
class CanonicalExerciseRepository @Inject constructor(private val catalogDao: CatalogDao) {

    suspend fun get(id: String) = catalogDao.getCanonicalExercise(id)

    suspend fun import(item: NormalizedExerciseImport): CanonicalImportResult {
        val incomingIdentity = item.sourceIdentity
        val exact = incomingIdentity?.let {
            catalogDao.findBySourceIdentity(it.sourceKey, it.externalExerciseId)
        }
        val existingRows = catalogDao.getAllCanonicalExercises()
        val existing = existingRows.map { row -> row.toExistingCanonical() }
        val identityMatch = exact?.let { source ->
            existingRows.firstOrNull { it.exercise.id == source.exerciseId }
        }
        val decision = if (identityMatch != null) {
            ExerciseImportIdentityDecision.ReimportExisting(
                canonicalExerciseId = identityMatch.exercise.id,
                preserveLocalEdits = identityMatch.metadata?.isUserEdited == true ||
                    identityMatch.metadata?.origin == ExerciseOrigin.USER_CREATED.name,
            )
        } else {
            ExerciseImportIdentityPolicy.resolve(item, existing)
        }

        return when (decision) {
            is ExerciseImportIdentityDecision.ReviewCandidates ->
                CanonicalImportResult.ReviewRequired(decision.candidates, item.diagnostics)
            is ExerciseImportIdentityDecision.ReimportExisting -> {
                val current = existingRows.firstOrNull { it.exercise.id == decision.canonicalExerciseId }
                if (current == null) {
                    persist(item, item.exercise.id, update = false)
                    CanonicalImportResult.Created(item.exercise.id, item.diagnostics)
                } else if (decision.preserveLocalEdits) {
                    // Preserve the entire local record and attach/refresh provenance only.
                    val identity = item.sourceIdentity
                    if (identity != null) {
                        catalogDao.upsertSourceIdentities(listOf(identity.toEntity(current.exercise.id)))
                    }
                    CanonicalImportResult.Protected(current.exercise.id, item.diagnostics)
                } else {
                    persist(item, current.exercise.id, update = true)
                    CanonicalImportResult.Updated(current.exercise.id, item.diagnostics)
                }
            }
            ExerciseImportIdentityDecision.CreateNew -> {
                persist(item, item.exercise.id.ifBlank { "exercise_${UUID.randomUUID()}" }, update = false)
                CanonicalImportResult.Created(item.exercise.id, item.diagnostics)
            }
        }
    }

    private suspend fun persist(item: NormalizedExerciseImport, id: String, update: Boolean) {
        val canonical = item.exercise
        val primaryMuscle = canonical.primaryMuscleGroupId ?: "core"
        if (catalogDao.getMuscleGroups().none { it.id == primaryMuscle }) {
            catalogDao.insertMuscleGroups(listOf(MuscleGroupEntity(primaryMuscle, primaryMuscle.replace('_', ' '))))
        }
        val base = ExerciseEntity(
            id = id,
            name = canonical.displayName,
            description = canonical.description.orEmpty(),
            movementPattern = canonical.movementPattern ?: MovementPattern.OTHER,
            movementType = canonical.movementType ?: MovementType.COMPOUND,
            primaryMuscleGroupId = primaryMuscle,
            difficulty = canonical.difficulty ?: Difficulty.INTERMEDIATE,
            isCustom = canonical.origin == ExerciseOrigin.USER_CREATED,
            createdByUserId = if (canonical.origin == ExerciseOrigin.USER_CREATED) "local-user" else null,
            unilateral = canonical.unilateral ?: false,
            source = when (canonical.origin) {
                ExerciseOrigin.BUILT_IN -> ExerciseSource.SEED
                ExerciseOrigin.IMPORTED -> ExerciseSource.WGER
                ExerciseOrigin.USER_CREATED -> ExerciseSource.USER_CREATED
            },
            notes = item.sourceAttribution.orEmpty(),
            demoUri = item.media.firstOrNull { it.type.name == "IMAGE" }?.uri,
        )
        val metadata = CanonicalExerciseEntity(
            exerciseId = id,
            primaryMuscleGroupId = canonical.primaryMuscleGroupId,
            instructions = canonical.instructions.joinToString("\n"),
            movementFamilyId = canonical.movementFamilyId,
            origin = canonical.origin.name,
            isUserEdited = canonical.origin == ExerciseOrigin.USER_CREATED,
        )
        val secondary = canonical.secondaryMuscleGroupIds.map {
            ExerciseSecondaryTargetEntity(id, it)
        }
        val equipment = canonical.equipmentIds.map { EquipmentEntity(it, it) }
        val equipmentLinks = canonical.equipmentIds.map { ExerciseEquipmentCrossRef(id, it) }
        val instructions = canonical.instructions.mapIndexed { index, text ->
            ExerciseInstructionEntity(id, index, text)
        }
        val media = item.media.mapIndexed { index, row -> row.toEntity(id, index) }
        val source = item.sourceIdentity?.let { listOf(it.toEntity(id)) }.orEmpty()
        catalogDao.saveCanonicalExerciseBundle(
            exercise = base,
            metadata = metadata,
            instructions = instructions,
            equipment = equipment,
            equipmentLinks = equipmentLinks,
            secondaryTargets = secondary,
            media = media,
            sourceIdentities = source,
            update = update,
        )
    }

    private fun ExerciseWithCanonicalRelations.toExistingCanonical() = ExistingCanonicalExercise(
        exercise = CanonicalExercise(
            id = exercise.id,
            displayName = exercise.name,
            description = exercise.description.takeIf(String::isNotBlank),
            instructions = instructions.map { it.instruction },
            movementPattern = exercise.movementPattern,
            movementType = exercise.movementType,
            difficulty = exercise.difficulty,
            unilateral = exercise.unilateral,
            primaryMuscleGroupId = metadata?.primaryMuscleGroupId ?: exercise.primaryMuscleGroupId,
            secondaryMuscleGroupIds = secondaryTargets.map { it.id }.toSet(),
            equipmentIds = equipment.map { it.id }.toSet(),
            movementFamilyId = metadata?.movementFamilyId,
            origin = metadata?.origin?.let { runCatching { ExerciseOrigin.valueOf(it) }.getOrNull() }
                ?: if (exercise.isCustom) ExerciseOrigin.USER_CREATED else ExerciseOrigin.IMPORTED,
        ),
        sourceIdentities = sourceIdentities.map {
            ExerciseSourceIdentity(it.sourceKey, it.externalExerciseId, it.sourceUrl, it.importedAtEpochMs, it.updatedAtEpochMs)
        },
        isUserEdited = metadata?.isUserEdited == true,
    )

    private fun ExerciseSourceIdentity.toEntity(exerciseId: String) = ExerciseSourceIdentityEntity(
        sourceKey = sourceKey,
        externalExerciseId = externalExerciseId,
        exerciseId = exerciseId,
        sourceUrl = sourceUrl,
        importedAtEpochMs = importedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ExerciseMedia.toEntity(exerciseId: String, index: Int) = ExerciseMediaEntity(
        id = "${exerciseId}_media_$index",
        exerciseId = exerciseId,
        type = type.name,
        uri = uri.orEmpty(),
        sourceKey = sourceKey,
        attribution = attribution,
        creator = creator,
        licenseName = licenseName,
        licenseUrl = licenseUrl,
        sortOrder = index,
    )
}
