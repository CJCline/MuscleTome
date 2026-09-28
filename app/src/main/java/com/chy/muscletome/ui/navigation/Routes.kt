package com.chy.muscletome.ui.navigation

/**
 * Single source of truth for every route in the graph. Screens never build
 * route strings by hand — they call these builders, so a renamed parameter
 * or path is a compile error here, not a silent 404 at runtime.
 *
 * Pattern constants are used for the NavHost declarations; builder functions
 * produce the concrete navigated route.
 */
object Routes {
    // Top-level tabs
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val LIBRARY = "library"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    // Parameterless flows
    const val ADD_EXERCISE = "add_exercise"
    const val EXERCISE_IMPORT_REVIEW = "exercise_import_review"

    // Parameterized destinations: pattern for the graph, builder for navigate()
    const val ROUTINE_PATTERN = "routine/{routineId}"
    fun routine(routineId: String) = "routine/$routineId"

    const val DAY_PATTERN = "routine/{routineId}/day/{dayId}"
    fun day(routineId: String, dayId: String) = "routine/$routineId/day/$dayId"

    const val ADD_SLOT_PATTERN = "routine/{routineId}/day/{dayId}/add_slot"
    fun addSlot(routineId: String, dayId: String) = "routine/$routineId/day/$dayId/add_slot"

    const val EXERCISE_PATTERN = "exercise/{exerciseId}"
    fun exercise(exerciseId: String) = "exercise/$exerciseId"

    const val SESSION_PATTERN = "session/{sessionId}"
    fun session(sessionId: String) = "session/$sessionId"

    const val WORKOUT_PATTERN = "workout/{sessionId}"
    fun workout(sessionId: String) = "workout/$sessionId"
}
