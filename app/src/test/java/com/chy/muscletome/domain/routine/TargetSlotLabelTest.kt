package com.chy.muscletome.domain.routine

import com.chy.muscletome.domain.model.TargetMovementType
import org.junit.Assert.assertEquals
import org.junit.Test

/** TARGET slots must read as real training labels, not "Target slot". */
class TargetSlotLabelTest {

    @Test
    fun singleMuscleWithMovementReadsNaturally() {
        assertEquals(
            "Chest isolation",
            TargetSlotLabel.label(listOf("Chest"), TargetMovementType.ISOLATION),
        )
        assertEquals(
            "Back compound",
            TargetSlotLabel.label(listOf("Back"), TargetMovementType.COMPOUND),
        )
    }

    @Test
    fun anyMovementDropsTheKindWord() {
        assertEquals(
            "Chest · Triceps",
            TargetSlotLabel.label(listOf("Chest", "Triceps"), TargetMovementType.ANY),
        )
    }

    @Test
    fun manyMusclesCollapseWithACount() {
        assertEquals(
            "Chest · Triceps +1 isolation",
            TargetSlotLabel.label(
                listOf("Chest", "Triceps", "Shoulders"),
                TargetMovementType.ISOLATION,
            ),
        )
    }

    @Test
    fun blankMusclesFallBackToAny() {
        assertEquals(
            "Any muscle",
            TargetSlotLabel.label(listOf("", "  "), TargetMovementType.ANY),
        )
        assertEquals(
            "Any muscle compound",
            TargetSlotLabel.label(emptyList(), TargetMovementType.COMPOUND),
        )
    }
}
