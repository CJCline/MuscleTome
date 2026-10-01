package com.chy.muscletome.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectOnFocusTextFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectOnFocusTextFieldReplacesValueOnInitialFocus() {
        var text by mutableStateOf("100")
        compose.setContent {
            MaterialTheme {
                SelectOnFocusOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { androidx.compose.material3.Text("Weight") },
                    modifier = Modifier.testTag("weight_field"),
                )
            }
        }

        val node = compose.onNodeWithTag("weight_field")
        node.assertTextEquals("Weight", "100")

        // Initial click to focus populated field, then typing replacement "120"
        node.performClick()
        node.performTextInput("120")

        // Value replaced immediately on focus instead of appending ("100120")
        assertEquals("120", text)
    }

    @Test
    fun metricStepperReplacesValueOnInitialFocus() {
        var stepperValue by mutableStateOf("80.0")
        compose.setContent {
            MaterialTheme {
                MetricStepper(
                    label = "Weight",
                    value = stepperValue,
                    onValueChange = { stepperValue = it },
                    onDelta = {},
                    modifier = Modifier.testTag("stepper"),
                )
            }
        }

        // Stepper starts displaying "80.0"
        val stepperNode = compose.onNodeWithTag("stepper")

        // Click to enter edit mode, then type "90"
        stepperNode.performClick()
        compose.onNodeWithTag("stepper").performTextInput("90")

        assertEquals("90", stepperValue)
    }

    @Test
    fun selectOnFocusTextFieldAllowsMultipleEdits() {
        var text by mutableStateOf("Bench Press")
        compose.setContent {
            MaterialTheme {
                SelectOnFocusOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { androidx.compose.material3.Text("Name") },
                    modifier = Modifier.testTag("name_field"),
                )
            }
        }

        val node = compose.onNodeWithTag("name_field")
        node.performClick()
        node.performTextInput("Incline Press")

        assertEquals("Incline Press", text)
    }
}
