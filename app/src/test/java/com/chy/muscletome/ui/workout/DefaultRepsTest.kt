package com.chy.muscletome.ui.workout

import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.domain.model.DefaultRepPreference
import com.chy.muscletome.domain.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultRepsTest {

    private fun createSlot(min: Int, max: Int): RoutineSlotEntity {
        return RoutineSlotEntity(
            id = "slot-1",
            routineDayId = "day-1",
            orderIndex = 0,
            type = SlotType.FIXED,
            exerciseId = "squat",
            sets = 3,
            repRangeMin = min,
            repRangeMax = max,
            restSeconds = 90,
        )
    }

    @Test
    fun minimumPreference_returnsMinBound() {
        val slot = createSlot(min = 8, max = 12)
        val result = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MINIMUM)
        assertEquals(8, result)
    }

    @Test
    fun maximumPreference_returnsMaxBound() {
        val slot = createSlot(min = 8, max = 12)
        val result = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MAXIMUM)
        assertEquals(12, result)
    }

    @Test
    fun equalBounds_returnsSameValue_forMinimumAndMaximum() {
        val slot = createSlot(min = 10, max = 10)
        val resultMin = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MINIMUM)
        val resultMax = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MAXIMUM)
        assertEquals(10, resultMin)
        assertEquals(10, resultMax)
    }

    @Test
    fun missingSlot_returnsStandardMinOrMaxDefault() {
        val resultMin = ActiveWorkoutViewModel.calculateDefaultReps(null, DefaultRepPreference.MINIMUM)
        val resultMax = ActiveWorkoutViewModel.calculateDefaultReps(null, DefaultRepPreference.MAXIMUM)
        assertEquals(8, resultMin)
        assertEquals(12, resultMax)
    }

    @Test
    fun missingMinBound_fallsBackToMaxBound() {
        val slot = createSlot(min = 0, max = 15)
        val resultMin = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MINIMUM)
        val resultMax = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MAXIMUM)
        assertEquals(15, resultMin)
        assertEquals(15, resultMax)
    }

    @Test
    fun missingMaxBound_fallsBackToMinBound() {
        val slot = createSlot(min = 6, max = 0)
        val resultMin = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MINIMUM)
        val resultMax = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MAXIMUM)
        assertEquals(6, resultMin)
        assertEquals(6, resultMax)
    }

    @Test
    fun missingBothBounds_returnsFallbackDefault() {
        val slot = createSlot(min = 0, max = 0)
        val resultMin = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MINIMUM)
        val resultMax = ActiveWorkoutViewModel.calculateDefaultReps(slot, DefaultRepPreference.MAXIMUM)
        assertEquals(8, resultMin)
        assertEquals(12, resultMax)
    }
}
