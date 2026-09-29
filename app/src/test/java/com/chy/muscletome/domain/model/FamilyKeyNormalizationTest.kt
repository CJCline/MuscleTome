package com.chy.muscletome.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 5A: the single shared family-name normalizer used for duplicate
 * rejection when creating custom movement families.
 */
class FamilyKeyNormalizationTest {
    @Test fun normalizesLikeImportNameMatching() {
        assertEquals("bench press", MovementFamilies.normalizeKey("  Bench  Press "))
        assertEquals("bench press", MovementFamilies.normalizeKey("BENCH-PRESS!"))
        assertEquals("squat", MovementFamilies.normalizeKey("Squat"))
        assertEquals("hip thrust", MovementFamilies.normalizeKey("Hip_Thrust"))
    }

    @Test fun legacyFamilyLabelsNormalizeToTheirIds() {
        // Seed rows use normalizeKey(label) — these must stay collision-free
        // against the legacy IDs they coexist with.
        MovementFamilies.all.forEach { family ->
            val key = MovementFamilies.normalizeKey(family.label)
            assertEquals(family.id, key.replace(' ', '_'))
        }
    }

    @Test fun blankOrPunctuationOnlyNamesAreRejectedByNormalizerContract() {
        assertEquals("", MovementFamilies.normalizeKey("   "))
        assertEquals("", MovementFamilies.normalizeKey("!!! ---"))
    }
}
