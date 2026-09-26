package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.model.WeightUnit
import kotlin.math.abs

/**
 * Barbell plate math: how to load a target weight from a standard inventory.
 *
 * Greedy per-side breakdown (largest plate first) — how lifters actually
 * load. The leftover is the per-side gap the inventory can't hit; [Breakdown]
 * also carries the nearest *loadable* total so the UI can offer to snap the
 * entry field to it.
 */
object PlateMath {

    /** One plate denomination and how many go on each side. */
    data class PlatePair(val weight: Double, val countPerSide: Int)

    /** How to load [targetWeight] total (bar + plates on both sides). */
    data class Breakdown(
        val barWeight: Double,
        val targetWeight: Double,
        val unit: WeightUnit,
        val plates: List<PlatePair>,
        /** Bar + every plate — the nearest weight this bar/inventory hits. */
        val loadableWeight: Double,
        /** Per-side gap with no plate for it (negative: target under bar). */
        val leftoverPerSide: Double,
    ) {
        val isAchievable: Boolean get() = abs(leftoverPerSide) <= EPSILON

        /** Per-side plates as text, e.g. "2 × 25 + 10". */
        val perSideLabel: String
            get() = plates.joinToString(" + ") { pair ->
                if (pair.countPerSide == 1) {
                    WeightUnits.displayText(pair.weight)
                } else {
                    "${pair.countPerSide} × ${WeightUnits.displayText(pair.weight)}"
                }
            }

        private companion object {
            const val EPSILON = 1e-9
        }
    }

    /** Standard per-side denominations, largest first. */
    fun platesFor(unit: WeightUnit): List<Double> = when (unit) {
        WeightUnit.KG -> listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        WeightUnit.LB -> listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)
    }

    /** Typical barbell options: kg-calibrated vs lb-calibrated plates. */
    fun barsFor(unit: WeightUnit): List<Double> = when (unit) {
        WeightUnit.KG -> listOf(20.0, 15.0, 10.0)
        WeightUnit.LB -> listOf(45.0, 35.0, 15.0)
    }

    fun defaultBar(unit: WeightUnit): Double = barsFor(unit).first()

    fun breakdown(targetWeight: Double, barWeight: Double, unit: WeightUnit): Breakdown {
        val plates = mutableListOf<PlatePair>()
        var remainingPerSide = (targetWeight - barWeight) / 2.0
        for (plate in platesFor(unit)) {
            if (plate > (remainingPerSide + EPSILON)) continue
            val count = (remainingPerSide / plate).toInt()
            plates += PlatePair(plate, count)
            remainingPerSide -= count * plate
        }
        val perSideLoaded = plates.sumOf { it.weight * it.countPerSide }
        return Breakdown(
            barWeight = barWeight,
            targetWeight = targetWeight,
            unit = unit,
            plates = plates,
            loadableWeight = barWeight + 2.0 * perSideLoaded,
            leftoverPerSide = remainingPerSide,
        )
    }

    private const val EPSILON = 1e-9
}
