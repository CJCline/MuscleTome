package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** KG ↔ LB conversion and display formatting. */
class WeightUnitsTest {

    @Test
    fun conversionRoundTrips() {
        val kgValues = listOf(0.0, 1.0, 2.5, 20.0, 87.5, 100.0, 250.0)
        for (kg in kgValues) {
            assertEquals(
                "round trip failed for $kg",
                kg,
                WeightUnits.lbToKg(WeightUnits.kgToLb(kg)),
                1e-9,
            )
        }
    }

    @Test
    fun factorMatchesTheInternationalDefinition() {
        assertEquals(2.2046226218, WeightUnits.kgToLb(1.0), 1e-12)
        assertEquals(100.0, WeightUnits.lbToKg(220.46226218), 1e-6)
    }

    @Test
    fun convertHonorsTheUnitPair() {
        assertEquals(100.0, WeightUnits.convert(100.0, WeightUnit.KG, WeightUnit.KG), 0.0)
        assertEquals(220.46226218, WeightUnits.convert(100.0, WeightUnit.KG, WeightUnit.LB), 1e-9)
        assertEquals(100.0, WeightUnits.convert(220.46226218, WeightUnit.LB, WeightUnit.KG), 1e-9)
    }

    @Test
    fun otherIsTheOppositeUnit() {
        assertEquals(WeightUnit.LB, WeightUnits.other(WeightUnit.KG))
        assertEquals(WeightUnit.KG, WeightUnits.other(WeightUnit.LB))
    }

    @Test
    fun suffixesReadNaturally() {
        assertEquals(" kg", WeightUnits.suffix(WeightUnit.KG))
        assertEquals(" lb", WeightUnits.suffix(WeightUnit.LB))
        assertEquals("100 kg", WeightUnits.display(100.0, WeightUnit.KG))
        assertEquals("87.5 lb", WeightUnits.display(87.5, WeightUnit.LB))
    }

    @Test
    fun displayTrimsFloatNoise() {
        assertEquals("220.46 lb", WeightUnits.display(220.46224276, WeightUnit.LB))
        assertEquals("100 kg", WeightUnits.display(100.0, WeightUnit.KG))
        assertEquals("100", WeightUnits.displayText(100.0))
        assertEquals("222.96", WeightUnits.displayText(222.95999999999998))
    }
}
