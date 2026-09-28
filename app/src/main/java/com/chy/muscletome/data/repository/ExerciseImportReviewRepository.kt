package com.chy.muscletome.data.repository

import androidx.room.withTransaction
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseImportReviewDao
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.ExerciseImportResolutionEntity
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.ExerciseMatchCandidate
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.ImportDiagnostic
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import javax.inject.Singleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

@Singleton
class ExerciseImportReviewRepository @Inject constructor(
    private val database: MuscleTomeDatabase,
    private val reviewDao: ExerciseImportReviewDao,
    private val catalogDao: CatalogDao,
    private val canonicalRepository: CanonicalExerciseRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    fun observePending(): Flow<List<PendingExerciseImportEntity>> = reviewDao.observePending()

    suspend fun enqueue(item: NormalizedExerciseImport, candidates: List<ExerciseMatchCandidate>): String {
        val id = UUID.randomUUID().toString()
        reviewDao.insertPending(PendingExerciseImportEntity(
            id = id,
            sourceKey = item.sourceIdentity?.sourceKey,
            externalExerciseId = item.sourceIdentity?.externalExerciseId,
            displayName = item.exercise.displayName,
            pattern = item.exercise.movementPattern?.name,
            muscleIdsJson = json.encodeToString(item.exercise.targetMuscleIds().toList()),
            equipmentIdsJson = json.encodeToString(item.exercise.equipmentIds.toList()),
            payloadJson = json.encodeToString(item),
            candidateExerciseIdsJson = json.encodeToString(candidates.map { it.canonicalExerciseId }),
            createdAtEpochMs = System.currentTimeMillis(),
        ))
        return id
    }

    suspend fun keepBoth(pendingId: String): CanonicalImportResult {
        val pending = requireNotNull(reviewDao.getPending(pendingId))
        val item = json.decodeFromString<NormalizedExerciseImport>(pending.payloadJson)
        val result = canonicalRepository.createReviewed(item.copy(exercise = item.exercise.copy(
            id = "${item.exercise.id}_${pending.id.take(8)}",
        )))
        database.withTransaction {
            recordAndDelete(pending, "KEEP_BOTH", (result as? CanonicalImportResult.Created)?.exerciseId)
        }
        return result
    }

    suspend fun mergeIntoExisting(pendingId: String, existingId: String): CanonicalImportResult {
        val pending = requireNotNull(reviewDao.getPending(pendingId))
        val incoming = json.decodeFromString<NormalizedExerciseImport>(pending.payloadJson)
        val existing = requireNotNull(canonicalRepository.get(existingId))
        val merged = merge(existing, incoming)
        val result = canonicalRepository.update(merged, existingId)
        database.withTransaction { recordAndDelete(pending, "MERGE", existingId) }
        return result
    }

    suspend fun resolutions(): List<ExerciseImportResolutionEntity> = reviewDao.getResolutions()

    suspend fun discardIncoming(pendingId: String) {
        val pending = requireNotNull(reviewDao.getPending(pendingId))
        database.withTransaction { recordAndDelete(pending, "DISCARD", null) }
    }

    private suspend fun recordAndDelete(pending: PendingExerciseImportEntity, action: String, resolvedId: String?) {
        reviewDao.recordResolution(ExerciseImportResolutionEntity(
            pending.id, action, resolvedId, System.currentTimeMillis(),
        ))
        reviewDao.deletePending(pending.id)
    }

    private fun merge(existing: ExerciseWithCanonicalRelations,
                      incoming: NormalizedExerciseImport): NormalizedExerciseImport {
        val oldMedia = existing.media.map { row -> ExerciseMedia(
            type = runCatching { ExerciseMediaType.valueOf(row.type) }.getOrDefault(ExerciseMediaType.OTHER),
            uri = row.uri, sourceKey = row.sourceKey, attribution = row.attribution,
            creator = row.creator, licenseName = row.licenseName, licenseUrl = row.licenseUrl,
        ) }
        return incoming.copy(
            exercise = incoming.exercise.copy(id = existing.exercise.id),
            media = (oldMedia + incoming.media).distinctBy { it.sourceKey to it.uri },
            sourceIdentity = incoming.sourceIdentity ?: existing.sourceIdentities.firstOrNull()?.let {
                ExerciseSourceIdentity(it.sourceKey, it.externalExerciseId, it.sourceUrl, it.importedAtEpochMs, it.updatedAtEpochMs)
            },
        )
    }

    private fun CanonicalExercise.targetMuscleIds(): Set<String> = buildSet {
        primaryMuscleGroupId?.let(::add)
        addAll(secondaryMuscleGroupIds)
    }
}
