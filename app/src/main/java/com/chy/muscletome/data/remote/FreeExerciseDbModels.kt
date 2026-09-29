package com.chy.muscletome.data.remote

import kotlinx.serialization.Serializable

/**
 * free-exercise-db (yuhonas) dataset DTOs. Parsing lives in the adapter layer;
 * provider types never leak past [FreeExerciseDbImportAdapter].
 *
 * Schema (Unlicense): every field required by the upstream schema except
 * `force`; `mechanic`/`equipment`/`force` may be null.
 */
@Serializable
data class FreeExerciseDbRecord(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String? = null,
    val images: List<String> = emptyList(),
)

/** Asset envelope written by tools/curate_free_exercise_db.py. */
@Serializable
data class FreeExerciseDbAsset(
    val source: String,
    val revision: String,
    val records: List<FreeExerciseDbRecord> = emptyList(),
)
