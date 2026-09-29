package com.chy.muscletome.domain.model

/** Stable family labels and conservative fallback mapping for display names. */
object MovementFamilies {
    data class Family(val id: String, val label: String)

    val all = listOf(
        Family("squat", "Squat"), Family("bench_press", "Bench Press"),
        Family("row", "Row"), Family("deadlift", "Deadlift"),
        Family("overhead_press", "Overhead Press"), Family("curl", "Curl"),
        Family("lunge", "Lunge"), Family("plank", "Plank"),
    )

    /**
     * Families group comparable variations, not all movements sharing a pattern
     * or muscle. Display names are not stable taxonomy IDs. Source-provided family
     * IDs should override this conservative name-based fallback.
     */
    @Suppress("UNUSED_PARAMETER")
    fun familyId(name: String, movementPattern: MovementPattern? = null): String? {
        val normalized = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        val tokens = normalized.split(Regex("\\s+")).toSet()
        return when {
            "squat" in tokens -> "squat"
            "deadlift" in tokens || "rdl" in tokens -> "deadlift"
            "lunge" in tokens -> "lunge"
            "plank" in tokens -> "plank"
            "curl" in tokens -> "curl"
            "row" in tokens -> "row"
            "pulldown" in tokens && "lat" in tokens -> "row"
            "pullup" in tokens || "chinup" in tokens ||
                ("pull" in tokens && "up" in tokens) || ("chin" in tokens && "up" in tokens) -> "row"
            "fly" in tokens -> if ("cable" in tokens || "chest" in tokens) "bench_press" else null
            "bench" in tokens && "press" in tokens && "pec" !in tokens && "deck" !in tokens -> "bench_press"
            "pushup" in tokens || ("push" in tokens && "up" in tokens) -> "bench_press"
            "overhead" in tokens && "press" in tokens -> "overhead_press"
            "shoulder" in tokens && "press" in tokens -> "overhead_press"
            else -> null
        }
    }

    fun label(id: String): String? = all.firstOrNull { it.id == id }?.label

    /**
     * Single shared normalizer for family dedup (Phase 5A): trim, lowercase,
     * collapse runs of non-alphanumerics to a single space. Mirrors how import
     * name matching normalizes display names.
     */
    fun normalizeKey(displayName: String): String =
        displayName.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
