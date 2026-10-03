package com.chy.muscletome.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.theme.ArchivalCream
import com.chy.muscletome.ui.theme.Ink
import com.chy.muscletome.ui.theme.MutedGrayBorder
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenImportReview: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                title = { Text("EXERCISE LIBRARY", style = MuscleTomeTextStyles.heading, color = ArchivalCream) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExercise,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(2.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "ADD EXERCISE")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenImportReview) {
                    Text("IMPORT REVIEW", style = MuscleTomeTextStyles.button)
                }
            }

            SelectOnFocusOutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(width = 1.dp, color = MutedGrayBorder, shape = RoundedCornerShape(2.dp)),
                singleLine = true,
                label = { Text("SEARCH EXERCISES", style = MuscleTomeTextStyles.label) },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.exercises.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            EmptyState(
                                title = "NO EXERCISES FOUND",
                                body = if (state.query.isBlank()) {
                                    "THE LIBRARY FILLS FROM SEED DATA AND CUSTOM EXERCISES."
                                } else {
                                    "NO EXERCISES MATCH YOUR SEARCH."
                                },
                            )
                            if (state.query.isBlank()) {
                                AccentButton(
                                    text = "CREATE CUSTOM EXERCISE",
                                    onClick = onAddExercise,
                                )
                            }
                        }
                    }
                } else {
                    val categories = state.exercises
                        .groupBy { it.category.ifBlank { it.primaryMuscleGroupId }.uppercase(Locale.US) }

                    categories.forEach { (categoryName, categoryExercises) ->
                        item(key = "category_header_$categoryName") {
                            SectionHeader(categoryName)
                        }

                        val parentExercises = categoryExercises.filter { it.parentExerciseId == null }
                        val childByParent = categoryExercises.filter { it.parentExerciseId != null }
                            .groupBy { it.parentExerciseId }

                        parentExercises.forEach { parent ->
                            item(key = "parent_${parent.id}") {
                                ParentExerciseRow(parent, onClick = { onOpenExercise(parent.id) })
                            }

                            val children = childByParent[parent.id].orEmpty()
                            children.forEachIndexed { index, child ->
                                val isLast = index == children.lastIndex
                                item(key = "child_${child.id}") {
                                    ChildExerciseRow(
                                        exercise = child,
                                        isLastChild = isLast,
                                        onClick = { onOpenExercise(child.id) },
                                    )
                                }
                            }
                        }

                        val standaloneChildren = categoryExercises.filter { child ->
                            child.parentExerciseId != null && parentExercises.none { p -> p.id == child.parentExerciseId }
                        }
                        standaloneChildren.forEach { standalone ->
                            item(key = "standalone_${standalone.id}") {
                                ParentExerciseRow(standalone, onClick = { onOpenExercise(standalone.id) })
                            }
                        }

                        item(key = "divider_$categoryName") {
                            LedgerDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ParentExerciseRow(
    exercise: ExerciseEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                exercise.name.uppercase(Locale.US),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val subtitle = exercise.muscleGroup.ifBlank { exercise.primaryMuscleGroupId }.uppercase(Locale.US)
            Text(
                subtitle,
                style = MuscleTomeTextStyles.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (exercise.isCustom) {
            MicroTag(text = "[CUSTOM]")
        }
    }
}

@Composable
internal fun ChildExerciseRow(
    exercise: ExerciseEntity,
    isLastChild: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CadConnectorLine(
            isLastChild = isLastChild,
            modifier = Modifier
                .width(36.dp)
                .height(36.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                exercise.name.uppercase(Locale.US),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val subtitle = exercise.muscleGroup.ifBlank { exercise.primaryMuscleGroupId }.uppercase(Locale.US)
            Text(
                subtitle,
                style = MuscleTomeTextStyles.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (exercise.isCustom) {
            MicroTag(text = "[CUSTOM]")
        }
    }
}

@Composable
fun CadConnectorLine(
    isLastChild: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = 1.5.dp.toPx()
        val lineColor = Color(0xFF4D5A43) // MutedGrayBorder / RawSteel
        val widthPx = size.width
        val heightPx = size.height
        val halfHeightPx = heightPx / 2f
        val startXPx = 12.dp.toPx()

        drawLine(
            color = lineColor,
            start = Offset(startXPx, 0f),
            end = Offset(startXPx, if (isLastChild) halfHeightPx else heightPx),
            strokeWidth = strokeWidthPx,
        )
        drawLine(
            color = lineColor,
            start = Offset(startXPx, halfHeightPx),
            end = Offset(widthPx, halfHeightPx),
            strokeWidth = strokeWidthPx,
        )
    }
}
