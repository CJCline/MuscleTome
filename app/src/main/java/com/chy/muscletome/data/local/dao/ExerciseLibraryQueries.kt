package com.chy.muscletome.data.local.dao

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity

data class ExerciseLibraryRow(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val metadata: CanonicalExerciseEntity?,
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
)
