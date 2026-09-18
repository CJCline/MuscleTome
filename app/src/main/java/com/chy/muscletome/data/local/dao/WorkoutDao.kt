package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY startedAtEpochMs DESC")
    fun observeSessions(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM session_slot_results WHERE sessionId = :sessionId")
    fun observeSlotResults(sessionId: String): Flow<List<SessionSlotResultEntity>>

    @Query(
        """
        SELECT sl.* FROM set_logs sl
        INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id
        WHERE ssr.sessionId = :sessionId
        ORDER BY sl.completedAtEpochMs
        """,
    )
    fun observeSetsForSession(sessionId: String): Flow<List<SetLogEntity>>

    @Query(
        """
        SELECT sl.* FROM set_logs sl
        INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id
        WHERE ssr.resolvedExerciseId = :exerciseId
        ORDER BY sl.completedAtEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun lastSetForExercise(exerciseId: String): SetLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(row: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlotResult(row: SessionSlotResultEntity)

    @Update
    suspend fun updateSlotResult(row: SessionSlotResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(row: SetLogEntity)

    @Query("UPDATE workout_sessions SET endedAtEpochMs = :endedAt WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long)

    @Query("SELECT * FROM session_slot_results")
    fun observeAllSlotResults(): kotlinx.coroutines.flow.Flow<List<com.chy.muscletome.data.local.entity.SessionSlotResultEntity>>

    @Query(
        """
        SELECT sl.* FROM set_logs sl
        INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id
        INNER JOIN workout_sessions ws ON ssr.sessionId = ws.id
        WHERE ws.userId = :userId
        ORDER BY sl.completedAtEpochMs DESC
        """,
    )
    fun observeAllSets(userId: String): kotlinx.coroutines.flow.Flow<List<com.chy.muscletome.data.local.entity.SetLogEntity>>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE userId = :userId
          AND routineDayId = :dayId
          AND endedAtEpochMs IS NULL
        ORDER BY startedAtEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun findOpenSession(userId: String, dayId: String): WorkoutSessionEntity?

    @Query("SELECT COUNT(*) FROM set_logs sl INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id WHERE ssr.sessionId = :sessionId")
    suspend fun setCountForSession(sessionId: String): Int
}