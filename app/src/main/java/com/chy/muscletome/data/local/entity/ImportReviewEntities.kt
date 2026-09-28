package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "pending_exercise_imports")
data class PendingExerciseImportEntity(
    @PrimaryKey val id: String,
    val sourceKey: String?,
    val externalExerciseId: String?,
    val displayName: String,
    val pattern: String?,
    val muscleIdsJson: String,
    val equipmentIdsJson: String,
    val payloadJson: String,
    val candidateExerciseIdsJson: String,
    val createdAtEpochMs: Long,
)

@Serializable
@Entity(
    tableName = "exercise_import_resolutions",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["resolvedExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("resolvedExerciseId")],
)
data class ExerciseImportResolutionEntity(
    @PrimaryKey val pendingImportId: String,
    val action: String,
    val resolvedExerciseId: String?,
    val resolvedAtEpochMs: Long,
    val sourceKey: String? = null,
    val externalExerciseId: String? = null,
)
