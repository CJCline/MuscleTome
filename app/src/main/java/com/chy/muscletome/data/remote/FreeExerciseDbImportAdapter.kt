package com.chy.muscletome.data.remote

import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.ImportDiagnostic
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.domain.model.MovementHeuristics
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.NormalizedExerciseImport

/**
 * Maps an already-parsed free-exercise-db record into the source-neutral
 * import contract, per the curated tables in docs/canonical-exercise-import.md.
 * Blank names are rejected as unusable identity; every unmapped/absent value
 * emits a diagnostic with its native source value — nothing is invented.
 */
object FreeExerciseDbImportAdapter {

    const val SOURCE_KEY = "free_exercise_db"
    const val ATTRIBUTION = "free-exercise-db (yuhonas)"
    const val LICENSE_NAME = "Unlicense"
    const val LICENSE_URL = "https://unlicense.org/"
    private const val IMAGE_BASE =
        "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

    /** Curated muscle mapping; unmapped values stay absent with a diagnostic. */
    internal val MUSCLE_MAP: Map<String, String> = mapOf(
        "quadriceps" to "quads",
        "shoulders" to "shoulders",
        "abdominals" to "abs",
        "chest" to "chest",
        "hamstrings" to "hamstrings",
        "triceps" to "triceps",
        "biceps" to "biceps",
        "lats" to "lats",
        "middle back" to "rhomboids",
        "calves" to "calves",
        "lower back" to "back",
        "glutes" to "glutes",
        "traps" to "traps",
    )

    /** Curated equipment mapping; unmapped/unspecified stay absent. */
    internal val EQUIPMENT_MAP: Map<String, String> = mapOf(
        "barbell" to "barbell",
        "dumbbell" to "dumbbell",
        "cable" to "cable",
        "body only" to "bodyweight",
        "machine" to "machine",
        "kettlebells" to "kettlebell",
        "bands" to "band",
        "e-z curl bar" to "barbell",
    )

    /** Returns null only for unusable identity (blank name). */
    fun normalize(
        record: FreeExerciseDbRecord,
        importedAtEpochMs: Long? = null,
    ): NormalizedExerciseImport? {
        val name = record.name.trim()
        if (name.isBlank()) return null

        val diagnostics = mutableListOf<ImportDiagnostic>()

        // Muscles: first primary wins; the dataset's single multi-primary
        // record's remaining entries join the secondary set.
        val primaryNative = record.primaryMuscles.firstOrNull()?.trim()?.lowercase()
        val primary = primaryNative?.let { MUSCLE_MAP[it] }
        if (primary == null) {
            diagnostics += ImportDiagnostic(
                code = "unmapped_muscle",
                message = "Primary muscle could not be mapped",
                field = "primaryMuscles",
                sourceValue = primaryNative,
            )
        }
        val secondary = buildSet {
            addAll(record.primaryMuscles.drop(1))
            addAll(record.secondaryMuscles)
        }.mapNotNull { native ->
            val trimmed = native.trim().lowercase()
            MUSCLE_MAP[trimmed] ?: run {
                diagnostics += ImportDiagnostic(
                    code = "unmapped_muscle",
                    message = "Secondary muscle could not be mapped",
                    field = "secondaryMuscles",
                    sourceValue = native,
                )
                null
            }
        }.filter { it != primary }.toSet()

        // Equipment: unmapped and unspecified stay absent, never fabricated.
        val equipmentNative = record.equipment?.trim()?.lowercase()
        val equipment = when {
            equipmentNative == null -> {
                diagnostics += ImportDiagnostic(
                    code = "unspecified_equipment",
                    message = "Equipment not specified by source",
                    field = "equipment",
                )
                emptySet()
            }
            equipmentNative !in EQUIPMENT_MAP -> {
                diagnostics += ImportDiagnostic(
                    code = "unmapped_equipment",
                    message = "Equipment label could not be mapped",
                    field = "equipment",
                    sourceValue = record.equipment,
                )
                emptySet()
            }
            else -> setOf(EQUIPMENT_MAP.getValue(equipmentNative))
        }

        // Instructions: empty is allowed but reported.
        if (record.instructions.none { it.isNotBlank() }) {
            diagnostics += ImportDiagnostic(
                code = "missing_instructions",
                message = "Record has no instructions",
                field = "instructions",
            )
        }

        // Movement pattern: the shared name heuristics encode MuscleTome's
        // taxonomy (squat/hinge/…) which `force` cannot express; force is
        // only a fallback when the name yields OTHER.
        val pattern = MovementHeuristics.movementPattern(name).let { heuristic ->
            if (heuristic == MovementPattern.OTHER) {
                when (record.force?.trim()?.lowercase()) {
                    "pull" -> MovementPattern.PULL
                    "push" -> MovementPattern.PUSH
                    else -> MovementPattern.OTHER
                }
            } else {
                heuristic
            }
        }

        // Movement type: mechanic when present; documented default otherwise.
        val type = when (record.mechanic?.trim()?.lowercase()) {
            "isolation" -> MovementType.ISOLATION
            "compound" -> MovementType.COMPOUND
            else -> MovementType.COMPOUND
        }

        val media = record.images.map { path ->
            ExerciseMedia(
                type = ExerciseMediaType.IMAGE,
                uri = IMAGE_BASE + path,
                sourceKey = SOURCE_KEY,
                attribution = ATTRIBUTION,
                creator = ATTRIBUTION,
                licenseName = LICENSE_NAME,
                licenseUrl = LICENSE_URL,
            )
        }

        return NormalizedExerciseImport(
            exercise = CanonicalExercise(
                id = "fedb_${record.id}",
                displayName = name,
                description = null,
                instructions = record.instructions.map(String::trim).filter(String::isNotBlank),
                movementPattern = pattern,
                movementType = type,
                difficulty = when (record.level?.trim()?.lowercase()) {
                    "beginner" -> Difficulty.BEGINNER
                    "intermediate" -> Difficulty.INTERMEDIATE
                    "expert" -> Difficulty.ADVANCED
                    else -> Difficulty.INTERMEDIATE
                },
                unilateral = null,
                primaryMuscleGroupId = primary,
                secondaryMuscleGroupIds = secondary,
                equipmentIds = equipment,
                movementFamilyId = MovementFamilies.familyId(name, pattern),
                origin = ExerciseOrigin.IMPORTED,
            ),
            sourceIdentity = ExerciseSourceIdentity(
                sourceKey = SOURCE_KEY,
                externalExerciseId = record.id,
                sourceUrl = "https://github.com/yuhonas/free-exercise-db",
                importedAtEpochMs = importedAtEpochMs,
            ),
            sourceAttribution = ATTRIBUTION,
            media = media,
            diagnostics = diagnostics.toList(),
        )
    }
}
