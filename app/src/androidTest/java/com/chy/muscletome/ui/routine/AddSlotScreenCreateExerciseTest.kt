package com.chy.muscletome.ui.routine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddSlotScreenCreateExerciseTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun addSlotScreenShowsCreateExerciseOption() {
        var createExerciseCalled by mutableStateOf(false)

        compose.setContent {
            MaterialTheme {
                AddSlotScreen(
                    onBack = {},
                    onAddExercise = { createExerciseCalled = true },
                )
            }
        }

        // Action button "New exercise" in top app bar should be displayed
        val topBarButton = compose.onNodeWithText("New exercise")
        topBarButton.assertIsDisplayed()

        topBarButton.performClick()
        assertTrue(createExerciseCalled)
    }
}
