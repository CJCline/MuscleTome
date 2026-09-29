package com.chy.muscletome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A movement family row (Phase 5A). The eight legacy IDs (`squat`, `bench_press`,
 * `row`, `deadlift`, `overhead_press`, `curl`, `lunge`, `plank`) are seeded as
 * `BUILT_IN`; user-created families use `USER_CREATED`. `normalizedKey` is the
 * dedup key: creates colliding with it must surface the existing family, not
 * create a duplicate.
 */
@Serializable
@Entity(
    tableName = "movement_families",
    indices = [Index(value = ["normalizedKey"], unique = true)],
)
data class MovementFamilyEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val normalizedKey: String,
    val createdAtEpochMs: Long,
    val origin: String = "BUILT_IN",
)

/**
 * A downloaded copy of an [ExerciseMediaEntity] (Phase 5C). The original URI,
 * attribution and license columns on `exercise_media` are never mutated; this
 * row only records the cached file so imported media can display offline.
 * Intentionally excluded from backups — restore re-fetches on demand.
 */
@Serializable
@Entity(
    tableName = "exercise_media_cache",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId"), Index("fetchedAtEpochMs")],
)
data class ExerciseMediaCacheEntity(
    @PrimaryKey val mediaId: String,
    val localPath: String,
    val fetchedAtEpochMs: Long,
    val bytes: Long,
    /** Wall-clock of the last license-allowlist check so re-fetches re-validate. */
    val licenseCheckedAtEpochMs: Long = 0,
)

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
