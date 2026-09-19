package com.chy.muscletome.ui.navigation

import com.chy.muscletome.ui.workout.ActiveWorkoutScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chy.muscletome.ui.library.AddExerciseScreen
import com.chy.muscletome.ui.library.ExerciseLibraryScreen
import com.chy.muscletome.ui.routine.AddSlotScreen
import com.chy.muscletome.ui.routine.DayDetailScreen
import com.chy.muscletome.ui.routine.RoutineDetailScreen
import com.chy.muscletome.ui.routine.RoutineListScreen
import com.chy.muscletome.ui.home.HomeScreen
import androidx.compose.material.icons.filled.BarChart
import com.chy.muscletome.ui.settings.SettingsScreen
import com.chy.muscletome.ui.stats.SessionDetailScreen
import com.chy.muscletome.ui.stats.StatsScreen
@Composable
fun MuscleTomeNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute == "home" ||
            currentRoute == "routines" ||
            currentRoute == "library" ||
            currentRoute == "stats" ||
            currentRoute == "settings"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Today") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "routines",
                        onClick = {
                            navController.navigate("routines") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Routines") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "library",
                        onClick = {
                            navController.navigate("library") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "stats",
                        onClick = {
                            navController.navigate("stats") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        label = { Text("History") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(route = "home") {
                HomeScreen(
                    onOpenWorkout = { sessionId -> navController.navigate("workout/$sessionId") },
                    onOpenRoutines = {
                        navController.navigate("routines") { launchSingleTop = true }
                    },
                    onOpenRoutine = { routineId -> navController.navigate("routine/$routineId") },
                )
            }
            composable("routines") {
                RoutineListScreen(
                    onOpenRoutine = { id -> navController.navigate("routine/$id") },
                )
            }
            composable(
                route = "routine/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
            ) {
                RoutineDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDay = { dayId ->
                        val routineId = it.arguments?.getString("routineId")
                        navController.navigate("routine/$routineId/day/$dayId")
                    },
                )
            }
            composable(
                route = "routine/{routineId}/day/{dayId}",
                arguments = listOf(
                    navArgument("routineId") { type = NavType.StringType },
                    navArgument("dayId") { type = NavType.StringType },
                ),
            ) {
                val routineId = it.arguments?.getString("routineId")
                val dayId = it.arguments?.getString("dayId")
                DayDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAddSlot = { navController.navigate("routine/$routineId/day/$dayId/add_slot") },
                    onStartWorkout = { sessionId -> navController.navigate("workout/$sessionId") },
                )
            }
            composable(
                route = "routine/{routineId}/day/{dayId}/add_slot",
                arguments = listOf(
                    navArgument("routineId") { type = NavType.StringType },
                    navArgument("dayId") { type = NavType.StringType },
                ),
            ) {
                AddSlotScreen(onBack = { navController.popBackStack() })
            }
            composable("library") {
                ExerciseLibraryScreen(
                    onAddExercise = { navController.navigate("add_exercise") },
                )
            }
            composable("add_exercise") {
                AddExerciseScreen(onBack = { navController.popBackStack() })
            }
            composable(route = "stats") {
                StatsScreen(
                    onOpenSession = { sessionId -> navController.navigate("session/$sessionId") }
                )
            }
            composable(
                route = "session/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(route = "settings") {
                SettingsScreen()
            }
            composable(
                route = "workout/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                ActiveWorkoutScreen(
                    onFinished = { navController.popBackStack() },
                )
            }
        }
    }
}
