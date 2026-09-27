package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercise_secondary_targets",
    primaryKeys = ["exerciseId", "muscleGroupId"],
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MuscleGroupEntity::class, parentColumns = ["id"], childColumns = ["muscleGroupId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("muscleGroupId")],
)
data class ExerciseSecondaryTargetEntity(
    val exerciseId: String,
    val muscleGroupId: String,
)
