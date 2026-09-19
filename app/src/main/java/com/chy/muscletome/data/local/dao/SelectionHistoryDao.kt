package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity

@Dao
interface SelectionHistoryDao {
    @Query("SELECT * FROM exercise_selection_history WHERE userId = :userId")
    suspend fun getAll(userId: String): List<ExerciseSelectionHistoryEntity>

    @Query(
        """
        SELECT * FROM exercise_selection_history
        WHERE userId = :userId AND exerciseId = :exerciseId AND muscleGroupId = :muscleGroupId
        """,
    )
    suspend fun get(
        userId: String,
        exerciseId: String,
        muscleGroupId: String,
    ): ExerciseSelectionHistoryEntity?

    @Upsert
    suspend fun upsert(row: ExerciseSelectionHistoryEntity)
}