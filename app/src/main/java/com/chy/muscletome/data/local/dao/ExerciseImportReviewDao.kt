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

    @Query("SELECT * FROM pending_exercise_imports WHERE sourceKey IS :sourceKey AND externalExerciseId IS :externalId LIMIT 1")
    suspend fun findPending(sourceKey: String?, externalId: String?): PendingExerciseImportEntity?

    @Query("SELECT * FROM exercise_import_resolutions WHERE pendingImportId = :id")
    suspend fun getResolution(id: String): ExerciseImportResolutionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM exercise_import_resolutions WHERE sourceKey = :sourceKey AND externalExerciseId = :externalId)")
    suspend fun hasResolutionForIdentity(sourceKey: String, externalId: String): Boolean

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
