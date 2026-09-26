package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleVolumeTargetEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.session.PersonalRecords
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
    /** Weekly set goal from muscle_volume_targets; null = no target. */
    val weeklySetTarget: Int? = null,
)

data class StatsSnapshot(
    val sessions: List<SessionSummary> = emptyList(),
    val exercises: List<ExerciseStat> = emptyList(),
    val muscles: List<MuscleVolume> = emptyList(),
    val personalRecords: List<PersonalRecord> = emptyList(),
    val completedSessionCount: Int = 0,
    val totalSets: Int = 0,
    val totalVolume: Double = 0.0,
    /** Unit the (already-converted) stored weights display in. */
    val weightUnit: WeightUnit = WeightUnit.KG,
)

/** One exercise's personal record: the all-time best e1RM set. */
data class PersonalRecord(
    val exercise: ExerciseEntity,
    val set: SetLogEntity,
    /** Epley e1RM of the record set. */
    val e1rm: Double,
)

@Singleton
class StatsRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val catalogDao: CatalogDao,
    private val userDao: UserDao,
) {
    fun observeStats(): Flow<StatsSnapshot> = combine(
        workoutDao.observeSessions(SeedCatalog.LOCAL_USER_ID),
        workoutDao.observeAllSlotResults(),
        workoutDao.observeAllSets(SeedCatalog.LOCAL_USER_ID),
        catalogDao.observeExercises(),
        catalogDao.observeMuscleGroups(),
        userDao.observeVolumeTargets(SeedCatalog.LOCAL_USER_ID),
        userDao.observeUser(SeedCatalog.LOCAL_USER_ID),
    ) { values ->
        // combine()'s typed overloads stop at five flows — this is the
        // vararg form, so index carefully (DayDetail bug lesson).
        @Suppress("UNCHECKED_CAST")
        val sessions = values[0] as List<WorkoutSessionEntity>
        @Suppress("UNCHECKED_CAST")
        val results = values[1] as List<SessionSlotResultEntity>
        @Suppress("UNCHECKED_CAST")
        val sets = values[2] as List<SetLogEntity>
        @Suppress("UNCHECKED_CAST")
        val exercises = values[3] as List<ExerciseEntity>
        @Suppress("UNCHECKED_CAST")
        val muscles = values[4] as List<MuscleGroupEntity>
        @Suppress("UNCHECKED_CAST")
        val targets = values[5] as List<MuscleVolumeTargetEntity>
        @Suppress("UNCHECKED_CAST")
        val user = values[6] as UserEntity
        buildSnapshot(sessions, results, sets, exercises, muscles, targets, user.weightUnit)
    }

    private fun buildSnapshot(
        sessions: List<WorkoutSessionEntity>,
        results: List<SessionSlotResultEntity>,
        sets: List<SetLogEntity>,
        exercises: List<ExerciseEntity>,
        muscles: List<MuscleGroupEntity>,
        targets: List<MuscleVolumeTargetEntity>,
        weightUnit: WeightUnit,
    ): StatsSnapshot {
        val targetByMuscle = targets.associateBy({ it.muscleGroupId }, { it.weeklySetTarget })
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
                    estimated1Rm = PersonalRecords.epley1Rm(last.weight, last.reps),
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
                    weeklySetTarget = targetByMuscle[muscleId],
                )
            }
            .sortedByDescending { it.setsThisWeek }

        // Personal records: the all-time best e1RM set per exercise. The
        // record set is the one with the highest e1RM (earliest wins ties —
        // it set the bar); the list reads newest record first.
        val personalRecords = results
            .groupBy { it.resolvedExerciseId }
            .mapNotNull { (exerciseId, exerciseResults) ->
                val exercise = exerciseMap[exerciseId] ?: return@mapNotNull null
                val exerciseSets = exerciseResults.flatMap { setsByResult[it.id].orEmpty() }
                    .sortedBy { it.completedAtEpochMs }
                // maxByOrNull keeps the first maximum, so ties resolve to
                // the set that set the bar, not a later duplicate.
                val recordSet = exerciseSets.maxByOrNull { PersonalRecords.epley1Rm(it.weight, it.reps) }
                    ?: return@mapNotNull null
                PersonalRecord(
                    exercise = exercise,
                    set = recordSet,
                    e1rm = PersonalRecords.epley1Rm(recordSet.weight, recordSet.reps),
                )
            }
            .sortedByDescending { it.set.completedAtEpochMs }

        return StatsSnapshot(
            sessions = sessionSummaries,
            exercises = exerciseStats,
            muscles = muscleVolumes,
            personalRecords = personalRecords,
            completedSessionCount = sessions.count { it.endedAtEpochMs != null },
            totalSets = sets.size,
            totalVolume = sets.sumOf { it.weight * it.reps },
            weightUnit = weightUnit,
        )
    }
}