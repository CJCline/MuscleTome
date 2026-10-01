package com.chy.muscletome.ui.workout

import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.WeightStep
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.session.WeightUnits
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightStepTest {

    @Test
    fun weightStepEnumValuesAndLabelsAreAccurate() {
        assertEquals(1.0, WeightStep.STEP_1.value, 0.001)
        assertEquals("1 lb", WeightStep.STEP_1.label(WeightUnit.LB))

        assertEquals(2.5, WeightStep.STEP_2_5.value, 0.001)
        assertEquals("2.5 lb", WeightStep.STEP_2_5.label(WeightUnit.LB))

        assertEquals(5.0, WeightStep.STEP_5.value, 0.001)
        assertEquals("5 lb", WeightStep.STEP_5.label(WeightUnit.LB))

        assertEquals(10.0, WeightStep.STEP_10.value, 0.001)
        assertEquals("10 lb", WeightStep.STEP_10.label(WeightUnit.LB))
    }

    @Test
    fun step2_5IsRetainedAndDisplayedAccurately() {
        val user = UserEntity(
            id = "local-user",
            name = "You",
            weightUnit = WeightUnit.LB,
            weightStep = WeightStep.STEP_2_5,
        )

        assertEquals(WeightStep.STEP_2_5, user.weightStep)
        assertEquals(2.5, user.weightStep.value, 0.001)

        val state = ActiveWorkoutUiState(
            weightUnit = user.weightUnit,
            configuredWeightStep = user.weightStep.value,
        )

        assertEquals(2.5, state.weightStep, 0.001)
        assertEquals(1.25, state.weightLongStep, 0.001)
    }

    @Test
    fun bumpWeightWith2_5StepFormatsCleanly() {
        val step = 2.5
        var current = 100.0

        current += step
        assertEquals("102.5", WeightUnits.displayText(current))

        current += step
        assertEquals("105", WeightUnits.displayText(current))

        current -= step
        assertEquals("102.5", WeightUnits.displayText(current))
    }
}
