package com.chy.regimen.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chy.regimen.data.local.dao.CatalogDao
import com.chy.regimen.data.local.dao.RoutineDao
import com.chy.regimen.data.local.dao.UserDao
import com.chy.regimen.data.local.dao.WorkoutDao
import com.chy.regimen.data.local.entity.EquipmentEntity
import com.chy.regimen.data.local.entity.ExerciseEntity
import com.chy.regimen.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.regimen.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.regimen.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.regimen.data.local.entity.MuscleGroupEntity
import com.chy.regimen.data.local.entity.RoutineDayEntity
import com.chy.regimen.data.local.entity.RoutineEntity
import com.chy.regimen.data.local.entity.RoutineSlotEntity
import com.chy.regimen.data.local.entity.SessionSlotResultEntity
import com.chy.regimen.data.local.entity.SetLogEntity
import com.chy.regimen.data.local.entity.SlotEquipmentFilterCrossRef
import com.chy.regimen.data.local.entity.SlotTargetMuscleCrossRef
import com.chy.regimen.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.regimen.data.local.entity.UserEntity
import com.chy.regimen.data.local.entity.UserExcludedExerciseCrossRef
import com.chy.regimen.data.local.entity.WorkoutSessionEntity

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
abstract class RegimenDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun catalogDao(): CatalogDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
}