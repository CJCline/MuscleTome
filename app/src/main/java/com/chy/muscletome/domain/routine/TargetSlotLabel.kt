package com.chy.muscletome.domain.routine

import com.chy.muscletome.domain.model.TargetMovementType

/**
 * Human label for a TARGET slot, e.g. "Chest isolation" or
 * "Chest · Triceps +1 compound". Pure presentation logic, shared by the
 * Home "Up next" preview and the routine day rows so both surfaces name
 * auto-picked slots identically.
 */
object TargetSlotLabel {

    /** Badge text marking a slot whose exercise is picked at session start. */
    const val AI_PICK = "AI pick"

    fun label(
        muscleNames: List<String>,
        movement: TargetMovementType,
    ): String {
        val kind = when (movement) {
            TargetMovementType.COMPOUND -> " compound"
            TargetMovementType.ISOLATION -> " isolation"
            TargetMovementType.ANY -> ""
        }
        val names = muscleNames.map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            names.isEmpty() -> "Any muscle$kind"
            names.size <= 2 -> names.joinToString(" · ") + kind
            else -> names.take(2).joinToString(" · ") + " +${names.size - 2}" + kind
        }
    }
}
