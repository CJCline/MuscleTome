package com.chy.muscletome.domain.template

import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.TargetMovementType

/**
 * One slot in a [RoutineTemplate] day — either a FIXED seed exercise or a
 * TARGET that the variety engine resolves at session start.
 */
data class TemplateSlot(
    val type: SlotType,
    val exerciseId: String? = null,
    val targetMuscleIds: List<String> = emptyList(),
    val targetMovementType: TargetMovementType = TargetMovementType.ANY,
    val sets: Int,
    val repRangeMin: Int,
    val repRangeMax: Int,
    val restSeconds: Int,
)

data class TemplateDay(
    val name: String,
    val slots: List<TemplateSlot>,
)

data class RoutineTemplate(
    val id: String,
    val name: String,
    val blurb: String,
    val days: List<TemplateDay>,
)

/**
 * First-run program templates. Every day leads with FIXED seed compounds and
 * finishes with TARGET accessories, so the variety engine is visible (and
 * exercised) from day one. FIXED ids must exist in [SeedCatalog]; validated
 * by `RoutineTemplatesTest`.
 */
object RoutineTemplates {

    val FULL_BODY_3X = RoutineTemplate(
        id = "full_body_3x",
        name = "Full Body 3×",
        blurb = "Three balanced whole-body sessions. Squat, press, and pull every time out.",
        days = listOf(
            TemplateDay(
                "Full Body A",
                listOf(
                    fixed("squat", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("barbell_bench_press", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("barbell_row", sets = 3, min = 6, max = 10, rest = 120),
                    target(listOf("biceps"), sets = 3, min = 8, max = 12, rest = 60),
                    target(listOf("abs"), sets = 3, min = 8, max = 15, rest = 45),
                ),
            ),
            TemplateDay(
                "Full Body B",
                listOf(
                    fixed("romanian_deadlift", sets = 4, min = 6, max = 10, rest = 150),
                    fixed("overhead_press", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("lat_pulldown", sets = 3, min = 8, max = 12, rest = 120),
                    target(listOf("triceps"), sets = 3, min = 8, max = 12, rest = 60),
                    target(listOf("side_delts"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                ),
            ),
            TemplateDay(
                "Full Body C",
                listOf(
                    fixed("deadlift", sets = 3, min = 3, max = 6, rest = 180),
                    fixed("dumbbell_bench_press", sets = 3, min = 8, max = 12, rest = 120),
                    fixed("pull_up", sets = 3, min = 6, max = 12, rest = 120),
                    target(listOf("quads"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("rear_delts"), TargetMovementType.ISOLATION, sets = 3, min = 12, max = 15, rest = 60),
                ),
            ),
        ),
    )

    val UPPER_LOWER = RoutineTemplate(
        id = "upper_lower",
        name = "Upper / Lower",
        blurb = "Four sessions splitting body halves. Heavy compounds first, targeted pump work after.",
        days = listOf(
            TemplateDay(
                "Upper A",
                listOf(
                    fixed("barbell_bench_press", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("barbell_row", sets = 4, min = 6, max = 10, rest = 150),
                    fixed("overhead_press", sets = 3, min = 6, max = 10, rest = 120),
                    target(listOf("biceps"), sets = 3, min = 8, max = 12, rest = 60),
                    target(listOf("triceps"), sets = 3, min = 8, max = 12, rest = 60),
                ),
            ),
            TemplateDay(
                "Lower A",
                listOf(
                    fixed("squat", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("romanian_deadlift", sets = 3, min = 8, max = 12, rest = 120),
                    target(listOf("quads"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("hamstrings"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("calves"), sets = 3, min = 12, max = 20, rest = 45),
                ),
            ),
            TemplateDay(
                "Upper B",
                listOf(
                    fixed("dumbbell_bench_press", sets = 4, min = 8, max = 12, rest = 120),
                    fixed("pull_up", sets = 4, min = 6, max = 12, rest = 120),
                    target(listOf("side_delts"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("rear_delts"), TargetMovementType.ISOLATION, sets = 3, min = 12, max = 15, rest = 60),
                    target(listOf("chest"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                ),
            ),
            TemplateDay(
                "Lower B",
                listOf(
                    fixed("deadlift", sets = 3, min = 3, max = 6, rest = 180),
                    fixed("leg_press", sets = 3, min = 10, max = 15, rest = 120),
                    target(listOf("glutes"), sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("abs"), sets = 3, min = 10, max = 20, rest = 45),
                ),
            ),
        ),
    )

    val PPL = RoutineTemplate(
        id = "ppl",
        name = "Push / Pull / Legs",
        blurb = "The classic six-day-capable split. One movement pattern per day, accessories auto-picked.",
        days = listOf(
            TemplateDay(
                "Push",
                listOf(
                    fixed("barbell_bench_press", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("overhead_press", sets = 3, min = 6, max = 10, rest = 120),
                    target(listOf("chest"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("side_delts"), TargetMovementType.ISOLATION, sets = 3, min = 12, max = 15, rest = 60),
                    target(listOf("triceps"), sets = 3, min = 8, max = 12, rest = 60),
                ),
            ),
            TemplateDay(
                "Pull",
                listOf(
                    fixed("barbell_row", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("pull_up", sets = 3, min = 6, max = 12, rest = 120),
                    target(listOf("lats"), sets = 3, min = 8, max = 12, rest = 90),
                    target(listOf("biceps"), sets = 3, min = 8, max = 12, rest = 60),
                    target(listOf("rear_delts"), TargetMovementType.ISOLATION, sets = 3, min = 12, max = 15, rest = 60),
                ),
            ),
            TemplateDay(
                "Legs",
                listOf(
                    fixed("squat", sets = 4, min = 5, max = 8, rest = 150),
                    fixed("romanian_deadlift", sets = 3, min = 8, max = 12, rest = 120),
                    target(listOf("quads"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("hamstrings"), TargetMovementType.ISOLATION, sets = 3, min = 10, max = 15, rest = 60),
                    target(listOf("calves"), sets = 3, min = 12, max = 20, rest = 45),
                ),
            ),
        ),
    )

    val ALL = listOf(FULL_BODY_3X, UPPER_LOWER, PPL)

    /** Onboarding "none of the above" option — creates nothing. */
    val SCRATCH = RoutineTemplate(
        id = "scratch",
        name = "Start from scratch",
        blurb = "Skip templates — build your routine yourself in the Routines tab.",
        days = emptyList(),
    )

    private fun fixed(id: String, sets: Int, min: Int, max: Int, rest: Int) = TemplateSlot(
        type = SlotType.FIXED,
        exerciseId = id,
        sets = sets,
        repRangeMin = min,
        repRangeMax = max,
        restSeconds = rest,
    )

    private fun target(
        muscleIds: List<String>,
        movement: TargetMovementType = TargetMovementType.ANY,
        sets: Int,
        min: Int,
        max: Int,
        rest: Int,
    ) = TemplateSlot(
        type = SlotType.TARGET,
        targetMuscleIds = muscleIds,
        targetMovementType = movement,
        sets = sets,
        repRangeMin = min,
        repRangeMax = max,
        restSeconds = rest,
    )
}
