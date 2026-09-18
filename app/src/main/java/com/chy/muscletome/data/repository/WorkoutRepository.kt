package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.SelectionReason
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
) {
    fun observeSession(id: String) = workoutDao.observeSession(id)
    fun observeSlotResults(sessionId: String) = workoutDao.observeSlotResults(sessionId)
    fun observeSets(sessionId: String) = workoutDao.observeSetsForSession(sessionId)

    suspend fun lastSetForExercise(exerciseId: String): SetLogEntity? =
        workoutDao.lastSetForExercise(exerciseId)

    suspend fun startSession(dayId: String): String {
        val slots = routineDao.getSlots(dayId)
        require(slots.isNotEmpty()) { "Day has no exercises" }
        require(slots.all { it.exerciseId != null }) { "Every slot needs an exercise" }

        val sessionId = UUID.randomUUID().toString()
        workoutDao.insertSession(
            WorkoutSessionEntity(
                id = sessionId,
                userId = SeedCatalog.LOCAL_USER_ID,
                routineDayId = dayId,
                startedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        slots.forEach { slot ->
            workoutDao.insertSlotResult(
                SessionSlotResultEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    routineSlotId = slot.id,
                    resolvedExerciseId = checkNotNull(slot.exerciseId),
                    selectionReason = SelectionReason.FIXED,
                ),
            )
        }
        return sessionId
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
        workoutDao.endSession(sessionId, System.currentTimeMillis())
    }
}