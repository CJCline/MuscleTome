package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT COUNT(*) FROM muscle_groups")
    suspend fun muscleCount(): Int

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Query("SELECT * FROM muscle_groups ORDER BY name")
    fun observeMuscleGroups(): Flow<List<MuscleGroupEntity>>

    @Query("SELECT * FROM equipment ORDER BY name")
    fun observeEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE (:query = '' OR name LIKE '%' || :query || '%' ESCAPE '\')
          AND (:muscleGroupId IS NULL OR primaryMuscleGroupId = :muscleGroupId)
        ORDER BY name
        """,
    )
    fun searchExercises(query: String, muscleGroupId: String?): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: String): ExerciseEntity?

    @Transaction
    @Query("SELECT * FROM exercises")
    suspend fun getAllCanonicalExercises(): List<ExerciseWithCanonicalRelations>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroupsForImport(rows: List<MuscleGroupEntity>)

    @Transaction
    suspend fun saveCanonicalExerciseBundle(
        exercise: ExerciseEntity,
        metadata: CanonicalExerciseEntity,
        instructions: List<ExerciseInstructionEntity>,
        muscleGroups: List<MuscleGroupEntity>,
        equipment: List<EquipmentEntity>,
        equipmentLinks: List<ExerciseEquipmentLinkEntity>,
        secondaryTargets: List<ExerciseSecondaryTargetEntity>,
        media: List<ExerciseMediaEntity>,
        sourceIdentities: List<ExerciseSourceIdentityEntity>,
        update: Boolean,
    ) {
        insertMuscleGroups(muscleGroups)
        upsertEquipment(equipment)
        if (update) upsertExercises(listOf(exercise)) else insertExercises(listOf(exercise))
        upsertCanonicalMetadata(listOf(metadata))
        deleteInstructions(exercise.id)
        deleteCanonicalEquipment(exercise.id)
        deleteSecondaryTargets(exercise.id)
        deleteExerciseMedia(exercise.id)
        deleteSourceIdentities(exercise.id)
        upsertInstructions(instructions)
        upsertCanonicalEquipment(equipmentLinks)
        upsertSecondaryTargets(secondaryTargets)
        upsertExerciseMedia(media)
        upsertSourceIdentities(sourceIdentities)
    }

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getCanonicalExercise(id: String): ExerciseWithCanonicalRelations?

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeCanonicalExercise(id: String): Flow<ExerciseWithCanonicalRelations?>

    @Query("SELECT * FROM exercise_source_identities WHERE sourceKey = :sourceKey AND externalExerciseId = :externalId")
    suspend fun findBySourceIdentity(sourceKey: String, externalId: String): ExerciseSourceIdentityEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeExercise(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getExerciseByName(name: String): ExerciseEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM exercises WHERE name = :name COLLATE NOCASE)")
    fun observeNameTaken(name: String): Flow<Boolean>

    @Query("UPDATE exercises SET notes = :notes WHERE id = :exerciseId")
    suspend fun updateExerciseNotes(exerciseId: String, notes: String)

    @Query("SELECT * FROM muscle_groups ORDER BY name")
    suspend fun getMuscleGroups(): List<MuscleGroupEntity>

    @Query("SELECT * FROM equipment ORDER BY name")
    suspend fun getEquipment(): List<EquipmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroups(rows: List<MuscleGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquipment(rows: List<EquipmentEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertCanonicalMetadata(rows: List<CanonicalExerciseEntity>)

    @Upsert
    suspend fun upsertInstructions(rows: List<ExerciseInstructionEntity>)

    @Upsert
    suspend fun upsertExerciseMedia(rows: List<ExerciseMediaEntity>)

    @Upsert
    suspend fun upsertSourceIdentities(rows: List<ExerciseSourceIdentityEntity>)

    @Upsert
    suspend fun upsertSecondaryTargets(rows: List<ExerciseSecondaryTargetEntity>)

    @Upsert
    suspend fun upsertCanonicalEquipment(rows: List<ExerciseEquipmentLinkEntity>)

    @Query("DELETE FROM exercise_instructions WHERE exerciseId = :exerciseId")
    suspend fun deleteInstructions(exerciseId: String)

    @Query("DELETE FROM exercise_media WHERE exerciseId = :exerciseId")
    suspend fun deleteExerciseMedia(exerciseId: String)

    @Query("DELETE FROM exercise_source_identities WHERE exerciseId = :exerciseId")
    suspend fun deleteSourceIdentities(exerciseId: String)

    @Query("DELETE FROM exercise_secondary_targets WHERE exerciseId = :exerciseId")
    suspend fun deleteSecondaryTargets(exerciseId: String)

    @Query("DELETE FROM exercise_equipment_links WHERE exerciseId = :exerciseId")
    suspend fun deleteCanonicalEquipment(exerciseId: String)

    @Query("DELETE FROM exercise_equipment WHERE exerciseId = :exerciseId")
    suspend fun deleteLegacyEquipment(exerciseId: String)

    /** Backup import restores the full row (notes, edits) — upsert, not ignore. */
    @Upsert
    suspend fun upsertExercises(rows: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertMuscleGroups(rows: List<MuscleGroupEntity>)

    @Upsert
    suspend fun upsertEquipment(rows: List<EquipmentEntity>)

    @Upsert
    suspend fun upsertExerciseEquipment(rows: List<ExerciseEquipmentCrossRef>)

    @Upsert
    suspend fun upsertSecondaryMuscles(rows: List<ExerciseSecondaryMuscleCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExerciseEquipment(rows: List<ExerciseEquipmentCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSecondaryMuscles(rows: List<ExerciseSecondaryMuscleCrossRef>)

    @Query("SELECT * FROM exercise_equipment")
    suspend fun getExerciseEquipment(): List<ExerciseEquipmentCrossRef>

    @Query("SELECT * FROM exercise_secondary_muscles")
    suspend fun getSecondaryMuscles(): List<ExerciseSecondaryMuscleCrossRef>

    @Query("SELECT * FROM exercises")
    suspend fun getExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM canonical_exercises")
    suspend fun getAllCanonicalMetadata(): List<CanonicalExerciseEntity>

    @Query("SELECT * FROM exercise_instructions ORDER BY exerciseId, sortOrder")
    suspend fun getAllInstructions(): List<ExerciseInstructionEntity>

    @Query("SELECT * FROM exercise_media ORDER BY exerciseId, sortOrder")
    suspend fun getAllExerciseMedia(): List<ExerciseMediaEntity>

    @Query("SELECT * FROM exercise_source_identities")
    suspend fun getAllSourceIdentities(): List<ExerciseSourceIdentityEntity>

    @Query("SELECT * FROM exercise_secondary_targets")
    suspend fun getAllSecondaryTargets(): List<ExerciseSecondaryTargetEntity>

    @Query("SELECT * FROM exercise_equipment_links")
    suspend fun getAllCanonicalEquipment(): List<ExerciseEquipmentLinkEntity>

    @Query("SELECT name FROM exercises")
    suspend fun getExerciseNames(): List<String>

    @Query(
        """
        SELECT e.* FROM equipment e
        INNER JOIN exercise_equipment ee ON e.id = ee.equipmentId
        WHERE ee.exerciseId = :exerciseId
        ORDER BY e.name
        """,
    )
    fun observeEquipmentForExercise(exerciseId: String): Flow<List<EquipmentEntity>>

    @Query(
        """
        SELECT mg.* FROM muscle_groups mg
        INNER JOIN exercise_secondary_muscles esm ON mg.id = esm.muscleGroupId
        WHERE esm.exerciseId = :exerciseId
        ORDER BY mg.name
        """,
    )
    fun observeSecondaryMusclesForExercise(exerciseId: String): Flow<List<MuscleGroupEntity>>
}