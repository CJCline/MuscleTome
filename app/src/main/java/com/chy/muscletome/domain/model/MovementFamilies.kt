package com.chy.muscletome.domain.model

/** Stable family labels and safe name mapping. Family membership is stored as text metadata. */
object MovementFamilies {
    data class Family(val id: String, val label: String)

    val all = listOf(
        Family("squat", "Squat"), Family("bench_press", "Bench Press"),
        Family("row", "Row"), Family("deadlift", "Deadlift"),
        Family("overhead_press", "Overhead Press"), Family("curl", "Curl"),
        Family("lunge", "Lunge"), Family("plank", "Plank"),
    )

    fun familyId(name: String, movementPattern: MovementPattern? = null): String? {
        val n = name.trim().lowercase()
        return when {
            n.contains("squat") || n.contains("leg press") || movementPattern == MovementPattern.SQUAT -> "squat"
            n.contains("bench press") || n.contains("push-up") || n.contains("push up") || n.contains("cable fly") -> "bench_press"
            n.contains("row") || n.contains("pull-up") || n.contains("pull up") || n.contains("lat pulldown") -> "row"
            n.contains("deadlift") || n.contains("rdl") -> "deadlift"
            n.contains("overhead press") || n.contains("shoulder press") -> "overhead_press"
            n.contains("curl") -> "curl"
            n.contains("lunge") -> "lunge"
            n.contains("plank") -> "plank"
            else -> null
        }
    }

    fun label(id: String): String? = all.firstOrNull { it.id == id }?.label
}
