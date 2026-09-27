package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExerciseCanonicalDao {
    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    abstract suspend fun getWithRelations(id: String): ExerciseWithCanonicalRelations?

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    abstract fun observeWithRelations(id: String): Flow<ExerciseWithCanonicalRelations?>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    protected abstract suspend fun getExerciseRow(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise_source_identities WHERE sourceKey = :sourceKey AND externalExerciseId = :externalId")
    abstract suspend fun findSourceIdentity(sourceKey: String, externalId: String): ExerciseSourceIdentityEntity?

    @Query("DELETE FROM exercise_media WHERE exerciseId = :exerciseId")
    protected abstract suspend fun deleteMedia(exerciseId: String)

    @Query("DELETE FROM exercise_source_identities WHERE exerciseId = :exerciseId")
    protected abstract suspend fun deleteSourceIdentities(exerciseId: String)

    @Query("DELETE FROM exercise_secondary_targets WHERE exerciseId = :exerciseId")
    protected abstract suspend fun deleteSecondaryTargets(exerciseId: String)

    @Query("DELETE FROM exercise_equipment WHERE exerciseId = :exerciseId")
    protected abstract suspend fun deleteEquipmentLinks(exerciseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertExercise(row: ExerciseEntity)

    @Update
    protected abstract suspend fun updateExercise(row: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMedia(rows: List<ExerciseMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSourceIdentities(rows: List<ExerciseSourceIdentityEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSecondaryTargets(rows: List<ExerciseSecondaryTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEquipmentLinks(rows: List<ExerciseEquipmentLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEquipment(rows: List<EquipmentEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMuscles(rows: List<MuscleGroupEntity>)

    @Transaction
    open suspend fun insertCanonical(
        exercise: ExerciseEntity,
        equipment: List<EquipmentEntity>,
        equipmentLinks: List<ExerciseEquipmentLinkEntity>,
        secondaryTargets: List<ExerciseSecondaryTargetEntity>,
        media: List<ExerciseMediaEntity>,
        sourceIdentities: List<ExerciseSourceIdentityEntity>,
    ) {
        insertMuscles(emptyList())
        insertEquipment(equipment)
        insertExercise(exercise)
        replaceOwnedRelations(exercise.id, equipmentLinks, secondaryTargets, media, sourceIdentities)
    }

    @Transaction
    open suspend fun updateCanonical(
        exercise: ExerciseEntity,
        equipment: List<EquipmentEntity>,
        equipmentLinks: List<ExerciseEquipmentLinkEntity>,
        secondaryTargets: List<ExerciseSecondaryTargetEntity>,
        media: List<ExerciseMediaEntity>,
        sourceIdentities: List<ExerciseSourceIdentityEntity>,
    ) {
        updateExercise(exercise)
        insertEquipment(equipment)
        replaceOwnedRelations(exercise.id, equipmentLinks, secondaryTargets, media, sourceIdentities)
    }

    private suspend fun replaceOwnedRelations(
        exerciseId: String,
        equipmentLinks: List<ExerciseEquipmentLinkEntity>,
        secondaryTargets: List<ExerciseSecondaryTargetEntity>,
        media: List<ExerciseMediaEntity>,
        sourceIdentities: List<ExerciseSourceIdentityEntity>,
    ) {
        deleteEquipmentLinks(exerciseId)
        deleteSecondaryTargets(exerciseId)
        deleteMedia(exerciseId)
        deleteSourceIdentities(exerciseId)
        insertEquipmentLinks(equipmentLinks.filter { it.exerciseId == exerciseId })
        insertSecondaryTargets(secondaryTargets.filter { it.exerciseId == exerciseId })
        insertMedia(media.filter { it.exerciseId == exerciseId })
        insertSourceIdentities(sourceIdentities.filter { it.exerciseId == exerciseId })
    }
}
