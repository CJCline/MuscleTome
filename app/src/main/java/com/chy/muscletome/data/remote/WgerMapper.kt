package com.chy.muscletome.data.remote

import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementHeuristics
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType

object WgerMapper {

    /** Map wger name_en / name → your muscle_group ids from SeedCatalog. */
    fun muscleId(nameEn: String): String? {
        val n = nameEn.trim().lowercase()
        return when {
            n.contains("pectoral") || n == "chest" -> "chest"
            n.contains("latissimus") || n == "lats" || n.contains("lat ") -> "lats"
            n.contains("trapezius") || n == "traps" -> "traps"
            n.contains("rhomboid") -> "rhomboids"
            n.contains("deltoid") && (n.contains("rear") || n.contains("posterior")) -> "rear_delts"
            n.contains("deltoid") && (n.contains("lateral") || n.contains("side")) -> "side_delts"
            n.contains("deltoid") || n.contains("shoulder") -> "front_delts"
            n.contains("biceps") && !n.contains("femoris") -> "biceps"
            n.contains("triceps") -> "triceps"
            n.contains("quadriceps") || n == "quads" -> "quads"
            n.contains("hamstring") || n.contains("biceps femoris") -> "hamstrings"
            n.contains("glute") -> "glutes"
            n.contains("calf") || n.contains("gastroc") || n.contains("soleus") -> "calves"
            n.contains("abdomin") || n == "abs" || n.contains("oblique") -> "abs"
            n.contains("back") -> "back"
            n.contains("arm") -> "arms"
            n.contains("leg") -> "legs"
            n.contains("core") -> "core"
            else -> null
        }
    }

    /** Known normalized equipment ID, or null when the source label is unmapped. */
    fun knownEquipmentId(name: String): String? {
        val n = name.trim().lowercase()
        return when {
            n.contains("barbell") -> "barbell"
            n.contains("dumbbell") -> "dumbbell"
            n.contains("cable") || n.contains("pulley") -> "cable"
            n.contains("machine") || n.contains("sled") || n.contains("smith") -> "machine"
            n.contains("body") || n == "none" || n.contains("no equipment") -> "bodyweight"
            n.contains("band") -> "band"
            n.contains("kettle") -> "kettlebell"
            n.contains("ez") -> "barbell"
            else -> null
        }
    }

    /** Preserve existing importer behavior for unknown equipment labels. */
    fun equipmentId(name: String): String {
        val n = name.trim().lowercase()
        return knownEquipmentId(name)
            ?: n.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "other" }
    }

    fun equipmentDisplayName(name: String): String = name.trim().ifBlank { "Other" }

    fun movementType(name: String, category: String?): MovementType =
        MovementHeuristics.movementType(name)

    fun movementPattern(name: String, category: String?): MovementPattern =
        MovementHeuristics.movementPattern(name)

    fun difficulty(name: String): Difficulty = Difficulty.INTERMEDIATE

    fun exerciseId(wgerId: Int): String = "wger_$wgerId"
}