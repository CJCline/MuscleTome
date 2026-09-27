package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercise_instructions",
    primaryKeys = ["exerciseId", "sortOrder"],
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseInstructionEntity(
    val exerciseId: String,
    val sortOrder: Int,
    val instruction: String,
)
