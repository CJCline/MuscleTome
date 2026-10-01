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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.LedgerIndex
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.TemplatesBanner

/**
 * The full program list: create, duplicate, delete, switch the active
 * program, and — via the collapsible starter-programs banner — add a
 * starter program. Home stays focused on "what do I do today?" and links
 * here for anything program-management related.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    onOpenRoutine: (String) -> Unit,
    viewModel: RoutineListViewModel = hiltViewModel(),
) {
    val routinesState by viewModel.routines.collectAsStateWithLifecycle()
    val routines = routinesState?.first.orEmpty()
    val activeRoutineId = routinesState?.second
    var showCreate by rememberSaveable { mutableStateOf(value = false) }
    var deletingRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var templatesExpanded by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.routineAdded.collect { name ->
            snackbarHostState.showSnackbar("Added \"$name\" — set as your program")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Starter programs: the template banner lives here now that Home
            // no longer lists routines. Collapsible; hidden while empty
            // (the empty state below offers the same entry point as a CTA).
            if (routines.isNotEmpty()) {
                TemplatesBanner(
                    expanded = templatesExpanded,
                    onToggle = { templatesExpanded = !templatesExpanded },
                    onAddTemplate = { template ->
                        viewModel.addRoutineFromTemplate(template)
                    },
                )
            }
            if (routines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EmptyState(
                        title = "No routines yet",
                        body = "Create Push/Pull/Legs or a custom split.",
                    )
                    TemplatesBanner(
                        expanded = templatesExpanded,
                        onToggle = { templatesExpanded = !templatesExpanded },
                        onAddTemplate = { template ->
                            viewModel.addRoutineFromTemplate(template)
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    routine.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                if (routine.id == activeRoutineId) {
                                    MicroTag(text = "Active")
                                } else {
                                    TextButton(
                                        onClick = { viewModel.setActiveRoutine(routine.id) },
                                    ) {
                                        Text("Set active")
                                    }
                                }
                            }
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
            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
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
