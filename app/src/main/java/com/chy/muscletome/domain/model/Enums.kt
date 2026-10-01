package com.chy.muscletome.domain.model

enum class MovementPattern { PUSH, PULL, HINGE, SQUAT, CARRY, OTHER }

enum class MovementType { COMPOUND, ISOLATION }

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

/**
 * Persisted provenance label. Stored as TEXT by name; only append values
 * (never reorder/rename) — older rows decode by name.
 */
enum class ExerciseSource { SEED, WGER, FREE_EXERCISE_DB, USER_CREATED, EXTERNAL }

/** Central registry between adapter source keys and persisted labels. */
object ExerciseSources {
    /** Maps a [NormalizedExerciseImport] sourceKey to its persisted label. */
    fun fromSourceKey(sourceKey: String?): ExerciseSource = when (sourceKey) {
        "wger" -> ExerciseSource.WGER
        "free_exercise_db" -> ExerciseSource.FREE_EXERCISE_DB
        null -> ExerciseSource.EXTERNAL
        else -> ExerciseSource.EXTERNAL
    }

    /** Short user-facing label for library rows; null = no badge. */
    fun displayLabel(source: ExerciseSource): String? = when (source) {
        ExerciseSource.WGER -> "wger"
        ExerciseSource.FREE_EXERCISE_DB -> "free-exercise-db"
        ExerciseSource.EXTERNAL -> "imported"
        else -> null
    }
}

enum class SlotType { FIXED, TARGET }

enum class TargetMovementType { COMPOUND, ISOLATION, ANY }

enum class SelectionReason { FIXED, AI_ROTATED, USER_OVERRIDE, USER_REROLL }

enum class WeightUnit { KG, LB }

/** Which effort scale the UI speaks; RPE stays the stored canon. */
enum class EffortScale { RPE, RIR }

enum class MatchStrictness { STRICT, LOOSE }

/** Preference for defaulting reps in workouts: configured minimum or maximum bound. */
enum class DefaultRepPreference { MINIMUM, MAXIMUM }