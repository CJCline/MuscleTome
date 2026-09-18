package com.chy.muscletome.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
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
        RoutineEntity::class,
        RoutineDayEntity::class,
        RoutineSlotEntity::class,
        WorkoutSessionEntity::class,
        SessionSlotResultEntity::class,
        SetLogEntity::class,
        ExerciseSelectionHistoryEntity::class,
        ExerciseEquipmentCrossRef::class,
        ExerciseSecondaryMuscleCrossRef::class,
        SlotTargetMuscleCrossRef::class,
        SlotEquipmentFilterCrossRef::class,
        UserAvailableEquipmentCrossRef::class,
        UserExcludedExerciseCrossRef::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MuscleTomeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun catalogDao(): CatalogDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
}
