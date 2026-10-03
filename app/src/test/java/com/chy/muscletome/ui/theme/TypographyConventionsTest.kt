package com.chy.muscletome.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyConventionsTest {

    @Test
    fun formatWeight_alwaysFormatsToSingleDecimalPlace() {
        assertEquals("225.0", MuscleTomeTextStyles.formatWeight(225.0))
        assertEquals("225.5", MuscleTomeTextStyles.formatWeight(225.5))
        assertEquals("0.0", MuscleTomeTextStyles.formatWeight(0.0))
        assertEquals("0.0", MuscleTomeTextStyles.formatWeight(null as Double?))
        assertEquals("135.0", MuscleTomeTextStyles.formatWeight(135.0f))
    }

    @Test
    fun formatBracket_formatsUppercaseSquareBracketTag() {
        assertEquals("[PR]", MuscleTomeTextStyles.formatBracket("pr"))
        assertEquals("[CUSTOM]", MuscleTomeTextStyles.formatBracket("[custom]"))
        assertEquals("[ACTIVE]", MuscleTomeTextStyles.formatBracket(" active "))
        assertEquals("[WARMUP]", MuscleTomeTextStyles.formatBracket("[WARMUP]"))
    }

    @Test
    fun textStyles_enforceMonospaceDataValuesAndWideTracking() {
        assertEquals(FontFamily.Monospace, MuscleTomeTextStyles.dataValue.fontFamily)
        assertEquals(FontFamily.Monospace, MuscleTomeTextStyles.dataBracket.fontFamily)

        assertTrue(MuscleTomeTextStyles.heading.letterSpacing.value >= 1.0f)
        assertTrue(MuscleTomeTextStyles.sectionTitle.letterSpacing.value >= 1.5f)
        assertTrue(MuscleTomeTextStyles.label.letterSpacing.value >= 1.5f)
        assertTrue(MuscleTomeTextStyles.tag.letterSpacing.value >= 1.0f)
        assertTrue(MuscleTomeTextStyles.button.letterSpacing.value >= 1.0f)
    }
}
