package com.chy.muscletome.data.local.dao

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity

/** Room relation projection used to atomically load the normalized exercise tree. */
data class ExerciseWithCanonicalRelations(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val metadata: CanonicalExerciseEntity?,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val instructions: List<ExerciseInstructionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ExerciseEquipmentLinkEntity::class,
            parentColumn = "exerciseId",
            entityColumn = "equipmentId",
        ),
    )
    val equipment: List<EquipmentEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ExerciseSecondaryTargetEntity::class,
            parentColumn = "exerciseId",
            entityColumn = "muscleGroupId",
        ),
    )
    val secondaryTargets: List<MuscleGroupEntity>,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val media: List<ExerciseMediaEntity>,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val sourceIdentities: List<ExerciseSourceIdentityEntity>,
)
