package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.SubscriptionStatus
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.domain.model.WeightUnit

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    val primaryMatchStrictness: MatchStrictness = MatchStrictness.LOOSE,
    val preferCompoundEarly: Boolean = true,
    val maxDifficulty: Difficulty = Difficulty.ADVANCED,
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    val subscriptionExpiryEpochMs: Long? = null,
    val lastVerifiedEntitlementEpochMs: Long? = null,
)

@Entity(tableName = "muscle_groups")
data class MuscleGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentGroupId: String? = null,
)

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val name: String,
)

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
    indices = [Index("primaryMuscleGroupId"), Index("createdByUserId"), Index("name")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    val movementType: MovementType,
    val primaryMuscleGroupId: String,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val unilateral: Boolean = false,
    val source: ExerciseSource = ExerciseSource.SEED,
    val notes: String = "",
    val demoUri: String? = null,
)

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
)

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
)

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