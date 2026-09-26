package com.chy.muscletome.domain.session

import com.chy.muscletome.domain.model.EffortScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RPE ↔ RIR conversion matrix. */
class EffortScalesTest {

    @Test
    fun rirIsTheComplementOfRpe() {
        assertEquals(0, EffortScales.rirFor(10f))
        assertEquals(1, EffortScales.rirFor(9f))
        assertEquals(2, EffortScales.rirFor(8f))
        assertEquals(3, EffortScales.rirFor(7f))
        assertEquals(4, EffortScales.rirFor(6f))
        // Half-points round down to whole RIR (7.5 → 2).
        assertEquals(2, EffortScales.rirFor(7.5f))
        assertNull(EffortScales.rirFor(null))
    }

    @Test
    fun rpeIsTheComplementOfRir() {
        assertEquals(10f, EffortScales.rpeFor(0))
        assertEquals(8f, EffortScales.rpeFor(2))
        assertEquals(6f, EffortScales.rpeFor(4))
        assertNull(EffortScales.rpeFor(null))
    }

    @Test
    fun chipRangesMirrorEachOther() {
        assertEquals(listOf(6, 7, 8, 9, 10), EffortScales.chipValues(EffortScale.RPE))
        assertEquals(listOf(0, 1, 2, 3, 4), EffortScales.chipValues(EffortScale.RIR))
    }

    @Test
    fun labelsRoundTripBothWays() {
        assertEquals("RPE 8", EffortScales.label(8f, EffortScale.RPE))
        assertEquals("RPE 7.5", EffortScales.label(7.5f, EffortScale.RPE))
        assertEquals("RIR 2", EffortScales.label(8f, EffortScale.RIR))
        assertNull(EffortScales.label(null, EffortScale.RPE))
    }
}
