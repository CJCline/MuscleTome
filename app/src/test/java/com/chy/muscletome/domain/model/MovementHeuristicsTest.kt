package com.chy.muscletome.domain.model

import com.chy.muscletome.data.remote.WgerMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 4: the heuristics are the single implementation of the name-keyword
 * movement rules; the wger adapter must delegate to them so both adapters
 * corroborate identically on cross-source candidates.
 */
class MovementHeuristicsTest {

    @Test
    fun wgerMapperDelegatesToSharedHeuristics() {
        val names = listOf(
            "Romanian Deadlift", "Barbell Back Squat", "Lunge", "Bent Over Row",
            "Pull-up", "Chin-up", "Lat Pulldown", "Bench Press", "Push-up", "Dip",
            "Farmers Walk", "Suitcase Carry", "Cable Fly", "Lateral Raise",
            "Barbell Curl", "Leg Extension", "Tricep Kickback", "Shrug",
            "Standing Calf Raise", "Plank", "Battling Ropes",
        )
        names.forEach { name ->
            assertEquals(name, MovementHeuristics.movementPattern(name), WgerMapper.movementPattern(name, null))
            assertEquals(name, MovementHeuristics.movementType(name), WgerMapper.movementType(name, null))
        }
    }

    @Test
    fun unknownNamesFallBackToOtherPatternAndCompoundType() {
        assertEquals(MovementPattern.OTHER, MovementHeuristics.movementPattern("Something Novel"))
        assertEquals(MovementType.COMPOUND, MovementHeuristics.movementType("Something Novel"))
    }

    @Test
    fun forcelessStaticNamesRelyOnHeuristicsNotGuesses() {
        // FEDB `force` static/null falls back to these rules; a plank stays
        // non-hinge/squat/pull/push/carry and therefore OTHER.
        assertEquals(MovementPattern.OTHER, MovementHeuristics.movementPattern("Plank"))
        // A "static" hold like the stretch-style Plank must not be guessed
        // into a force pattern.
        assertEquals(MovementType.ISOLATION, MovementHeuristics.movementType("Calf Stretch"))
    }
}
