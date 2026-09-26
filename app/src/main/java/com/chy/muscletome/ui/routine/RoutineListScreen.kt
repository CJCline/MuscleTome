package com.chy.muscletome.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.LedgerIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    onOpenRoutine: (String) -> Unit,
    viewModel: RoutineListViewModel = hiltViewModel(),
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(value = false) }
    var deletingRoutine by remember { mutableStateOf<RoutineEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Routines") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create routine")
            }
        },
    ) { innerPadding ->
        if (routines.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                EmptyState(
                    title = "No routines yet",
                    body = "Create Push/Pull/Legs or a custom split.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(routines, key = { _, routine -> routine.id }) { index, routine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRoutine(routine.id) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LedgerIndex(index = index + 1)
                        Text(
                            routine.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.duplicateRoutine(routine.id) }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Duplicate routine",
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = { deletingRoutine = routine }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete routine",
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    if (index < routines.lastIndex) {
                        LedgerDivider()
                    }
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New routine",
            label = "Routine name",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.createRoutine(name)
                showCreate = false
            },
        )
    }
    deletingRoutine?.let { routine ->
        AlertDialog(
            onDismissRequest = { deletingRoutine = null },
            title = { Text("Delete routine?") },
            text = {
                Text(
                    "\"${routine.name}\" and all its days and slots will be " +
                        "deleted. Your logged workouts are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRoutine(routine.id)
                        deletingRoutine = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingRoutine = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(value = "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
