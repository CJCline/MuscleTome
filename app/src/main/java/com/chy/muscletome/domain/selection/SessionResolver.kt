package com.chy.muscletome.domain.selection

import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import javax.inject.Inject

/** One slot of a session, resolved to a concrete exercise. */
data class ResolvedSlot(
    val slotId: String,
    val exerciseId: String,
    val reason: SelectionReason,
)

sealed interface SlotResolution {
    data class Success(val slots: List<ResolvedSlot>) : SlotResolution
    /** The slot that could not be resolved, labeled for the user. */
    data class NoMatch(val slotLabel: String) : SlotResolution
}

/**
 * Pure session-start resolution: turns a day's slots into concrete exercises.
 * FIXED slots pass their exercise through; TARGET slots are picked by the
 * [VarietyEngine] against the user's equipment, exclusions, and history —
 * with per-session duplicate avoidance.
 *
 * Extracted from WorkoutRepository so the selection rules are unit-testable
 * without a database.
 */
class SessionResolver @Inject constructor(
    private val engine: VarietyEngine,
) {

    fun resolve(
        slots: List<RoutineSlotEntity>,
        targetsBySlotId: Map<String, Set<String>>,
        catalog: List<EngineCandidate>,
        muscleGroups: List<MuscleGroupEntity>,
        availableEquipmentIds: Set<String>,
        excludedExerciseIds: Set<String>,
        maxDifficulty: Difficulty,
        matchStrictness: MatchStrictness,
        preferCompoundEarly: Boolean,
    ): SlotResolution {
        val muscleParents = muscleGroups.associate { it.id to it.parentGroupId }
        val resolved = mutableListOf<ResolvedSlot>()
        val picked = mutableSetOf<String>()

        slots.forEachIndexed { index, slot ->
            if (slot.type == SlotType.FIXED) {
                val exerciseId = slot.exerciseId
                    ?: return SlotResolution.NoMatch("Fixed slot missing exercise")
                resolved += ResolvedSlot(slot.id, exerciseId, SelectionReason.FIXED)
                picked += exerciseId
                return@forEachIndexed
            }

            val targets = targetsBySlotId[slot.id].orEmpty()
            val pick = engine.pickWithFallback(
                EngineRequest(
                    targetMuscleIds = targets,
                    targetMovementType = slot.targetMovementType,
                    availableEquipmentIds = availableEquipmentIds,
                    excludedExerciseIds = excludedExerciseIds,
                    alreadyPickedIds = picked,
                    maxDifficulty = maxDifficulty,
                    matchStrictness = matchStrictness,
                    preferCompoundEarly = preferCompoundEarly,
                    slotIndex = index,
                ),
                catalog,
                muscleParents,
            )

            if (pick == null) {
                val muscleName = targets.firstOrNull()?.let { targetId ->
                    muscleGroups.find { it.id == targetId }?.name
                } ?: "Target"
                return SlotResolution.NoMatch("$muscleName (${slot.targetMovementType})")
            }
            resolved += ResolvedSlot(slot.id, pick.exercise.id, SelectionReason.AI_ROTATED)
            picked += pick.exercise.id
        }
        return SlotResolution.Success(resolved)
    }
}
