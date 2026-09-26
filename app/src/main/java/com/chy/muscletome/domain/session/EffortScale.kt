package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.model.EffortScale

/**
 * Pure RPE ↔ RIR conversion. RPE is the stored canon; RIR is the complement:
 * RIR = 10 − RPE (RPE 10 = 0 reps in reserve, RPE 7 = 3 in reserve).
 *
 * Extracted so the two scales can never drift apart across UI surfaces —
 * every chip, label, and dialog derives from these functions.
 */
object EffortScales {

    /** RIR for a given RPE; null when RPE is absent. */
    fun rirFor(rpe: Float?): Int? = rpe?.let { (10 - it).toInt() }

    /** RPE for a given integer RIR; null when RIR is absent. */
    fun rpeFor(rir: Int?): Float? = rir?.let { (10 - it).toFloat() }

    /**
     * The chip values offered for a scale: RPE 6–10 vs RIR 0–4 (the mirrored
     * range — anything above RIR 4 reads as RPE below 6 and isn't chip-worthy).
     */
    fun chipValues(scale: EffortScale): List<Int> = when (scale) {
        EffortScale.RPE -> listOf(6, 7, 8, 9, 10)
        EffortScale.RIR -> listOf(0, 1, 2, 3, 4)
    }

    /** Formats a stored RPE on the given scale, e.g. RPE 8 → "RIR 2". */
    fun label(rpe: Float?, scale: EffortScale): String? {
        if (rpe == null) return null
        return when (scale) {
            EffortScale.RPE -> "RPE " + if (rpe % 1f == 0f) rpe.toInt().toString() else rpe.toString()
            EffortScale.RIR -> "RIR " + ((10 - rpe).toInt()).toString()
        }
    }

    /**
     * A stored RPE as bare editable text on the given scale
     * ("8" in RPE mode, "2" in RIR mode); null → "".
     */
    fun toScaleText(rpe: Float?, scale: EffortScale): String {
        if (rpe == null) return ""
        return when (scale) {
            EffortScale.RPE -> if (rpe % 1f == 0f) rpe.toInt().toString() else rpe.toString()
            EffortScale.RIR -> (10 - rpe).toInt().toString()
        }
    }

    /**
     * Parses text typed on the given scale back into RPE canon. Blank or
     * unparsable → null (clearing the target). RIR accepts 0–10; RPE accepts
     * 1–10 (half-points allowed).
     */
    fun fromScaleText(text: String, scale: EffortScale): Float? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return when (scale) {
            EffortScale.RPE -> trimmed.toFloatOrNull()?.takeIf { it in 1f..10f }
            EffortScale.RIR -> trimmed.toIntOrNull()?.takeIf { it in 0..10 }?.let { (10 - it).toFloat() }
        }
    }
}
