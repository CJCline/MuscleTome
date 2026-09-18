package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.entity.UserExcludedExerciseCrossRef
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
) {
    private val userId = SeedCatalog.LOCAL_USER_ID

    fun observeUser(): Flow<UserEntity?> = userDao.observeUser(userId)
    fun observeAvailableEquipmentIds(): Flow<List<String>> = userDao.observeAvailableEquipmentIds(userId)
    fun observeExcludedExerciseIds(): Flow<List<String>> = userDao.observeExcludedExerciseIds(userId)

    suspend fun setWeightUnit(unit: WeightUnit) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(weightUnit = unit))
    }

    suspend fun setMatchStrictness(value: MatchStrictness) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(primaryMatchStrictness = value))
    }

    suspend fun setPreferCompoundEarly(value: Boolean) {
        val user = userDao.getUser(userId) ?: return
        userDao.update(user.copy(preferCompoundEarly = value))
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
}