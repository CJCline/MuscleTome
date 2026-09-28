package com.chy.muscletome.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseCanonicalDao
import com.chy.muscletome.data.local.dao.ExerciseImportReviewDao
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.SelectionHistoryDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import com.chy.muscletome.data.local.entity.ExerciseImportResolutionEntity
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.MuscleVolumeTargetEntity
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.SlotEquipmentFilterCrossRef
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.entity.UserExcludedExerciseCrossRef
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        UserEntity::class,
        MuscleGroupEntity::class,
        EquipmentEntity::class,
        ExerciseEntity::class,
        PendingExerciseImportEntity::class,
        ExerciseImportResolutionEntity::class,
        CanonicalExerciseEntity::class,
        ExerciseMediaEntity::class,
        ExerciseSourceIdentityEntity::class,
        ExerciseInstructionEntity::class,
        ExerciseSecondaryTargetEntity::class,
        ExerciseEquipmentLinkEntity::class,
        RoutineEntity::class,
        RoutineDayEntity::class,
        RoutineSlotEntity::class,
        WorkoutSessionEntity::class,
        SessionSlotResultEntity::class,
        SetLogEntity::class,
        ExerciseSelectionHistoryEntity::class,
        MuscleVolumeTargetEntity::class,
        ExerciseEquipmentCrossRef::class,
        ExerciseSecondaryMuscleCrossRef::class,
        SlotTargetMuscleCrossRef::class,
        SlotEquipmentFilterCrossRef::class,
        UserAvailableEquipmentCrossRef::class,
        UserExcludedExerciseCrossRef::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MuscleTomeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun exerciseCanonicalDao(): ExerciseCanonicalDao
    abstract fun exerciseImportReviewDao(): ExerciseImportReviewDao
    abstract fun catalogDao(): CatalogDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun selectionHistoryDao(): SelectionHistoryDao
}
