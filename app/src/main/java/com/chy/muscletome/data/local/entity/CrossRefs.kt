package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exercise_equipment",
    primaryKeys = ["exerciseId", "equipmentId"],
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("equipmentId")],
)
data class ExerciseEquipmentCrossRef(
    val exerciseId: String,
    val equipmentId: String,
)

@Entity(
    tableName = "exercise_secondary_muscles",
    primaryKeys = ["exerciseId", "muscleGroupId"],
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleGroupEntity::class, parentColumns = ["id"], childColumns = ["muscleGroupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("muscleGroupId")],
)
data class ExerciseSecondaryMuscleCrossRef(
    val exerciseId: String,
    val muscleGroupId: String,
)

@Entity(
    tableName = "slot_target_muscles",
    primaryKeys = ["slotId", "muscleGroupId"],
    foreignKeys = [
        ForeignKey(entity = RoutineSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleGroupEntity::class, parentColumns = ["id"], childColumns = ["muscleGroupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("muscleGroupId")],
)
data class SlotTargetMuscleCrossRef(
    val slotId: String,
    val muscleGroupId: String,
)

@Entity(
    tableName = "slot_equipment_filters",
    primaryKeys = ["slotId", "equipmentId"],
    foreignKeys = [
        ForeignKey(entity = RoutineSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("equipmentId")],
)
data class SlotEquipmentFilterCrossRef(
    val slotId: String,
    val equipmentId: String,
)

@Entity(
    tableName = "user_available_equipment",
    primaryKeys = ["userId", "equipmentId"],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("equipmentId")],
)
data class UserAvailableEquipmentCrossRef(
    val userId: String,
    val equipmentId: String,
)

@Entity(
    tableName = "user_excluded_exercises",
    primaryKeys = ["userId", "exerciseId"],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId")],
)
data class UserExcludedExerciseCrossRef(
    val userId: String,
    val exerciseId: String,
    val reason: String = "",
)