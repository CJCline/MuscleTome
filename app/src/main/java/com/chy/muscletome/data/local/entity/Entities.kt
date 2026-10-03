package com.chy.muscletome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chy.muscletome.domain.model.DefaultRepPreference
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.domain.model.WeightStep
import com.chy.muscletome.domain.model.WeightUnit
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    val primaryMatchStrictness: MatchStrictness = MatchStrictness.LOOSE,
    val preferCompoundEarly: Boolean = true,
    val maxDifficulty: Difficulty = Difficulty.ADVANCED,
    /** Which effort scale the workout UI speaks (RPE stays the stored canon). */
    val effortScale: EffortScale = EffortScale.RPE,
    /** Default rep count preference: MINIMUM or MAXIMUM of configured range. */
    val defaultRepPreference: DefaultRepPreference = DefaultRepPreference.MINIMUM,
    /** Weight step preference for workout controls. */
    val weightStep: WeightStep = WeightStep.STEP_5,
    /**
     * The routine Home's "Up next" trains. Null (or a stale pointer to a
     * deleted routine) falls back to the newest routine; the user switches
     * programs explicitly on Home. No FK on purpose: deleting the pointed-to
     * routine is re-selection, not an error.
     */
    val activeRoutineId: String? = null,
)

@Serializable
@Entity(tableName = "muscle_groups")
data class MuscleGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentGroupId: String? = null,
)

@Serializable
@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Serializable
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = MuscleGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryMuscleGroupId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("primaryMuscleGroupId"),
        Index("createdByUserId"),
        Index("name"),
        Index("parentExerciseId"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "",
    val muscleGroup: String = "",
    val description: String = "",
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    val movementType: MovementType = MovementType.COMPOUND,
    val primaryMuscleGroupId: String = "",
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val parentExerciseId: String? = null,
    val isCustom: Boolean = false,
    val isDefault: Boolean = true,
    val createdByUserId: String? = null,
    val unilateral: Boolean = false,
    val source: ExerciseSource = ExerciseSource.SEED,
    val notes: String = "",
    val demoUri: String? = null,
)

@Serializable
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ownerId")],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ownerId: String,
    val createdAtEpochMs: Long,
)

@Serializable
@Entity(
    tableName = "routine_days",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class RoutineDayEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val name: String,
    val orderIndex: Int,
)

@Serializable
@Entity(
    tableName = "routine_slots",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineDayId"), Index("exerciseId")],
)
data class RoutineSlotEntity(
    @PrimaryKey val id: String,
    val routineDayId: String,
    val orderIndex: Int,
    val type: SlotType,
    val exerciseId: String? = null,
    val targetMovementType: TargetMovementType = TargetMovementType.ANY,
    val sets: Int,
    val repRangeMin: Int,
    val repRangeMax: Int,
    val restSeconds: Int,
    val targetRpe: Float? = null,
    /**
     * Superset/circuit membership: slots sharing a group id are trained
     * round-robin in the live session. Null = standalone slot. Membership is
     * by id, not adjacency — reordering rows never breaks a group.
     */
    val supersetGroupId: String? = null,
)

@Serializable
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("userId"), Index("routineDayId"), Index("startedAtEpochMs")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val routineDayId: String?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
)

@Serializable
@Entity(
    tableName = "session_slot_results",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineSlotId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["resolvedExerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("routineSlotId"), Index("resolvedExerciseId")],
)
data class SessionSlotResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val routineSlotId: String?,
    val resolvedExerciseId: String,
    val selectionReason: SelectionReason,
    /**
     * Free-form remark about this exercise *in this session* — distinct from
     * the shared exercise cues in `ExerciseEntity.notes`. The DB default
     * matches the ALTER TABLE migration that added the column.
     */
    @ColumnInfo(defaultValue = "") val sessionNote: String = "",
    /**
     * Snapshot of `RoutineSlotEntity.supersetGroupId` taken at session start;
     * ad-hoc supersets created mid-workoot are grouped only here. Same
     * snapshot semantics as the resolved exercise: routine edits never
     * rewrite a running session.
     */
    val supersetGroupId: String? = null,
    /**
     * Explicit position of this result within the session — decoupled from
     * the routine's (editable) slot order so ad-hoc inserts can splice in
     * without renumbering the routine itself. Backfilled from slot order by
     * the v3→v4 migration; written at session start thereafter.
     */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /**
     * Planned sets for ad-hoc rows (which have no routine slot to ask);
     * null = slot-backed, use `RoutineSlotEntity.sets`.
     */
    val plannedSets: Int? = null,
)

@Serializable
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionSlotResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionSlotResultId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionSlotResultId")],
)
data class SetLogEntity(
    @PrimaryKey val id: String,
    val sessionSlotResultId: String,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Float? = null,
    val restSecondsActual: Int? = null,
    val completedAtEpochMs: Long,
)

@Serializable
@Entity(
    tableName = "canonical_exercises",
    primaryKeys = ["exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryMuscleGroupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("primaryMuscleGroupId"), Index("movementFamilyId")],
)
data class CanonicalExerciseEntity(
    val exerciseId: String,
    val primaryMuscleGroupId: String? = null,
    val instructions: String = "",
    val movementFamilyId: String? = null,
    val origin: String = "BUILT_IN",
    val isUserEdited: Boolean = false,
)

@Serializable
@Entity(
    tableName = "exercise_selection_history",
    primaryKeys = ["userId", "exerciseId", "muscleGroupId"],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleGroupEntity::class, parentColumns = ["id"], childColumns = ["muscleGroupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId"), Index("exerciseId"), Index("muscleGroupId")],
)
data class ExerciseSelectionHistoryEntity(
    val userId: String,
    val exerciseId: String,
    val muscleGroupId: String,
    val lastUsedAtEpochMs: Long,
    val useCount30d: Int = 0,
    val useCount90d: Int = 0,
    val completedCount: Int = 0,
    val rerollCount: Int = 0,
    val affinity: Float = 0f,
)

@Serializable
@Entity(
    tableName = "muscle_volume_targets",
    primaryKeys = ["userId", "muscleGroupId"],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleGroupEntity::class, parentColumns = ["id"], childColumns = ["muscleGroupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId"), Index("muscleGroupId")],
)
data class MuscleVolumeTargetEntity(
    val userId: String,
    val muscleGroupId: String,
    /** Weekly set target for this muscle; no row (or null) = no target. */
    val weeklySetTarget: Int? = null,
)