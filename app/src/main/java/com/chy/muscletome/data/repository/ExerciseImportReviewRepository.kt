package com.chy.muscletome.data.repository

import androidx.room.withTransaction
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.ExerciseImportReviewDao
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.ExerciseImportResolutionEntity
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.ExerciseMatchCandidate
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.UUID

@Singleton
class ExerciseImportReviewRepository @Inject constructor(
    private val database: MuscleTomeDatabase,
    private val reviewDao: ExerciseImportReviewDao,
    private val canonicalRepository: CanonicalExerciseRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observePending(): Flow<List<PendingExerciseImportEntity>> = reviewDao.observePending()

    suspend fun enqueue(item: NormalizedExerciseImport, candidates: List<ExerciseMatchCandidate>): String? =
        database.withTransaction {
            val identity = item.sourceIdentity
            val existing = reviewDao.findPending(identity?.sourceKey, identity?.externalExerciseId)
            if (existing != null) return@withTransaction existing.id
            val id = identity?.let { "${it.sourceKey}:${it.externalExerciseId}" }
                ?: UUID.randomUUID().toString()
            if ((identity?.let { reviewDao.hasResolutionForIdentity(it.sourceKey, it.externalExerciseId) } == true) ||
                (reviewDao.getResolution(id) != null)
            ) return@withTransaction null

            reviewDao.insertPending(
                PendingExerciseImportEntity(
                    id = id,
                    sourceKey = identity?.sourceKey,
                    externalExerciseId = identity?.externalExerciseId,
                    displayName = item.exercise.displayName,
                    pattern = item.exercise.movementPattern?.name,
                    muscleIdsJson = json.encodeToString(item.exercise.targetMuscleIds().toList()),
                    equipmentIdsJson = json.encodeToString(item.exercise.equipmentIds.toList()),
                    payloadJson = json.encodeToString(item),
                    candidateExerciseIdsJson = json.encodeToString(candidates.map { it.canonicalExerciseId }),
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
            id
        }

    suspend fun keepBoth(pendingId: String): CanonicalImportResult = database.withTransaction {
        val pending = requireNotNull(reviewDao.getPending(pendingId))
        val item = json.decodeFromString<NormalizedExerciseImport>(pending.payloadJson)
        val result = canonicalRepository.createReviewed(
            item.copy(exercise = item.exercise.copy(id = "${item.exercise.id}_${pending.id.take(8)}")),
        )
        recordAndDelete(pending, "KEEP_BOTH", (result as? CanonicalImportResult.Created)?.exerciseId)
        result
    }

    suspend fun mergeIntoExisting(pendingId: String, existingId: String): CanonicalImportResult =
        database.withTransaction {
            val pending = requireNotNull(reviewDao.getPending(pendingId))
            val incoming = json.decodeFromString<NormalizedExerciseImport>(pending.payloadJson)
            val existing = requireNotNull(canonicalRepository.get(existingId))
            val result = canonicalRepository.update(merge(existing, incoming), existingId)
            recordAndDelete(pending, "MERGE", existingId)
            result
        }

    suspend fun resolutions(): List<ExerciseImportResolutionEntity> = reviewDao.getResolutions()

    suspend fun discardIncoming(pendingId: String) = database.withTransaction {
        val pending = requireNotNull(reviewDao.getPending(pendingId))
        recordAndDelete(pending, "DISCARD", null)
    }

    private suspend fun recordAndDelete(pending: PendingExerciseImportEntity, action: String, resolvedId: String?) {
        reviewDao.recordResolution(
            ExerciseImportResolutionEntity(
                pendingImportId = pending.id,
                action = action,
                resolvedExerciseId = resolvedId,
                resolvedAtEpochMs = System.currentTimeMillis(),
                sourceKey = pending.sourceKey,
                externalExerciseId = pending.externalExerciseId,
            ),
        )
        reviewDao.deletePending(pending.id)
    }

    private fun merge(
        existing: ExerciseWithCanonicalRelations,
        incoming: NormalizedExerciseImport,
    ): NormalizedExerciseImport {
        val oldMedia = existing.media.map { row ->
            ExerciseMedia(
                type = runCatching { ExerciseMediaType.valueOf(row.type) }
                    .getOrDefault(ExerciseMediaType.OTHER),
                uri = row.uri,
                sourceKey = row.sourceKey,
                attribution = row.attribution,
                creator = row.creator,
                licenseName = row.licenseName,
                licenseUrl = row.licenseUrl,
            )
        }
        val canonical = CanonicalExercise(
            id = existing.exercise.id,
            displayName = existing.exercise.name,
            description = existing.exercise.description.takeIf(String::isNotBlank),
            instructions = existing.instructions.asSequence().sortedBy { it.sortOrder }.map { it.instruction }.toList(),
            movementPattern = existing.exercise.movementPattern,
            movementType = existing.exercise.movementType,
            difficulty = existing.exercise.difficulty,
            unilateral = existing.exercise.unilateral,
            primaryMuscleGroupId = existing.metadata?.primaryMuscleGroupId
                ?: existing.exercise.primaryMuscleGroupId,
            secondaryMuscleGroupIds = existing.secondaryTargets.asSequence().map { it.id }.toSet(),
            equipmentIds = existing.equipment.asSequence().map { it.id }.toSet(),
            movementFamilyId = existing.metadata?.movementFamilyId,
            origin = existing.metadata?.origin?.let { runCatching { ExerciseOrigin.valueOf(it) }.getOrNull() }
                ?: if (existing.exercise.isCustom) ExerciseOrigin.USER_CREATED else ExerciseOrigin.IMPORTED,
        )
        return NormalizedExerciseImport(
            exercise = canonical,
            sourceIdentity = incoming.sourceIdentity,
            sourceAttribution = existing.exercise.notes.takeIf(String::isNotBlank),
            media = (oldMedia + incoming.media).distinctBy { media ->
                media.sourceKey.orEmpty() to media.uri.orEmpty()
            },
        )
    }

    private fun CanonicalExercise.targetMuscleIds(): Set<String> = buildSet {
        primaryMuscleGroupId?.let(::add)
        addAll(secondaryMuscleGroupIds)
    }
}
