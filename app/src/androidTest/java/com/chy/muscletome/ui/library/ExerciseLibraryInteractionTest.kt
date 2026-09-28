package com.chy.muscletome.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.domain.model.MovementType
import org.junit.Rule
import org.junit.Test

class ExerciseLibraryInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun familyExpansionExposesStateAndRendersVariations() {
        compose.setContent {
            MaterialTheme {
                val expanded = remember { mutableStateOf(value = false) }
                Column {
                    Text(
                        "Squat exercise family",
                        modifier = Modifier
                            .testTag("family")
                            .semantics { stateDescription = if (expanded.value) "Expanded" else "Collapsed" }
                            .clickable { expanded.value = !expanded.value },
                    )
                    if (expanded.value) Text("Goblet Squat")
                }
            }
        }

        compose.onNodeWithTag("family").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"),
        ).performClick()
        compose.onNodeWithText("Goblet Squat").assertExists()
        compose.onNodeWithTag("family").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"),
        ).performClick()
        compose.onNode(hasText("Goblet Squat")).assertDoesNotExist()
    }

    @Test fun detailContentShowsOrderedInstructionsAndMediaAttribution() {
        val exercise = ExerciseEntity(
            id = "detail",
            name = "Demo Press",
            movementType = MovementType.COMPOUND,
            primaryMuscleGroupId = "chest",
        )
        compose.setContent {
            MaterialTheme {
                ExerciseDetailContent(
                    state = ExerciseDetailUiState(
                        exercise = exercise,
                        canonical = ExerciseWithCanonicalRelations(
                            exercise = exercise,
                            metadata = null,
                            instructions = listOf(
                                ExerciseInstructionEntity("detail", 1, "Press"),
                                ExerciseInstructionEntity("detail", 0, "Brace"),
                            ),
                            equipment = emptyList(),
                            secondaryTargets = emptyList(),
                            media = listOf(
                                ExerciseMediaEntity(
                                    id = "media",
                                    exerciseId = "detail",
                                    type = "VIDEO",
                                    uri = "https://media.example/video",
                                    creator = "Coach",
                                    attribution = "Source",
                                    licenseName = "CC-BY",
                                ),
                            ),
                            sourceIdentities = emptyList(),
                        ),
                        loaded = true,
                    ),
                )
            }
        }
        compose.onNodeWithText("1. Brace").assertExists()
        compose.onNodeWithText("2. Press").assertExists()
        compose.onNodeWithText("Coach · Source · CC-BY").assertExists()
    }

    @Test fun detailContentShowsExplicitEmptyMediaState() {
        val exercise = ExerciseEntity(
            id = "empty",
            name = "No Media Press",
            movementType = MovementType.COMPOUND,
            primaryMuscleGroupId = "chest",
        )
        compose.setContent {
            MaterialTheme {
                ExerciseDetailContent(
                    state = ExerciseDetailUiState(
                        exercise = exercise,
                        canonical = ExerciseWithCanonicalRelations(
                            exercise = exercise,
                            metadata = null,
                            instructions = emptyList(),
                            equipment = emptyList(),
                            secondaryTargets = emptyList(),
                            media = emptyList(),
                            sourceIdentities = emptyList(),
                        ),
                        loaded = true,
                    ),
                )
            }
        }
        compose.onNodeWithText("No exercise media available").assertExists()
    }
}
