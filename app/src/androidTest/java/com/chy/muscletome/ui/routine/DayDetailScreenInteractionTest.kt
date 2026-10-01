package com.chy.muscletome.ui.routine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DayDetailScreenInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun slotMetricFieldReplacesValueOnInitialFocus() {
        var currentText by mutableStateOf("8")
        compose.setContent {
            MaterialTheme {
                SlotMetricField(
                    label = "Rep min",
                    value = currentText,
                    isValueExternal = false,
                    onValueChange = { currentText = it },
                    modifier = Modifier.testTag("rep_min"),
                )
            }
        }

        val node = compose.onNodeWithTag("rep_min")
        node.assertTextEquals("Rep min", "8")

        // Tapping field to focus it and then typing "5"
        node.performClick()
        node.performTextInput("5")

        // Input replaces "8" with "5" instead of appending ("85") or erasing
        assertEquals("5", currentText)
    }

    @Test
    fun slotMetricFieldClearingAndReTyping() {
        var currentText by mutableStateOf("12")
        compose.setContent {
            MaterialTheme {
                SlotMetricField(
                    label = "Rep max",
                    value = currentText,
                    isValueExternal = false,
                    onValueChange = { currentText = it },
                    modifier = Modifier.testTag("rep_max"),
                )
            }
        }

        val node = compose.onNodeWithTag("rep_max")
        node.performClick()
        node.performTextClearance()

        assertEquals("", currentText)

        node.performTextInput("15")
        assertEquals("15", currentText)
    }

    @Test
    fun slotMetricFieldSwitchingFieldsPreservesState() {
        var minText by mutableStateOf("8")
        var maxText by mutableStateOf("12")

        compose.setContent {
            MaterialTheme {
                SlotMetricField(
                    label = "Rep min",
                    value = minText,
                    isValueExternal = false,
                    onValueChange = { minText = it },
                    modifier = Modifier.testTag("rep_min"),
                )
                SlotMetricField(
                    label = "Rep max",
                    value = maxText,
                    isValueExternal = false,
                    onValueChange = { maxText = it },
                    modifier = Modifier.testTag("rep_max"),
                )
            }
        }

        val minNode = compose.onNodeWithTag("rep_min")
        val maxNode = compose.onNodeWithTag("rep_max")

        // Focus and edit rep_min
        minNode.performClick()
        minNode.performTextInput("10")
        assertEquals("10", minText)

        // Switch to rep_max
        maxNode.performClick()
        maxNode.performTextInput("15")
        assertEquals("15", maxText)

        // Verify minText was preserved
        assertEquals("10", minText)
    }

    @Test
    fun bottomOfListExerciseFieldEditingWithScroll() {
        val list = List(15) { "item_$it" }
        val values = mutableStateMapOf<String, String>().apply {
            list.forEach { this[it] = "8" }
        }

        compose.setContent {
            MaterialTheme {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("list")) {
                    itemsIndexed(list, key = { _, item -> item }) { _, item ->
                        SlotMetricField(
                            label = "Rep min",
                            value = values[item] ?: "",
                            isValueExternal = false,
                            onValueChange = { values[item] = it },
                            modifier = Modifier.testTag("field_$item"),
                        )
                    }
                }
            }
        }

        val listNode = compose.onNodeWithTag("list")
        val lastNode = compose.onNodeWithTag("field_item_14")

        // Scroll to the bottom item and edit
        listNode.performScrollToNode(hasTestTag("field_item_14"))
        lastNode.performClick()
        lastNode.performTextInput("6")

        assertEquals("6", values["item_14"])
    }
}
