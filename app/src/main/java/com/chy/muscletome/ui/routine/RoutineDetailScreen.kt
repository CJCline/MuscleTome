package com.chy.muscletome.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.dragReorderItem
import com.chy.muscletome.ui.components.rememberDragReorderState
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    onBack: () -> Unit,
    onOpenDay: (String) -> Unit,
    viewModel: RoutineDetailViewModel = hiltViewModel(),
) {
    val routine by viewModel.routine.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(value = false) }
    var showDeleteRoutine by remember { mutableStateOf(false) }
    var deletingDay by remember { mutableStateOf<RoutineDayEntity?>(null) }

    var dayRows by remember { mutableStateOf(days) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(days) { if (!dragging) dayRows = days }
    val dayListState = rememberLazyListState()
    val dragState = rememberDragReorderState(
        listState = dayListState,
        onMove = { from, to ->
            dragging = true
            dayRows = dayRows.toMutableList().apply { add(to, removeAt(from)) }
        },
        onDrop = {
            dragging = false
            viewModel.reorderDays(dayRows.map { it.id })
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((routine?.name ?: "ROUTINE").uppercase(Locale.US), style = MuscleTomeTextStyles.heading) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::duplicateRoutine) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "DUPLICATE ROUTINE")
                    }
                    IconButton(onClick = { showDeleteRoutine = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "DELETE ROUTINE",
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.Add, contentDescription = "ADD DAY")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = dayListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (dayRows.isEmpty()) {
                item {
                    EmptyState(
                        title = "NO DAYS YET",
                        body = "ADD TRAINING DAYS TO THIS ROUTINE.",
                    )
                }
            }
            itemsIndexed(dayRows, key = { _, day -> day.id }) { index, day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dragReorderItem(dragState, day.id)
                        .clickable { onOpenDay(day.id) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(3.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            day.name.uppercase(Locale.US),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MonoText(
                            text = "DAY ${(day.orderIndex + 1).toString().padStart(2, '0')}",
                            style = MuscleTomeTextStyles.tag,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.duplicateDay(day.id) }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "DUPLICATE DAY",
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = { deletingDay = day }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "DELETE DAY",
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                if (index < dayRows.lastIndex) {
                    LedgerDivider()
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "NEW DAY",
            label = "DAY NAME",
            confirmLabel = "ADD",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.addDay(name)
                showCreate = false
            },
        )
    }

    if (showDeleteRoutine) {
        AlertDialog(
            onDismissRequest = { showDeleteRoutine = false },
            title = { Text("DELETE ROUTINE?", style = MuscleTomeTextStyles.heading) },
            text = {
                Text(
                    "\"${(routine?.name ?: "THIS ROUTINE").uppercase(Locale.US)}\" AND ALL DAYS WILL BE DELETED. LOGGED WORKOUTS REMAIN.",
                    style = MuscleTomeTextStyles.systemMessage,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteRoutine = false
                        viewModel.deleteRoutine()
                        onBack()
                    },
                ) {
                    Text("DELETE", style = MuscleTomeTextStyles.button, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRoutine = false }) {
                    Text("CANCEL", style = MuscleTomeTextStyles.button)
                }
            },
        )
    }

    deletingDay?.let { day ->
        AlertDialog(
            onDismissRequest = { deletingDay = null },
            title = { Text("DELETE DAY?", style = MuscleTomeTextStyles.heading) },
            text = {
                Text(
                    "\"${day.name.uppercase(Locale.US)}\" AND ITS SLOTS WILL BE REMOVED FROM ROUTINE. LOGGED WORKOUTS REMAIN.",
                    style = MuscleTomeTextStyles.systemMessage,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDay(day.id)
                        deletingDay = null
                    },
                ) {
                    Text("DELETE", style = MuscleTomeTextStyles.button, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDay = null }) {
                    Text("CANCEL", style = MuscleTomeTextStyles.button)
                }
            },
        )
    }
}
