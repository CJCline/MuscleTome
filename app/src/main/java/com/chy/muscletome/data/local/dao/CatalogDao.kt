package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT COUNT(*) FROM muscle_groups")
    suspend fun muscleCount(): Int

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Query("SELECT * FROM muscle_groups ORDER BY name")
    fun observeMuscleGroups(): Flow<List<MuscleGroupEntity>>

    @Query("SELECT * FROM equipment ORDER BY name")
    fun observeEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: String): ExerciseEntity?

    @Query("SELECT * FROM muscle_groups ORDER BY name")
    suspend fun getMuscleGroups(): List<MuscleGroupEntity>

    @Query("SELECT * FROM equipment ORDER BY name")
    suspend fun getEquipment(): List<EquipmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroups(rows: List<MuscleGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquipment(rows: List<EquipmentEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExerciseEquipment(rows: List<ExerciseEquipmentCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSecondaryMuscles(rows: List<ExerciseSecondaryMuscleCrossRef>)

    @Query("SELECT * FROM exercise_equipment")
    suspend fun getExerciseEquipment(): List<ExerciseEquipmentCrossRef>

    @Query("SELECT * FROM exercise_secondary_muscles")
    suspend fun getSecondaryMuscles(): List<ExerciseSecondaryMuscleCrossRef>

    @Query("SELECT * FROM exercises")
    suspend fun getExercises(): List<ExerciseEntity>
}