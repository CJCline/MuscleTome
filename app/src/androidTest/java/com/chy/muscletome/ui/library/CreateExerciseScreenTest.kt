package com.chy.muscletome.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.MovementType
import org.junit.Rule
import org.junit.Test

class CreateExerciseScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun parentVariationSectionIsCollapsedByDefaultAndExpandsOnToggle() {
        val parents = listOf(
            ExerciseEntity(
                id = "barbell_bench_press",
                name = "Barbell Bench Press",
                movementType = MovementType.COMPOUND,
                primaryMuscleGroupId = "chest",
            ),
        )

        compose.setContent {
            MaterialTheme {
                // Render the collapsible parent card state
                var parentExerciseId by remember { mutableStateOf<String?>(null) }
                var parentSectionExpanded by remember { mutableStateOf(false) }

                CreateExerciseParentVariationCard(
                    parentExercises = parents,
                    selectedParentId = parentExerciseId,
                    expanded = parentSectionExpanded,
                    onToggleExpand = { parentSectionExpanded = !parentSectionExpanded },
                    onSelectParent = { parentExerciseId = it },
                )
            }
        }

        // Section header and current link summary should be displayed
        compose.onNodeWithText("CURRENT LINK").assertIsDisplayed()
        compose.onNodeWithText("NONE (STANDALONE PARENT EXERCISE)").assertIsDisplayed()

        // Parent exercise choice should NOT be visible when collapsed
        compose.onNodeWithText("BARBELL BENCH PRESS").assertDoesNotExist()

        // Toggle expand
        compose.onNodeWithContentDescription("EXPAND PARENT EXERCISE OPTIONS").performClick()

        // Now parent options should be displayed
        compose.onNodeWithText("BARBELL BENCH PRESS").assertIsDisplayed()

        // Toggle collapse
        compose.onNodeWithContentDescription("COLLAPSE PARENT EXERCISE OPTIONS").performClick()

        // Options should be hidden again
        compose.onNodeWithText("BARBELL BENCH PRESS").assertDoesNotExist()
    }
}
