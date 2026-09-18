package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SessionSummary(
    val session: WorkoutSessionEntity,
    val exerciseNames: List<String>,
    val setCount: Int,
    val volume: Double,
)

data class ExerciseStat(
    val exercise: ExerciseEntity,
    val setCount: Int,
    val sessionCount: Int,
    val lastWeight: Double?,
    val lastReps: Int?,
    val estimated1Rm: Double?,
    val volume: Double,
)

data class MuscleVolume(
    val muscle: MuscleGroupEntity,
    val setsThisWeek: Int,
    val volumeThisWeek: Double,
)

data class StatsSnapshot(
    val sessions: List<SessionSummary> = emptyList(),
    val exercises: List<ExerciseStat> = emptyList(),
    val muscles: List<MuscleVolume> = emptyList(),
    val completedSessionCount: Int = 0,
    val totalSets: Int = 0,
    val totalVolume: Double = 0.0,
)

@Singleton
class StatsRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val catalogDao: CatalogDao,
) {
    fun observeStats(): Flow<StatsSnapshot> = combine(
        workoutDao.observeSessions(SeedCatalog.LOCAL_USER_ID),
        workoutDao.observeAllSlotResults(),
        workoutDao.observeAllSets(SeedCatalog.LOCAL_USER_ID),
        catalogDao.observeExercises(),
        catalogDao.observeMuscleGroups(),
    ) { sessions, results, sets, exercises, muscles ->
        buildSnapshot(sessions, results, sets, exercises, muscles)
    }

    private fun buildSnapshot(
        sessions: List<WorkoutSessionEntity>,
        results: List<SessionSlotResultEntity>,
        sets: List<SetLogEntity>,
        exercises: List<ExerciseEntity>,
        muscles: List<MuscleGroupEntity>,
    ): StatsSnapshot {
        val exerciseMap = exercises.associateBy { it.id }
        val muscleMap = muscles.associateBy { it.id }
        val resultsBySession = results.groupBy { it.sessionId }
        val setsByResult = sets.groupBy { it.sessionSlotResultId }
        val weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

        val sessionSummaries = sessions.map { session ->
            val sessionResults = resultsBySession[session.id].orEmpty()
            val sessionSets = sessionResults.flatMap { setsByResult[it.id].orEmpty() }
            SessionSummary(
                session = session,
                exerciseNames = sessionResults.mapNotNull { exerciseMap[it.resolvedExerciseId]?.name },
                setCount = sessionSets.size,
                volume = sessionSets.sumOf { it.weight * it.reps },
            )
        }.filter { summary ->
            summary.setCount > 0 || summary.session.endedAtEpochMs != null
        }

        val exerciseStats = results
            .groupBy { it.resolvedExerciseId }
            .mapNotNull { (exerciseId, exerciseResults) ->
                val exercise = exerciseMap[exerciseId] ?: return@mapNotNull null
                val exerciseSets = exerciseResults.flatMap { setsByResult[it.id].orEmpty() }
                    .sortedBy { it.completedAtEpochMs }
                if (exerciseSets.isEmpty()) return@mapNotNull null
                val last = exerciseSets.last()
                ExerciseStat(
                    exercise = exercise,
                    setCount = exerciseSets.size,
                    sessionCount = exerciseResults.map { it.sessionId }.toSet().size,
                    lastWeight = last.weight,
                    lastReps = last.reps,
                    estimated1Rm = epley1Rm(last.weight, last.reps),
                    volume = exerciseSets.sumOf { it.weight * it.reps },
                )
            }
            .sortedByDescending { it.volume }

        val weeklySets = sets.filter { it.completedAtEpochMs >= weekStart }
        val resultById = results.associateBy { it.id }
        val muscleVolumes = weeklySets
            .mapNotNull { set ->
                val exerciseId = resultById[set.sessionSlotResultId]?.resolvedExerciseId ?: return@mapNotNull null
                val muscleId = exerciseMap[exerciseId]?.primaryMuscleGroupId ?: return@mapNotNull null
                muscleId to set
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (muscleId, muscleSets) ->
                val muscle = muscleMap[muscleId] ?: return@mapNotNull null
                MuscleVolume(
                    muscle = muscle,
                    setsThisWeek = muscleSets.size,
                    volumeThisWeek = muscleSets.sumOf { it.weight * it.reps },
                )
            }
            .sortedByDescending { it.setsThisWeek }

        return StatsSnapshot(
            sessions = sessionSummaries,
            exercises = exerciseStats,
            muscles = muscleVolumes,
            completedSessionCount = sessions.count { it.endedAtEpochMs != null },
            totalSets = sets.size,
            totalVolume = sets.sumOf { it.weight * it.reps },
        )
    }

    private fun epley1Rm(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return weight
        return weight * (1.0 + reps / 30.0)
    }
}