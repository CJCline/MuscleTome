package com.chy.muscletome.ui.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole-number and decimal filtering for the day-editor metric fields
 * (sets, rep min/max, rest, target effort). These helpers back
 * `SlotMetricField`, whose local state lets edits persist during
 * recomposition; the tests pin the accepted text shapes.
 */
class DayDetailDraftTest {

    // --- isValidSlotFieldText ---

    @Test fun emptyIsAlwaysValidSoClearingWorks() {
        assertTrue(isValidSlotFieldText("", allowDecimal = false))
        assertTrue(isValidSlotFieldText("", allowDecimal = true))
    }

    @Test fun wholeNumbersAcceptDigitsUpToFour() {
        assertTrue(isValidSlotFieldText("3", allowDecimal = false))
        assertTrue(isValidSlotFieldText("12", allowDecimal = false))
        assertTrue(isValidSlotFieldText("9999", allowDecimal = false))
        assertFalse(isValidSlotFieldText("12345", allowDecimal = false))
    }

    @Test fun lettersAndSymbolsRejected() {
        assertFalse(isValidSlotFieldText("a", allowDecimal = false))
        assertFalse(isValidSlotFieldText("12x", allowDecimal = false))
        assertFalse(isValidSlotFieldText("1.5", allowDecimal = false))
        assertFalse(isValidSlotFieldText("-3", allowDecimal = false))
        assertFalse(isValidSlotFieldText(" ", allowDecimal = false))
    }

    @Test fun decimalAcceptsRpeShapes() {
        assertTrue(isValidSlotFieldText("7", allowDecimal = true))
        assertTrue(isValidSlotFieldText("7.", allowDecimal = true))
        assertTrue(isValidSlotFieldText("7.5", allowDecimal = true))
        assertTrue(isValidSlotFieldText("10.0", allowDecimal = true))
    }

    @Test fun decimalRejectsTooManyDigitsOrPlaces() {
        assertFalse(isValidSlotFieldText("123", allowDecimal = true))
        assertFalse(isValidSlotFieldText("7.55", allowDecimal = true))
        assertFalse(isValidSlotFieldText(".5", allowDecimal = true))
        assertFalse(isValidSlotFieldText("7.5.", allowDecimal = true))
    }

    // --- applySlotFieldChange ---

    @Test fun appendKeepsTypedText() {
        assertEquals(
            "10",
            applySlotFieldChange(oldText = "1", typedText = "10", allowDecimal = false),
        )
    }

    @Test fun backspaceKeepsTheShorterText() {
        assertEquals(
            "1",
            applySlotFieldChange(oldText = "12", typedText = "1", allowDecimal = false),
        )
    }

    @Test fun clearingTheFieldIsApplied() {
        assertEquals(
            "",
            applySlotFieldChange(oldText = "8", typedText = "", allowDecimal = false),
        )
    }

    @Test fun replacingTheSelectionIsApplied() {
        // Select-all on focus then typing replaces the whole value.
        assertEquals(
            "5",
            applySlotFieldChange(oldText = "12", typedText = "5", allowDecimal = false),
        )
    }

    @Test fun invalidKeystrokeKeepsOldText() {
        assertEquals(
            "12",
            applySlotFieldChange(oldText = "12", typedText = "12a", allowDecimal = false),
        )
    }

    @Test fun leadingPeriodIsDroppedForDecimals() {
        assertEquals(
            "5",
            applySlotFieldChange(oldText = "", typedText = ".5", allowDecimal = true),
        )
    }

    @Test fun secondPeriodIsDroppedForDecimals() {
        assertEquals(
            "7.5",
            applySlotFieldChange(oldText = "7.5", typedText = "7.5.", allowDecimal = true),
        )
    }

    @Test fun wholeNumberFieldsIgnorePeriods() {
        assertEquals(
            "8",
            applySlotFieldChange(oldText = "8", typedText = "8.", allowDecimal = false),
        )
    }

    @Test fun replacingRepMinWhenEqualsRepMax() {
        // Test replacing rep min when rep min equals rep max (e.g. repMin="8", repMax="8")
        val oldMin = "8"
        val typedNewMin = "5"
        val result = applySlotFieldChange(oldText = oldMin, typedText = typedNewMin, allowDecimal = false)
        assertEquals("5", result)
    }

    @Test fun clearingFieldThenTypingNewValue() {
        val cleared = applySlotFieldChange(oldText = "90", typedText = "", allowDecimal = false)
        assertEquals("", cleared)
        val retyped = applySlotFieldChange(oldText = "", typedText = "60", allowDecimal = false)
        assertEquals("60", retyped)
    }
}
