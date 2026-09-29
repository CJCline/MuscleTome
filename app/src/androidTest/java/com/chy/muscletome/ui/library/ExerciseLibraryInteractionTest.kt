package com.chy.muscletome.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.domain.model.MovementType
import org.junit.Rule
import org.junit.Test

class ExerciseLibraryInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualFamilyCardExpansionExposesStateAndRendersVariations() {
        compose.setContent {
            MaterialTheme {
                val expandedFamilies = remember { mutableStateMapOf<String, Boolean>() }
                val expanded = expandedFamilies["squat"] == true
                ExpandableExerciseFamily(
                    familyLabel = "Squat",
                    members = listOf(
                        ExerciseEntity(
                            id = "goblet_squat",
                            name = "Goblet Squat",
                            movementType = MovementType.COMPOUND,
                            primaryMuscleGroupId = "quads",
                        ),
                        ExerciseEntity(
                            id = "front_squat",
                            name = "Front Squat",
                            movementType = MovementType.COMPOUND,
                            primaryMuscleGroupId = "quads",
                        ),
                    ),
                    expanded = expanded,
                    onToggle = { expandedFamilies["squat"] = !expanded },
                    onOpenExercise = {},
                )
            }
        }

        val family = compose.onNodeWithContentDescription("Squat exercise family")
        family.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"),
        )
        compose.onNodeWithText("Goblet Squat").assertDoesNotExist()
        family.performClick()
        family.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"),
        )
        compose.onNodeWithText("Goblet Squat").assertExists()
        compose.onNodeWithText("Front Squat").assertExists()
        family.performClick()
        compose.onNodeWithText("Goblet Squat").assertDoesNotExist()
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
