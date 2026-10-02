package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

/** One logged set for an exercise, with the session it belongs to. */
data class ExerciseSetPoint(
    val id: String,
    val sessionId: String,
    val sessionStartEpochMs: Long,
    val completedAtEpochMs: Long,
    val weight: Double,
    val reps: Int,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY startedAtEpochMs DESC")
    suspend fun getSessions(userId: String): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY startedAtEpochMs DESC")
    fun observeSessions(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    /** Finished sessions since [sinceEpochMs], oldest last (consistency math). */
    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE userId = :userId AND endedAtEpochMs IS NOT NULL " +
            "AND startedAtEpochMs >= :sinceEpochMs " +
            "ORDER BY startedAtEpochMs",
    )
    suspend fun getSessionsSince(userId: String, sinceEpochMs: Long): List<WorkoutSessionEntity>

    @Query("SELECT DISTINCT resolvedExerciseId FROM session_slot_results")
    suspend fun getResolvedExerciseIds(): List<String>

    @Query("SELECT * FROM session_slot_results WHERE sessionId = :sessionId")
    fun observeSlotResults(sessionId: String): Flow<List<SessionSlotResultEntity>>

    @Query("SELECT * FROM session_slot_results WHERE sessionId = :sessionId")
    suspend fun getSlotResults(sessionId: String): List<SessionSlotResultEntity>

    @Query(
        """
        SELECT sl.* FROM set_logs sl
        INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id
        WHERE ssr.sessionId = :sessionId
        ORDER BY sl.completedAtEpochMs
        """,
    )
    suspend fun getSetsForSession(sessionId: String): List<SetLogEntity>

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

    @Query(
        """
        SELECT sl.id AS id,
               ssr.sessionId AS sessionId,
               ws.startedAtEpochMs AS sessionStartEpochMs,
               sl.completedAtEpochMs AS completedAtEpochMs,
               sl.weight AS weight,
               sl.reps AS reps
        FROM set_logs sl
        INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id
        INNER JOIN workout_sessions ws ON ssr.sessionId = ws.id
        WHERE ssr.resolvedExerciseId = :exerciseId
        ORDER BY sl.completedAtEpochMs ASC
        """,
    )
    fun observeExerciseSetPoints(exerciseId: String): Flow<List<ExerciseSetPoint>>

    @Query(
        """
        SELECT MAX(sl.weight) FROM set_logs sl
        INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id
        WHERE ssr.resolvedExerciseId = :exerciseId
        """,
    )
    suspend fun bestWeightForExercise(exerciseId: String): Double?

    @Query(
        """
        SELECT COUNT(DISTINCT ssr.sessionId) FROM session_slot_results ssr
        INNER JOIN set_logs sl ON sl.sessionSlotResultId = ssr.id
        WHERE ssr.resolvedExerciseId = :exerciseId
        """,
    )
    suspend fun sessionCountForExercise(exerciseId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(row: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlotResult(row: SessionSlotResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlotResults(rows: List<SessionSlotResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(rows: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLogs(rows: List<SetLogEntity>)

    @Query("SELECT * FROM session_slot_results")
    suspend fun getAllSlotResults(): List<SessionSlotResultEntity>

    /** Makes room for an ad-hoc superset partner spliced in after the anchor. */
    @Query(
        "UPDATE session_slot_results SET sortOrder = sortOrder + 1 " +
            "WHERE sessionId = :sessionId AND sortOrder > :afterSortOrder",
    )
    suspend fun shiftSortOrdersAbove(sessionId: String, afterSortOrder: Int)

    /** Re-sets superset membership on a session result (ad-hoc grouping). */
    @Query("UPDATE session_slot_results SET supersetGroupId = :groupId WHERE id = :resultId")
    suspend fun updateSlotResultGroup(resultId: String, groupId: String?)

    /**
     * Shifts everything after the anchor down one position and inserts the
     * ad-hoc partner into the freed slot — atomically, so a crash can't leave
     * a gap or a half-positioned row.
     */
    @Transaction
    suspend fun insertAdHocResult(
        sessionId: String,
        afterSortOrder: Int,
        row: SessionSlotResultEntity,
    ) {
        shiftSortOrdersAbove(sessionId, afterSortOrder)
        insertSlotResult(row)
    }

    @Query("SELECT * FROM set_logs")
    suspend fun getAllSetLogs(): List<SetLogEntity>

    @Update
    suspend fun updateSlotResult(row: SessionSlotResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(row: SetLogEntity)

    @Query("SELECT * FROM set_logs WHERE sessionSlotResultId = :resultId ORDER BY setNumber")
    suspend fun getSetsForSlotResult(resultId: String): List<SetLogEntity>

    @Update
    suspend fun updateSet(row: SetLogEntity)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSetById(id: String)

    /**
     * Unit-switch support: rescales every logged weight by [factor] in one
     * statement. Runs inside the user-pref transaction (see
     * UserRepository.setWeightUnit) so labels and numbers can't drift apart.
     */
    @Query("UPDATE set_logs SET weight = weight * :factor")
    suspend fun scaleAllWeights(factor: Double)

    @Query("UPDATE session_slot_results SET sessionNote = :note WHERE id = :id")
    suspend fun updateSessionNote(id: String, note: String)

    /**
     * Session row + its resolved slot results land atomically — a crash
     * mid-insert can no longer strand a session with missing results.
     */
    @Transaction
    suspend fun startSessionTransaction(
        session: WorkoutSessionEntity,
        slotResults: List<SessionSlotResultEntity>,
    ) {
        insertSession(session)
        slotResults.forEach { insertSlotResult(it) }
    }

    @Query("UPDATE workout_sessions SET endedAtEpochMs = :endedAt WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

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

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE userId = :userId AND endedAtEpochMs IS NULL
        ORDER BY startedAtEpochMs DESC
        LIMIT 1
        """,
    )
    fun observeOpenSession(userId: String): Flow<WorkoutSessionEntity?>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE userId = :userId AND endedAtEpochMs IS NOT NULL
        ORDER BY endedAtEpochMs DESC
        LIMIT 1
        """,
    )
    fun observeLastCompletedSession(userId: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT COUNT(*) FROM set_logs sl INNER JOIN session_slot_results ssr ON sl.sessionSlotResultId = ssr.id WHERE ssr.sessionId = :sessionId")
    suspend fun setCountForSession(sessionId: String): Int

    @Query("UPDATE session_slot_results SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSlotResultSortOrder(id: String, sortOrder: Int)
}