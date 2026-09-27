package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercise_equipment_links",
    primaryKeys = ["exerciseId", "equipmentId"],
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("equipmentId")],
)
data class ExerciseEquipmentLinkEntity(
    val exerciseId: String,
    val equipmentId: String,
)
