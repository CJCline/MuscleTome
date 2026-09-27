package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercise_media",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseMediaEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val type: String,
    val uri: String,
    val sourceKey: String? = null,
    val attribution: String? = null,
    val creator: String? = null,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "exercise_source_identities",
    primaryKeys = ["sourceKey", "externalExerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseSourceIdentityEntity(
    val sourceKey: String,
    val externalExerciseId: String,
    val exerciseId: String,
    val sourceUrl: String? = null,
    val importedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null,
)
