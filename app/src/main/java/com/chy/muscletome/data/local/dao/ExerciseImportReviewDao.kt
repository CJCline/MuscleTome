package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.chy.muscletome.data.local.entity.ExerciseImportResolutionEntity
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseImportReviewDao {
    @Query("SELECT * FROM pending_exercise_imports ORDER BY createdAtEpochMs")
    fun observePending(): Flow<List<PendingExerciseImportEntity>>

    @Query("SELECT * FROM pending_exercise_imports WHERE id = :id")
    suspend fun getPending(id: String): PendingExerciseImportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(row: PendingExerciseImportEntity)

    @Query("DELETE FROM pending_exercise_imports WHERE id = :id")
    suspend fun deletePending(id: String)

    @Upsert
    suspend fun recordResolution(row: ExerciseImportResolutionEntity)

    @Query("SELECT * FROM exercise_import_resolutions ORDER BY resolvedAtEpochMs")
    suspend fun getResolutions(): List<ExerciseImportResolutionEntity>

    @Upsert
    suspend fun upsertPendingImports(rows: List<PendingExerciseImportEntity>)

    @Upsert
    suspend fun upsertResolutions(rows: List<ExerciseImportResolutionEntity>)

    @Query("SELECT * FROM pending_exercise_imports")
    suspend fun getPendingSnapshot(): List<PendingExerciseImportEntity>
}
