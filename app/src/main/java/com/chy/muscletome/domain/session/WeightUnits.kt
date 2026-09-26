package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.model.WeightUnit
import kotlin.math.roundToInt

/**
 * Pure weight-unit conversion and display formatting.
 *
 * Storage canon: set weights are stored in the user's *current* unit; the
 * preference toggle converts history in place (see UserRepository). Display
 * surfaces therefore show raw stored values with a suffix from here — nothing
 * converts at read time, so the two units can never disagree about what a
 * logged number means.
 */
object WeightUnits {

    /** Pounds per kilogram — the exact international definition. */
    const val LB_PER_KG = 2.2046226218

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG

    fun lbToKg(lb: Double): Double = lb / LB_PER_KG

    /** Converts [weight] between units; [from] == [to] is a no-op. */
    fun convert(weight: Double, from: WeightUnit, to: WeightUnit): Double {
        if (from == to) return weight
        return if (from == WeightUnit.KG) kgToLb(weight) else lbToKg(weight)
    }

    /** The opposite unit — what a unit toggle switches to. */
    fun other(unit: WeightUnit): WeightUnit = when (unit) {
        WeightUnit.KG -> WeightUnit.LB
        WeightUnit.LB -> WeightUnit.KG
    }

    /** Bare unit suffix: " kg" / " lb" (leading space for direct append). */
    fun suffix(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> " kg"
        WeightUnit.LB -> " lb"
    }

    /** A stored weight as display text with suffix ("100 kg", "87.5 lb"). */
    fun display(weight: Double, unit: WeightUnit): String = trimmed(weight) + suffix(unit)

    /**
     * A weight as bare field text: float noise trimmed so converted values
     * (220.46224276) read cleanly in entry fields ("220.46").
     */
    fun displayText(weight: Double): String = trimmed(weight)

    /** Rounds to 2dp and drops a trailing ".0" (100.0 → "100"). */
    private fun trimmed(weight: Double): String {
        val rounded = (weight * 100.0).roundToInt().toDouble() / 100.0
        return if ((rounded % 1.0) == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
