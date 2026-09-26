package com.chy.muscletome.data.backup

import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
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

/**
 * The on-disk backup format. Version it explicitly: unknown future fields
 * must not crash older readers, and import must be able to reject formats it
 * doesn't understand.
 */
@Serializable
data class BackupDocument(
    /** Backup format version — bump on breaking changes, import rejects higher. */
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
    val routines: List<RoutineEntity>,
    val routineDays: List<RoutineDayEntity>,
    val routineSlots: List<RoutineSlotEntity>,
    val slotTargets: List<SlotTargetMuscleCrossRef>,
    val sessions: List<WorkoutSessionEntityDto>,
    val slotResults: List<SessionSlotResultEntity>,
    val setLogs: List<SetLogEntity>,
) {
    companion object {
        /**
         * v2: superset/circuit support — routineSlots.supersetGroupId and
         * session_slot_results' supersetGroupId/sortOrder/plannedSets. The
         * new fields are optional-with-defaults, so v1 documents still
         * import unchanged; v2 is REJECTED by v1-era builds (import checks
         * `formatVersion <= FORMAT_VERSION`), which is exactly what we want:
         * supersets must not silently drop on an old device.
         */
        const val FORMAT_VERSION = 2
    }
}

/**
 * The plain WorkoutSessionEntity is used at the DB layer; the DTO mirrors it
 * for the backup document so the format is stable against entity renames.
 */
@Serializable
data class WorkoutSessionEntityDto(
    val id: String,
    val userId: String,
    val routineDayId: String?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
)
