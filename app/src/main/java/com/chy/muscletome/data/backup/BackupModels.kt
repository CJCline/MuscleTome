package com.chy.muscletome.data.backup

import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.MuscleVolumeTargetEntity
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.entity.UserExcludedExerciseCrossRef
import kotlinx.serialization.Serializable

@Serializable
data class BackupDocument(
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String? = null,
    val exportedAtEpochMs: Long,
    val user: UserEntity,
    val muscleGroups: List<MuscleGroupEntity>,
    val equipment: List<EquipmentEntity>,
    val exercises: List<ExerciseEntity>,
    val exerciseEquipment: List<ExerciseEquipmentCrossRef>,
    val secondaryMuscles: List<ExerciseSecondaryMuscleCrossRef>,
    val availableEquipment: List<UserAvailableEquipmentCrossRef>,
    val excludedExercises: List<UserExcludedExerciseCrossRef>,
    val volumeTargets: List<MuscleVolumeTargetEntity> = emptyList(),
    val routines: List<RoutineEntity>,
    val routineDays: List<RoutineDayEntity>,
    val routineSlots: List<RoutineSlotEntity>,
    val slotTargets: List<SlotTargetMuscleCrossRef>,
    val sessions: List<WorkoutSessionEntityDto>,
    val slotResults: List<SessionSlotResultEntity>,
    val setLogs: List<SetLogEntity>,
    val selectionHistory: List<ExerciseSelectionHistoryEntity> = emptyList(),
    val canonicalExerciseMetadata: List<CanonicalExerciseEntity> = emptyList(),
    val canonicalInstructions: List<ExerciseInstructionEntity> = emptyList(),
    val canonicalEquipment: List<ExerciseEquipmentCrossRef> = emptyList(),
    val canonicalSecondaryTargets: List<ExerciseSecondaryTargetEntity> = emptyList(),
    val exerciseMedia: List<ExerciseMediaEntity> = emptyList(),
    val exerciseSourceIdentities: List<ExerciseSourceIdentityEntity> = emptyList(),
) {
    companion object {
        const val FORMAT_VERSION = 5
    }
}

internal fun requireSupportedBackupVersion(formatVersion: Int) {
    check(formatVersion <= BackupDocument.FORMAT_VERSION) {
        "Backup format $formatVersion is newer than this app supports " +
            "(${BackupDocument.FORMAT_VERSION}). Update the app first."
    }
}

@Serializable
data class WorkoutSessionEntityDto(
    val id: String,
    val userId: String,
    val routineDayId: String?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
)
