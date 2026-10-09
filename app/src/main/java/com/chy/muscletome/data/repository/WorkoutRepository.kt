package com.chy.muscletome.data.repository

import androidx.room.withTransaction
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseSetPoint
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.SelectionHistoryDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.routine.SupersetGrouper
import com.chy.muscletome.domain.routine.TargetSlotLabel
import com.chy.muscletome.domain.selection.VarietyEngine
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.domain.selection.EngineCandidate
import com.chy.muscletome.domain.selection.ResolvedSlot
import com.chy.muscletome.domain.selection.SessionResolver
import com.chy.muscletome.domain.selection.SlotResolution
import com.chy.muscletome.domain.selection.EngineRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

sealed class StartResult {
    data class Success(val sessionId: String) : StartResult()
    data class NoMatch(val slotLabel: String) : StartResult()
}

/** Active slot information for on-the-fly workout replacements. */
data class ActiveSlotOverrideOption(
    val result: SessionSlotResultEntity,
    val currentExerciseName: String,
    val sortOrder: Int,
)

/** One upcoming exercise in a day preview (Home's "Up next" card). */
data class UpcomingExercise(
    val name: String,
    /** Target-muscle descriptor for TARGET slots; null for fixed exercises. */
    val targetLabel: String? = null,
    /** True when the exercise is auto-picked at session start (TARGET slot). */
    val isAutoPick: Boolean = false,
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val userDao: UserDao,
    private val catalogDao: CatalogDao,
    private val selectionHistoryDao: SelectionHistoryDao,
    private val varietyEngine: VarietyEngine,
    private val database: MuscleTomeDatabase,
    private val sessionResolver: SessionResolver,
) {
    fun observeSession(id: String) = workoutDao.observeSession(id)

    fun observeOpenSession() =
        workoutDao.observeOpenSession(SeedCatalog.LOCAL_USER_ID)

    suspend fun getOpenSession(): WorkoutSessionEntity? =
        workoutDao.getOpenSession(SeedCatalog.LOCAL_USER_ID)

    suspend fun updateLastActiveResultId(sessionId: String, resultId: String?) {
        workoutDao.updateLastActiveResultId(sessionId, resultId)
    }

    fun observeLastCompletedSession() =
        workoutDao.observeLastCompletedSession(SeedCatalog.LOCAL_USER_ID)

    fun observeSlotResults(sessionId: String) = workoutDao.observeSlotResults(sessionId)
    fun observeSets(sessionId: String) = workoutDao.observeSetsForSession(sessionId)

    suspend fun lastSetForExercise(exerciseId: String): SetLogEntity? =
        workoutDao.lastSetForExercise(exerciseId)

    suspend fun bestWeightForExercise(exerciseId: String): Double? =
        workoutDao.bestWeightForExercise(exerciseId)

    suspend fun sessionCountForExercise(exerciseId: String): Int =
        workoutDao.sessionCountForExercise(exerciseId)

    /** Finished sessions since [sinceEpochMs], oldest last (consistency math). */
    suspend fun getSessionsSince(userId: String, sinceEpochMs: Long): List<WorkoutSessionEntity> =
        workoutDao.getSessionsSince(userId, sinceEpochMs)

    /**
     * The first few entries of a day as preview text — fixed exercises by
     * name, TARGET slots as their muscle/movement descriptor. Order follows
     * slot order; [limit] entries are rendered, anything beyond collapses
     * into a "+N more" tail.
     */
    suspend fun dayPreview(
        dayId: String,
        limit: Int = 3,
    ): Pair<List<UpcomingExercise>, Int> {
        val slots = routineDao.getSlotsWithTargets(dayId)
        val names = catalogDao.getExercises().associate { it.id to it.name }
        val muscleNames = catalogDao.getMuscleGroups().associate { it.id to it.name }
        val entries = slots.map { row ->
            if (row.slot.exerciseId != null) {
                UpcomingExercise(name = names[row.slot.exerciseId] ?: "Unknown exercise")
            } else {
                UpcomingExercise(
                    name = TargetSlotLabel.label(
                        muscleNames = row.targetMuscleGroupIds.mapNotNull { muscleNames[it.muscleGroupId] },
                        movement = row.slot.targetMovementType,
                    ),
                    targetLabel = TargetSlotLabel.AI_PICK,
                    isAutoPick = true,
                )
            }
        }
        return entries.take(limit) to maxOf(0, entries.size - limit)
    }

    fun observeExerciseSetPoints(exerciseId: String): Flow<List<ExerciseSetPoint>> =
        workoutDao.observeExerciseSetPoints(exerciseId)

    suspend fun startSession(dayId: String): StartResult {
        val existing = workoutDao.findOpenSession(SeedCatalog.LOCAL_USER_ID, dayId)
        if (existing != null) return StartResult.Success(existing.id)

        val slots = routineDao.getSlots(dayId)
        if (slots.isEmpty()) return StartResult.NoMatch("Day has no exercises")

        val user = userDao.getUser(SeedCatalog.LOCAL_USER_ID)
        val available = userDao.getAvailableEquipmentIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val excluded = userDao.getExcludedExerciseIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val muscleGroups = catalogDao.getMuscleGroups()

        val resolved = when (
            val resolution = sessionResolver.resolve(
                slots = slots,
                targetsBySlotId = slots.associate { slot ->
                    slot.id to routineDao.getSlotTargets(slot.id)
                        .map { it.muscleGroupId }
                        .toSet()
                },
                catalog = buildCatalog(),
                muscleGroups = muscleGroups,
                availableEquipmentIds = available,
                excludedExerciseIds = excluded,
                maxDifficulty = user?.maxDifficulty ?: Difficulty.ADVANCED,
                matchStrictness = user?.primaryMatchStrictness ?: MatchStrictness.LOOSE,
                preferCompoundEarly = user?.preferCompoundEarly ?: true,
            )
        ) {
            is SlotResolution.Success -> resolution.slots
            is SlotResolution.NoMatch -> return StartResult.NoMatch(resolution.slotLabel)
        }

        // Single atomic write: the session row and all of its resolved slot
        // results land together or not at all — a crash mid-insert can no
        // longer strand a session with missing results.
        val sessionId = UUID.randomUUID().toString()
        // Snapshot semantics: superset membership and result ordering are
        // frozen at start — routine edits made mid-session never rewrite a
        // running workout. Same rule as the resolved exercise itself.
        val slotById = slots.associateBy { it.id }
        workoutDao.startSessionTransaction(
            session = WorkoutSessionEntity(
                id = sessionId,
                userId = SeedCatalog.LOCAL_USER_ID,
                routineDayId = dayId,
                startedAtEpochMs = System.currentTimeMillis(),
            ),
            slotResults = resolved.map { res ->
                val slot = slotById[res.slotId]
                SessionSlotResultEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    routineSlotId = res.slotId,
                    resolvedExerciseId = res.exerciseId,
                    selectionReason = res.reason,
                    supersetGroupId = slot?.supersetGroupId,
                    sortOrder = slot?.orderIndex ?: 0,
                )
            },
        )
        return StartResult.Success(sessionId)
    }

    suspend fun logSet(
        sessionSlotResultId: String,
        setNumber: Int,
        weight: Double,
        reps: Int,
        rpe: Float?,
        restSecondsActual: Int?,
    ) {
        workoutDao.insertSet(
            SetLogEntity(
                id = UUID.randomUUID().toString(),
                sessionSlotResultId = sessionSlotResultId,
                setNumber = setNumber,
                weight = weight,
                reps = reps,
                rpe = rpe,
                restSecondsActual = restSecondsActual,
                completedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun finishSession(sessionId: String) {
        val now = System.currentTimeMillis()
        val results = workoutDao.getSlotResults(sessionId)
        val exercises = catalogDao.getExercises().associateBy { it.id }
        val existing = selectionHistoryDao
            .getAll(SeedCatalog.LOCAL_USER_ID)
            .associateBy { it.exerciseId }

        // One read-modify-write per exercise (a session may resolve the same
        // exercise in several slots), batched so the history update lands in
        // the same transaction as the end-of-session write.
        val updated = results
            .groupBy { it.resolvedExerciseId }
            .mapNotNull { (exerciseId, group) ->
                val exercise = exercises[exerciseId] ?: return@mapNotNull null
                val current = existing[exerciseId]
                val completed = group.size
                ExerciseSelectionHistoryEntity(
                    userId = SeedCatalog.LOCAL_USER_ID,
                    exerciseId = exerciseId,
                    muscleGroupId = exercise.primaryMuscleGroupId,
                    lastUsedAtEpochMs = now,
                    useCount30d = (current?.useCount30d ?: 0) + completed,
                    useCount90d = (current?.useCount90d ?: 0) + completed,
                    completedCount = (current?.completedCount ?: 0) + completed,
                    rerollCount = current?.rerollCount ?: 0,
                    affinity = ((current?.affinity ?: 0f) + (0.15f * completed)).coerceAtMost(1f),
                )
            }

        // Atomic: the session only flips to "finished" if its history rows
        // land too — no half-finished sessions with partial history.
        database.withTransaction {
            workoutDao.endSession(sessionId, now)
            if (updated.isNotEmpty()) selectionHistoryDao.upsertAll(updated)
        }
    }

    suspend fun discardSession(sessionId: String) {
        workoutDao.deleteSession(sessionId)
    }

    /** Corrects a mis-logged set (fat-fingered weight/reps must be fixable). */
    suspend fun updateSet(set: SetLogEntity) = workoutDao.updateSet(set)

    /**
     * Deletes a mis-logged set and renumbers the slot result's remaining
     * sets so they stay gapless.
     */
    suspend fun deleteSet(set: SetLogEntity) {
        workoutDao.deleteSetById(set.id)
        workoutDao.getSetsForSlotResult(set.sessionSlotResultId)
            .forEachIndexed { index, remaining ->
                if (remaining.setNumber != index + 1) {
                    workoutDao.updateSet(remaining.copy(setNumber = index + 1))
                }
            }
    }

    /** Session-specific remark for one resolved exercise in one session. */
    suspend fun updateSessionNote(slotResultId: String, note: String) =
        workoutDao.updateSessionNote(slotResultId, note)

    suspend fun rerollSlot(result: SessionSlotResultEntity, slotIndex: Int): String? {
        val slotId = result.routineSlotId ?: return null
        val user = userDao.getUser(SeedCatalog.LOCAL_USER_ID)
        val available = userDao.getAvailableEquipmentIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val excluded = userDao.getExcludedExerciseIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val targets = routineDao.getSlotTargets(slotId).map { it.muscleGroupId }.toSet()
        if (targets.isEmpty()) return null

        val catalog = buildCatalog()
        val pick = varietyEngine.pick(
            EngineRequest(
                targetMuscleIds = targets,
                targetMovementType = TargetMovementType.ANY,
                availableEquipmentIds = available,
                excludedExerciseIds = excluded,
                alreadyPickedIds = setOf(result.resolvedExerciseId),
                maxDifficulty = user?.maxDifficulty ?: Difficulty.ADVANCED,
                matchStrictness = user?.primaryMatchStrictness ?: MatchStrictness.LOOSE,
                preferCompoundEarly = user?.preferCompoundEarly ?: true,
                slotIndex = slotIndex,
            ),
            catalog,
        ) ?: varietyEngine.pick(
            EngineRequest(
                targetMuscleIds = targets,
                targetMovementType = TargetMovementType.ANY,
                availableEquipmentIds = available,
                excludedExerciseIds = excluded,
                alreadyPickedIds = emptySet(),
                maxDifficulty = Difficulty.ADVANCED,
                matchStrictness = MatchStrictness.LOOSE,
                preferCompoundEarly = true,
                slotIndex = slotIndex,
            ),
            catalog,
        ) ?: return null

        database.withTransaction {
            recordReroll(result.resolvedExerciseId)
            workoutDao.updateSlotResult(
                result.copy(
                    resolvedExerciseId = pick.exercise.id,
                    selectionReason = SelectionReason.USER_REROLL,
                ),
            )
        }
        return pick.exercise.id
    }

    suspend fun overrideSlot(result: SessionSlotResultEntity, exerciseId: String) {
        workoutDao.updateSlotResult(
            result.copy(
                resolvedExerciseId = exerciseId,
                selectionReason = SelectionReason.USER_OVERRIDE,
            ),
        )
    }

    /**
     * Turns the anchor result into a superset with an exercise pulled from the
     * library mid-workout. The partner row is spliced in right after the
     * anchor (sortOrder = anchor + 1, shifting everything below) and both
     * rows share one group id. [plannedSets] drives the two ad-hoc modes:
     * remaining-anchor-sets for "alternate", 1 for "just this one set".
     */
    suspend fun addSupersetPartner(
        sessionId: String,
        anchor: SessionSlotResultEntity,
        exerciseId: String,
        plannedSets: Int,
    ): String {
        val groupId = anchor.supersetGroupId ?: UUID.randomUUID().toString()
        val partner = SessionSlotResultEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            routineSlotId = null,
            resolvedExerciseId = exerciseId,
            selectionReason = SelectionReason.USER_OVERRIDE,
            supersetGroupId = groupId,
            sortOrder = anchor.sortOrder + 1,
            plannedSets = plannedSets,
        )
        // Anchor group id + partner insert must land together: a crash in
        // between must not strand a grouped anchor with no partner.
        database.withTransaction {
            if (anchor.supersetGroupId == null) {
                workoutDao.updateSlotResultGroup(anchor.id, groupId)
            }
            workoutDao.insertAdHocResult(sessionId, anchor.sortOrder, partner)
        }
        return partner.id
    }

    suspend fun reorderSlotResults(sessionId: String, orderedResultIds: List<String>) {
        database.withTransaction {
            orderedResultIds.forEachIndexed { index, id ->
                workoutDao.updateSlotResultSortOrder(id, index)
            }
        }
    }

    private suspend fun buildCatalog(): List<EngineCandidate> {
        val exercises = catalogDao.getExercises()
        val equipmentLinks = catalogDao.getExerciseEquipment().groupBy { it.exerciseId }
        val secondaryLinks = catalogDao.getSecondaryMuscles().groupBy { it.exerciseId }
        val allHistory = selectionHistoryDao.getAll(SeedCatalog.LOCAL_USER_ID)
            .groupBy { it.exerciseId }

        val lastUsedMap = allHistory.mapValues { (_, rows) -> rows.maxOf { it.lastUsedAtEpochMs } }
        val affinityMap = allHistory.mapValues { (_, rows) -> rows.maxOf { it.affinity } }

        return exercises.map { exercise ->
            EngineCandidate(
                exercise = exercise,
                equipmentIds = equipmentLinks[exercise.id].orEmpty().map { it.equipmentId }.toSet(),
                secondaryMuscleIds = secondaryLinks[exercise.id].orEmpty().map { it.muscleGroupId }.toSet(),
                lastUsedAtEpochMs = lastUsedMap[exercise.id]
                    ?: workoutDao.lastSetForExercise(exercise.id)?.completedAtEpochMs,
                affinity = affinityMap[exercise.id] ?: 0f,
            )
        }
    }

    private suspend fun recordReroll(exerciseId: String) {
        val exercise = catalogDao.getExercises().find { it.id == exerciseId } ?: return
        val muscleId = exercise.primaryMuscleGroupId
        val current = selectionHistoryDao.get(SeedCatalog.LOCAL_USER_ID, exerciseId, muscleId)
        selectionHistoryDao.upsert(
            ExerciseSelectionHistoryEntity(
                userId = SeedCatalog.LOCAL_USER_ID,
                exerciseId = exerciseId,
                muscleGroupId = muscleId,
                lastUsedAtEpochMs = current?.lastUsedAtEpochMs ?: 0L,
                useCount30d = current?.useCount30d ?: 0,
                useCount90d = current?.useCount90d ?: 0,
                completedCount = current?.completedCount ?: 0,
                rerollCount = (current?.rerollCount ?: 0) + 1,
                affinity = ((current?.affinity ?: 0f) - 0.2f).coerceAtLeast(-1f),
            ),
        )
    }
}
