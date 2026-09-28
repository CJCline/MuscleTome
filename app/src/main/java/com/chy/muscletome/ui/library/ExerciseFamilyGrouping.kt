package com.chy.muscletome.ui.library

import com.chy.muscletome.data.local.dao.ExerciseLibraryRow

internal data class ExerciseFamilyGroup(
    val familyId: String?,
    val rows: List<ExerciseLibraryRow>,
)

internal fun groupExerciseFamilies(rows: List<ExerciseLibraryRow>): List<ExerciseFamilyGroup> {
    val order = mutableListOf<String?>()
    val groups = linkedMapOf<String?, MutableList<ExerciseLibraryRow>>()
    rows.forEach { row ->
        val id = row.metadata?.movementFamilyId
        if (!groups.containsKey(id)) {
            groups[id] = mutableListOf()
            order += id
        }
        groups.getValue(id) += row
    }
    return order.map { ExerciseFamilyGroup(it, groups.getValue(it).toList()) }
}
