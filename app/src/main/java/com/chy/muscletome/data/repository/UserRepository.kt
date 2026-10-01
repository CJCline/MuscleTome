package com.chy.muscletome.data.repository

import androidx.room.withTransaction
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.MuscleVolumeTargetEntity
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.entity.UserExcludedExerciseCrossRef
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.DefaultRepPreference
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightStep
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.session.WeightUnits
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserRepository @Inject constructor(
    private val database: MuscleTomeDatabase,
    private val userDao: UserDao,
    private val workoutDao: WorkoutDao,
) {
    private val userId = SeedCatalog.LOCAL_USER_ID

    fun observeUser(): Flow<UserEntity?> = userDao.observeUser(userId)
    fun observeAvailableEquipmentIds(): Flow<List<String>> = userDao.observeAvailableEquipmentIds(userId)
    fun observeExcludedExerciseIds(): Flow<List<String>> = userDao.observeExcludedExerciseIds(userId)

    /**
     * Switches the weight unit and converts *all* logged set weights to
     * match, atomically. Stored numbers always live in the user's current
     * unit — otherwise the unit preference would be a relabeling lie, with
     * "100 kg" history re-rendered as "100 lb". One transaction because the
     * labels and the numbers must move together: crash mid-way leaves either
     * a converted history on an unconverted label or the reverse, and both
     * corrupt every PR and volume stat.
     */
    suspend fun setWeightUnit(unit: WeightUnit) {
        database.withTransaction {
            val user = userDao.getUser(userId) ?: return@withTransaction
            if (user.weightUnit == unit) return@withTransaction
            userDao.update(user.copy(weightUnit = unit))
            workoutDao.scaleAllWeights(
                WeightUnits.convert(1.0, from = user.weightUnit, to = unit),
            )
        }
    }

    suspend fun setMatchStrictness(value: MatchStrictness) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(primaryMatchStrictness = value))
    }

    /** Which effort scale (RPE/RIR) the workout UI speaks. */
    suspend fun setEffortScale(value: EffortScale) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(effortScale = value))
    }

    suspend fun setDefaultRepPreference(value: DefaultRepPreference) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(defaultRepPreference = value))
    }

    suspend fun setWeightStep(value: WeightStep) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(weightStep = value))
    }

    suspend fun setPreferCompoundEarly(value: Boolean) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(preferCompoundEarly = value))
    }

    /** Points Home's "Up next" at [routineId]; null falls back to the newest routine. */
    suspend fun setActiveRoutine(routineId: String?) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(activeRoutineId = routineId))
    }

    suspend fun setAvailableEquipment(ids: Set<String>) {
        userDao.clearAvailableEquipment(userId)
        userDao.insertAvailableEquipment(ids.map { UserAvailableEquipmentCrossRef(userId, it) })
    }

    suspend fun excludeExercise(exerciseId: String, reason: String = "Excluded in settings") {
        userDao.insertExcluded(UserExcludedExerciseCrossRef(userId, exerciseId, reason))
    }

    suspend fun includeExercise(exerciseId: String) {
        userDao.removeExcluded(userId, exerciseId)
    }

    /** Weekly set target for a muscle; null clears it. */
    suspend fun setWeeklySetTarget(muscleGroupId: String, weeklySetTarget: Int?) {
        if (weeklySetTarget == null) {
            userDao.deleteVolumeTarget(userId, muscleGroupId)
        } else {
            userDao.upsertVolumeTarget(
                MuscleVolumeTargetEntity(
                    userId = userId,
                    muscleGroupId = muscleGroupId,
                    weeklySetTarget = weeklySetTarget.coerceIn(1, 100),
                ),
            )
        }
    }
}