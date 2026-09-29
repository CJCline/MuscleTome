package com.chy.muscletome.domain.model

/**
 * Shared, conservative name-keyword heuristics for movement pattern/type.
 *
 * Extracted from the wger adapter so every source adapter derives identical
 * pattern/type values for the same display name — cross-source corroboration
 * in [ExerciseImportIdentityPolicy] depends on this consistency. Rules are
 * deliberately keyword-based and conservative; unknown names yield
 * [MovementPattern.OTHER] rather than a guess.
 */
object MovementHeuristics {

    /** Keyword fallback for a movement pattern; never guesses a family. */
    fun movementPattern(name: String): MovementPattern {
        val n = name.lowercase()
        return when {
            n.contains("deadlift") || n.contains("hinge") || n.contains("rdl") -> MovementPattern.HINGE
            n.contains("squat") || n.contains("lunge") || n.contains("leg press") -> MovementPattern.SQUAT
            n.contains("row") || n.contains("pull") || n.contains("chin") || n.contains("lat ") -> MovementPattern.PULL
            n.contains("press") || n.contains("push") || n.contains("dip") -> MovementPattern.PUSH
            n.contains("carry") || n.contains("walk") -> MovementPattern.CARRY
            else -> MovementPattern.OTHER
        }
    }

    /** Keyword fallback for compound vs isolation; unknown reads compound. */
    fun movementType(name: String): MovementType {
        val n = name.lowercase()
        val isolationHints = listOf("fly", "raise", "curl", "extension", "kickback", "shrug", "calf")
        return if (isolationHints.any { n.contains(it) }) MovementType.ISOLATION else MovementType.COMPOUND
    }
}
