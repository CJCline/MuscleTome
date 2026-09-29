package com.chy.muscletome.data.media

import com.chy.muscletome.data.media.MediaLicenseGate.isCacheable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5C: license gating for the on-demand media cache. Only licenses that
 * permit redistribution/caching may be fetched; anything unclear stays
 * reference-only.
 */
class ExerciseMediaLicenseTest {
    @Test fun allowlistAcceptsRedistributionLicenses() {
        assertTrue(isCacheable("Unlicense"))
        assertTrue(isCacheable("CC0 1.0"))
        assertTrue(isCacheable("MIT License"))
        assertTrue(isCacheable("Apache-2.0"))
        assertTrue(isCacheable("CC BY 4.0"))
        assertTrue(isCacheable("cc by-sa 4.0"))
        assertTrue(isCacheable("Public Domain"))
    }

    @Test fun unclearOrRestrictiveLicensesAreSkipped() {
        assertFalse(isCacheable(null))
        assertFalse(isCacheable(""))
        assertFalse(isCacheable("   "))
        assertFalse(isCacheable("unknown"))
        // NC/ND variants restrict redistribution → not cacheable.
        assertFalse(isCacheable("CC BY-NC 4.0"))
    }
}
