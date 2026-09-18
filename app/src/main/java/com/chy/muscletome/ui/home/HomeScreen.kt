package com.chy.muscletome.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startSessionId.collect { sessionId ->
            onOpenWorkout(sessionId)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Today") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.openSession != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Workout in progress", style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = viewModel::resumeOpenSession,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Resume") }
                        }
                    }
                }
            } else if (state.nextDay != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Up next", style = MaterialTheme.typography.labelLarge)
                            Text(
                                listOfNotNull(state.nextRoutineName, state.nextDay?.name)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Button(
                                onClick = viewModel::startNextDay,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Start workout") }
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No routines yet")
                            Button(
                                onClick = onOpenRoutines,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Create a routine") }
                        }
                    }
                }
            }

            item { Text("Routines", style = MaterialTheme.typography.titleMedium) }
            items(state.routines, key = { it.id }) { routine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(routine.name) })
                }
            }
        }
    }
}
