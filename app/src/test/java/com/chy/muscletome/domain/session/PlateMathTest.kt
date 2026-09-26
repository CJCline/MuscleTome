package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Greedy per-side plate breakdown. */
class PlateMathTest {

    @Test
    fun oneHundredKgLoadsTwentyFiveAndFifteenPerSide() {
        val breakdown = PlateMath.breakdown(targetWeight = 100.0, barWeight = 20.0, unit = WeightUnit.KG)
        assertEquals(
            listOf(PlateMath.PlatePair(25.0, 1), PlateMath.PlatePair(15.0, 1)),
            breakdown.plates,
        )
        assertTrue(breakdown.isAchievable)
        assertEquals(100.0, breakdown.loadableWeight, 1e-9)
    }

    @Test
    fun fractionalTargetsUseSmallestPlates() {
        // 102.5 kg on a 20 kg bar → 41.25 per side → 25 + 15 + 1.25.
        val breakdown = PlateMath.breakdown(targetWeight = 102.5, barWeight = 20.0, unit = WeightUnit.KG)
        assertEquals(
            listOf(
                PlateMath.PlatePair(25.0, 1),
                PlateMath.PlatePair(15.0, 1),
                PlateMath.PlatePair(1.25, 1),
            ),
            breakdown.plates,
        )
        assertTrue(breakdown.isAchievable)
    }

    @Test
    fun gapsBelowSmallestPlateAreReported() {
        // 61.25 kg on a 20 kg bar → 20.625 per side → 20 + 0.625 with no plate.
        val breakdown = PlateMath.breakdown(targetWeight = 61.25, barWeight = 20.0, unit = WeightUnit.KG)
        assertFalse(breakdown.isAchievable)
        assertEquals(0.625, breakdown.leftoverPerSide, 1e-9)
        assertEquals(60.0, breakdown.loadableWeight, 1e-9)
    }

    @Test
    fun targetBelowTheBarIsNotAchievable() {
        val breakdown = PlateMath.breakdown(targetWeight = 15.0, barWeight = 20.0, unit = WeightUnit.KG)
        assertFalse(breakdown.isAchievable)
        assertTrue(breakdown.plates.isEmpty())
        assertEquals(20.0, breakdown.loadableWeight, 1e-9)
    }

    @Test
    fun bareBarLoadsNoPlates() {
        val breakdown = PlateMath.breakdown(targetWeight = 20.0, barWeight = 20.0, unit = WeightUnit.KG)
        assertTrue(breakdown.isAchievable)
        assertTrue(breakdown.plates.isEmpty())
        assertEquals(20.0, breakdown.loadableWeight, 1e-9)
        assertEquals("Just the bar", true, breakdown.plates.isEmpty() && breakdown.isAchievable)
    }

    @Test
    fun poundsUseTheLbInventory() {
        // 225 lb on a 45 lb bar → two 45s per side.
        val breakdown = PlateMath.breakdown(targetWeight = 225.0, barWeight = 45.0, unit = WeightUnit.LB)
        assertEquals(listOf(PlateMath.PlatePair(45.0, 2)), breakdown.plates)
        assertTrue(breakdown.isAchievable)
        assertEquals(225.0, breakdown.loadableWeight, 1e-9)
    }

    @Test
    fun inventoriesFollowTheUnit() {
        assertEquals(listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25), PlateMath.platesFor(WeightUnit.KG))
        assertEquals(listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25), PlateMath.platesFor(WeightUnit.LB))
        assertEquals(listOf(20.0, 15.0, 10.0), PlateMath.barsFor(WeightUnit.KG))
        assertEquals(listOf(45.0, 35.0, 15.0), PlateMath.barsFor(WeightUnit.LB))
        assertEquals(20.0, PlateMath.defaultBar(WeightUnit.KG), 0.0)
        assertEquals(45.0, PlateMath.defaultBar(WeightUnit.LB), 0.0)
    }

    @Test
    fun perSideLabelReadsNaturally() {
        // 140 kg on a 20 kg bar → 60 per side → 2 × 25 + 10.
        val breakdown = PlateMath.breakdown(targetWeight = 140.0, barWeight = 20.0, unit = WeightUnit.KG)
        assertEquals("2 × 25 + 10", breakdown.perSideLabel)
    }
}
