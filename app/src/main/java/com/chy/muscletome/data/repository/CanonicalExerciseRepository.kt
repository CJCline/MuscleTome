package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
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
import com.chy.muscletome.domain.model.ExerciseSources
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.ExistingCanonicalExercise
import com.chy.muscletome.domain.model.ImportDiagnostic
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import dagger.Lazy
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

/** Aggregated outcome of a batch run through the canonical import pipeline. */
data class CanonicalBatchStats(
    val created: Int = 0,
    val updated: Int = 0,
    val protected: Int = 0,
    val reviewQueued: Int = 0,
    val skippedUnusable: Int = 0,
    val failed: Int = 0,
    /** Record-level diagnostics from every processed item, keyed by exercise id/external id where known. */
    val failures: List<String> = emptyList(),
)

@Singleton
class CanonicalExerciseRepository @Inject constructor(
    private val catalogDao: CatalogDao,
    private val reviewRepositoryProvider: Lazy<ExerciseImportReviewRepository>,
) {

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
                preserveLocalEdits = (identityMatch.metadata?.isUserEdited == true) ||
                    (identityMatch.metadata?.origin == ExerciseOrigin.USER_CREATED.name),
            )
        } else {
            ExerciseImportIdentityPolicy.resolve(item, existing)
        }

        return when (decision) {
            is ExerciseImportIdentityDecision.ReviewCandidates -> {
                val queued = reviewRepositoryProvider.get().enqueue(item, decision.candidates)
                if (queued == null) {
                    CanonicalImportResult.Protected(
                        decision.candidates.first().canonicalExerciseId,
                        item.diagnostics,
                    )
                } else {
                    CanonicalImportResult.ReviewRequired(decision.candidates, item.diagnostics)
                }
            }
            is ExerciseImportIdentityDecision.ReimportExisting -> {
                val current = existingRows.firstOrNull { it.exercise.id == decision.canonicalExerciseId }
                if (current == null) {
                    persist(item, item.exercise.id, update = false)
                    CanonicalImportResult.Created(item.exercise.id, item.diagnostics)
                } else if (decision.preserveLocalEdits) {
                    // Preserve the entire local record and attach/refresh provenance only.
                    item.sourceIdentity?.let { identity ->
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

    suspend fun update(item: NormalizedExerciseImport, id: String): CanonicalImportResult {
        persist(item.copy(exercise = item.exercise.copy(id = id)), id, update = true)
        return CanonicalImportResult.Updated(id, item.diagnostics)
    }

    /**
     * Batch import through the same identity/decision/persist pipeline as
     * [import] — not a parallel persistence path. The existing-canonical
     * snapshot is loaded once and kept current as the batch progresses so a
     * large bundled catalog neither re-queries per record nor misses
     * within-batch duplicates. Per-record failures are captured and never
     * abort the batch.
     */
    suspend fun importBatch(items: List<NormalizedExerciseImport>): CanonicalBatchStats {
        var created = 0
        var updated = 0
        var protected = 0
        var reviewQueued = 0
        var failed = 0
        val failures = mutableListOf<String>()

        // Identity → canonical id, and canonical id → current projection.
        // Both are maintained as the batch writes so later records see
        // earlier ones exactly as a fresh [import] would.
        val canonicalIdByIdentity = mutableMapOf<Pair<String, String>, String>()
        val existingById = LinkedHashMap<String, ExistingCanonicalExercise>()
        catalogDao.getAllCanonicalExercises().forEach { row ->
            val projection = row.toExistingCanonical()
            existingById[row.exercise.id] = projection
            row.sourceIdentities.forEach { identity ->
                canonicalIdByIdentity[identity.sourceKey to identity.externalExerciseId] = row.exercise.id
            }
        }

        /** Registers a freshly persisted record in the snapshot. */
        fun register(item: NormalizedExerciseImport, canonicalId: String) {
            existingById[canonicalId] = ExistingCanonicalExercise(
                exercise = item.exercise.copy(id = canonicalId),
                sourceIdentities = listOfNotNull(item.sourceIdentity),
            )
            item.sourceIdentity?.let { identity ->
                canonicalIdByIdentity[identity.sourceKey to identity.externalExerciseId] = canonicalId
            }
        }

        items.forEach { item ->
            try {
                val identity = item.sourceIdentity
                val exactId = identity?.let { canonicalIdByIdentity[it.sourceKey to it.externalExerciseId] }
                val decision = if (exactId != null) {
                    val current = requireNotNull(existingById[exactId])
                    ExerciseImportIdentityDecision.ReimportExisting(
                        canonicalExerciseId = exactId,
                        preserveLocalEdits = current.isUserEdited ||
                            current.exercise.origin == ExerciseOrigin.USER_CREATED,
                    )
                } else {
                    ExerciseImportIdentityPolicy.resolve(item, existingById.values.toList())
                }
                when (decision) {
                    is ExerciseImportIdentityDecision.ReviewCandidates -> {
                        val queued = reviewRepositoryProvider.get().enqueue(item, decision.candidates)
                        if (queued == null) {
                            protected++
                        } else {
                            reviewQueued++
                        }
                    }
                    is ExerciseImportIdentityDecision.ReimportExisting -> {
                        val current = existingById[decision.canonicalExerciseId]
                        if (current == null) {
                            persist(item, item.exercise.id, update = false)
                            register(item, item.exercise.id)
                            created++
                        } else if (decision.preserveLocalEdits) {
                            item.sourceIdentity?.let { source ->
                                catalogDao.upsertSourceIdentities(
                                    listOf(source.toEntity(decision.canonicalExerciseId)),
                                )
                            }
                            protected++
                        } else {
                            persist(item, decision.canonicalExerciseId, update = true)
                            // Refresh the projection so corroborating
                            // attributes stay accurate for later records.
                            catalogDao.getCanonicalExercise(decision.canonicalExerciseId)?.let { row ->
                                existingById[decision.canonicalExerciseId] = row.toExistingCanonical()
                                row.sourceIdentities.forEach { rowIdentity ->
                                    canonicalIdByIdentity[
                                        rowIdentity.sourceKey to rowIdentity.externalExerciseId
                                    ] = decision.canonicalExerciseId
                                }
                            }
                            updated++
                        }
                    }
                    ExerciseImportIdentityDecision.CreateNew -> {
                        val newId = item.exercise.id.ifBlank { "exercise_${UUID.randomUUID()}" }
                        persist(item, newId, update = false)
                        register(item, newId)
                        created++
                    }
                }
            } catch (t: Throwable) {
                failed++
                failures += "${item.exercise.id.ifBlank { item.sourceIdentity?.externalExerciseId ?: "unknown" }}: " +
                    "${t.message ?: t.javaClass.simpleName}"
            }
        }
        return CanonicalBatchStats(
            created = created,
            updated = updated,
            protected = protected,
            reviewQueued = reviewQueued,
            skippedUnusable = 0,
            failed = failed,
            failures = failures,
        )
    }

    suspend fun createReviewed(item: NormalizedExerciseImport): CanonicalImportResult {
        val newId = item.exercise.id.ifBlank { "exercise_${UUID.randomUUID()}" }
        persist(item.copy(exercise = item.exercise.copy(id = newId)), newId, update = false)
        return CanonicalImportResult.Created(newId, item.diagnostics)
    }

    suspend fun importForReview(item: NormalizedExerciseImport): CanonicalImportResult {
        val decision = ExerciseImportIdentityPolicy.resolve(
            item,
            catalogDao.getAllCanonicalExercises().map { it.toExistingCanonical() },
        )
        if (decision is ExerciseImportIdentityDecision.ReviewCandidates) {
            val queued = reviewRepositoryProvider.get().enqueue(item, decision.candidates)
            return queued?.let {
                CanonicalImportResult.ReviewRequired(decision.candidates, item.diagnostics)
            } ?: CanonicalImportResult.Protected(
                decision.candidates.first().canonicalExerciseId,
                item.diagnostics,
            )
        }
        return import(item)
    }

    private suspend fun persist(item: NormalizedExerciseImport, id: String, update: Boolean) {
        val canonical = item.exercise
        val primaryMuscle = canonical.primaryMuscleGroupId ?: "core"
        val importMuscles = buildSet {
            add(primaryMuscle)
            addAll(canonical.secondaryMuscleGroupIds)
        }.map { id ->
            MuscleGroupEntity(id, id.replace('_', ' ').replaceFirstChar(Char::uppercase))
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
                ExerciseOrigin.IMPORTED -> ExerciseSources.fromSourceKey(
                    item.sourceIdentity?.sourceKey,
                )
                ExerciseOrigin.USER_CREATED -> ExerciseSource.USER_CREATED
            },
            // Attribution lands in notes on create; on update the existing
            // notes are user-owned and must survive provider refreshes.
            notes = if (update) {
                catalogDao.getExercise(id)?.notes ?: item.sourceAttribution.orEmpty()
            } else {
                item.sourceAttribution.orEmpty()
            },
            demoUri = item.media.firstOrNull { it.type.name == "IMAGE" }?.uri,
        )
        val metadata = CanonicalExerciseEntity(
            exerciseId = id,
            primaryMuscleGroupId = canonical.primaryMuscleGroupId,
            instructions = canonical.instructions.joinToString("\n"),
            // Family IDs belong to the taxonomy namespace, not the exercise-ID namespace.
            movementFamilyId = canonical.movementFamilyId?.takeIf { it != id },
            origin = canonical.origin.name,
            isUserEdited = canonical.origin == ExerciseOrigin.USER_CREATED,
        )
        val secondary = canonical.secondaryMuscleGroupIds.map {
            ExerciseSecondaryTargetEntity(id, it)
        }
        val equipment = canonical.equipmentIds.map { EquipmentEntity(it, it) }
        val equipmentLinks = canonical.equipmentIds.map { ExerciseEquipmentLinkEntity(id, it) }
        val instructions = canonical.instructions.mapIndexed { index, text ->
            ExerciseInstructionEntity(id, index, text)
        }
        val media = item.media.mapIndexed { index, row -> row.toEntity(id, index) }
        val source = item.sourceIdentity?.let { listOf(it.toEntity(id)) }.orEmpty()
        catalogDao.saveCanonicalExerciseBundle(
            exercise = base,
            metadata = metadata,
            instructions = instructions,
            muscleGroups = importMuscles,
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
            secondaryMuscleGroupIds = secondaryTargets.asSequence().map { it.id }.toSet(),
            equipmentIds = equipment.asSequence().map { it.id }.toSet(),
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
        id = uri?.let { value ->
            val tail = value.substringAfterLast('/').substringAfterLast(':').ifBlank { index.toString() }
            "${exerciseId}_media_$tail"
        } ?: "${exerciseId}_media_$index",
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
