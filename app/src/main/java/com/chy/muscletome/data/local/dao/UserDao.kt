package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUser(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUser(id: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @androidx.room.Update
    suspend fun update(user: UserEntity)

    @Query("SELECT equipmentId FROM user_available_equipment WHERE userId = :userId")
    fun observeAvailableEquipmentIds(userId: String): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("DELETE FROM user_available_equipment WHERE userId = :userId")
    suspend fun clearAvailableEquipment(userId: String)

    @Query("SELECT exerciseId FROM user_excluded_exercises WHERE userId = :userId")
    fun observeExcludedExerciseIds(userId: String): kotlinx.coroutines.flow.Flow<List<String>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertExcluded(row: com.chy.muscletome.data.local.entity.UserExcludedExerciseCrossRef)

    @Query("DELETE FROM user_excluded_exercises WHERE userId = :userId AND exerciseId = :exerciseId")
    suspend fun removeExcluded(userId: String, exerciseId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAvailableEquipment(rows: List<UserAvailableEquipmentCrossRef>)

    @Query("SELECT equipmentId FROM user_available_equipment WHERE userId = :userId")
    suspend fun getAvailableEquipmentIds(userId: String): List<String>

    @Query("SELECT exerciseId FROM user_excluded_exercises WHERE userId = :userId")
    suspend fun getExcludedExerciseIds(userId: String): List<String>
}