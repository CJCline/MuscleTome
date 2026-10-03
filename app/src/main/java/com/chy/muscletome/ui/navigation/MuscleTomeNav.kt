package com.chy.muscletome.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chy.muscletome.ui.home.HomeScreen
import com.chy.muscletome.ui.library.AddExerciseScreen
import com.chy.muscletome.ui.library.CreateExerciseScreen
import com.chy.muscletome.ui.library.ExerciseDetailScreen
import com.chy.muscletome.ui.library.ExerciseImportReviewScreen
import com.chy.muscletome.ui.library.ExerciseLibraryScreen
import com.chy.muscletome.ui.onboarding.OnboardingScreen
import com.chy.muscletome.ui.onboarding.OnboardingViewModel
import com.chy.muscletome.ui.routine.AddSlotScreen
import com.chy.muscletome.ui.routine.DayDetailScreen
import com.chy.muscletome.ui.routine.RoutineDetailScreen
import com.chy.muscletome.ui.routine.RoutineListScreen
import com.chy.muscletome.ui.settings.SettingsScreen
import com.chy.muscletome.ui.stats.SessionDetailScreen
import com.chy.muscletome.ui.stats.StatsScreen
import com.chy.muscletome.ui.theme.MutedGrayBorder
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import com.chy.muscletome.ui.workout.ActiveWorkoutScreen

/** Iron nav bar colors: hazard yellow selected, steel unselected. */
@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun MuscleTomeNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingDone by onboardingViewModel.completed.collectAsStateWithLifecycle()
    var onboardingDismissed by remember { mutableStateOf(false) }
    val showOnboarding = !onboardingDone && !onboardingDismissed

    val showBottomBar = currentRoute == "home" ||
            currentRoute == "routines" ||
            currentRoute == "library" ||
            currentRoute == "stats" ||
            currentRoute == "settings"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(width = 1.dp, color = MutedGrayBorder),
                ) {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("TODAY", style = MuscleTomeTextStyles.tag) },
                        colors = navItemColors(),
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
                        label = { Text("ROUTINES", style = MuscleTomeTextStyles.tag) },
                        colors = navItemColors(),
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
                        label = { Text("LIBRARY", style = MuscleTomeTextStyles.tag) },
                        colors = navItemColors(),
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
                        label = { Text("STATS", style = MuscleTomeTextStyles.tag) },
                        colors = navItemColors(),
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
                        label = { Text("SETTINGS", style = MuscleTomeTextStyles.tag) },
                        colors = navItemColors(),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (showOnboarding) {
            OnboardingScreen(onDone = { onboardingDismissed = true })
        } else {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(route = Routes.HOME) {
                    HomeScreen(
                        onOpenWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
                        onOpenRoutines = {
                            navController.navigate(Routes.ROUTINES) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.ROUTINES) {
                    RoutineListScreen(
                        onOpenRoutine = { id -> navController.navigate(Routes.routine(id)) },
                    )
                }
                composable(
                    route = Routes.ROUTINE_PATTERN,
                    arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
                ) {
                    RoutineDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDay = { dayId ->
                            val routineId = it.arguments?.getString("routineId")
                            navController.navigate(Routes.day(requireNotNull(routineId), dayId))
                        },
                    )
                }
                composable(
                    route = Routes.DAY_PATTERN,
                    arguments = listOf(
                        navArgument("routineId") { type = NavType.StringType },
                        navArgument("dayId") { type = NavType.StringType },
                    ),
                ) {
                    val routineId = it.arguments?.getString("routineId")
                    val dayId = it.arguments?.getString("dayId")
                    DayDetailScreen(
                        onBack = { navController.popBackStack() },
                        onAddSlot = {
                            navController.navigate(
                                Routes.addSlot(
                                    requireNotNull(routineId),
                                    requireNotNull(dayId),
                                ),
                            )
                        },
                        onStartWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
                    )
                }
                composable(
                    route = Routes.ADD_SLOT_PATTERN,
                    arguments = listOf(
                        navArgument("routineId") { type = NavType.StringType },
                        navArgument("dayId") { type = NavType.StringType },
                    ),
                ) {
                    AddSlotScreen(
                        onBack = { navController.popBackStack() },
                        onAddExercise = { navController.navigate(Routes.addExercise()) },
                    )
                }
                composable(Routes.LIBRARY) {
                    ExerciseLibraryScreen(
                        onAddExercise = { navController.navigate(Routes.addExercise()) },
                        onOpenExercise = { exerciseId -> navController.navigate(Routes.exercise(exerciseId)) },
                        onOpenImportReview = { navController.navigate(Routes.EXERCISE_IMPORT_REVIEW) },
                    )
                }
                composable(
                    route = Routes.ADD_EXERCISE_PATTERN,
                    arguments = listOf(
                        navArgument("exerciseId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    CreateExerciseScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.EXERCISE_IMPORT_REVIEW) {
                    ExerciseImportReviewScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.EXERCISE_PATTERN,
                    arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
                ) {
                    ExerciseDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { exerciseId -> navController.navigate(Routes.addExercise(exerciseId)) },
                    )
                }
                composable(route = Routes.STATS) {
                    StatsScreen(
                        onOpenSession = { sessionId -> navController.navigate(Routes.session(sessionId)) },
                        onOpenHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = Routes.SESSION_PATTERN,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    SessionDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(route = Routes.SETTINGS) {
                    SettingsScreen()
                }
                composable(
                    route = Routes.WORKOUT_PATTERN,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    ActiveWorkoutScreen(
                        onBack = { navController.popBackStack() },
                        onFinished = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
