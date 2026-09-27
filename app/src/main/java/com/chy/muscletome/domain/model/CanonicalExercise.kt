package com.chy.muscletome.domain.model

import java.text.Normalizer
import java.util.Locale

/** Source-independent identity and exercise facts used at import boundaries. */
data class CanonicalExercise(
    /** Stable MuscleTome identity; never an external provider's ID. */
    val id: String,
    val displayName: String,
    val description: String? = null,
    val instructions: List<String> = emptyList(),
    val movementPattern: MovementPattern? = null,
    val movementType: MovementType? = null,
    val difficulty: Difficulty? = null,
    val unilateral: Boolean? = null,
    val primaryMuscleGroupId: String? = null,
    val secondaryMuscleGroupIds: Set<String> = emptySet(),
    val equipmentIds: Set<String> = emptySet(),
    /** Optional movement-family link; each variation keeps its own [id]. */
    val movementFamilyId: String? = null,
    val origin: ExerciseOrigin = ExerciseOrigin.IMPORTED,
)

enum class ExerciseOrigin { BUILT_IN, IMPORTED, USER_CREATED }

enum class ExerciseMediaType { IMAGE, VIDEO, OTHER }

/** One media reference; Phase 1 records metadata only and never fetches it. */
data class ExerciseMedia(
    val type: ExerciseMediaType,
    /** URI or stable source-owned reference; may be absent for malformed records. */
    val uri: String? = null,
    val sourceKey: String? = null,
    val attribution: String? = null,
    val creator: String? = null,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
)

/** Provider identity and synchronization timestamps kept outside the exercise. */
data class ExerciseSourceIdentity(
    val sourceKey: String,
    val externalExerciseId: String,
    val sourceUrl: String? = null,
    val importedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null,
)

/** Normalized adapter output; adapters do not expose provider-specific schemas. */
data class NormalizedExerciseImport(
    val exercise: CanonicalExercise,
    val sourceIdentity: ExerciseSourceIdentity? = null,
    val sourceAttribution: String? = null,
    val media: List<ExerciseMedia> = emptyList(),
    val diagnostics: List<ImportDiagnostic> = emptyList(),
)

data class ImportDiagnostic(
    val code: String,
    val message: String,
    val field: String? = null,
)

/** Existing canonical facts plus local-edit protection status for resolution. */
data class ExistingCanonicalExercise(
    val exercise: CanonicalExercise,
    val sourceIdentities: List<ExerciseSourceIdentity> = emptyList(),
    val isUserEdited: Boolean = false,
)

sealed interface ExerciseImportIdentityDecision {
    /** Same source key + external ID: reuse this canonical exercise on re-import. */
    data class ReimportExisting(
        val canonicalExerciseId: String,
        val preserveLocalEdits: Boolean,
    ) : ExerciseImportIdentityDecision

    /** Similar cross-source data is surfaced for review, never auto-merged. */
    data class ReviewCandidates(
        val candidates: List<ExerciseMatchCandidate>,
    ) : ExerciseImportIdentityDecision

    /** No exact identity and no credible review candidate exists. */
    data object CreateNew : ExerciseImportIdentityDecision
}

data class ExerciseMatchCandidate(
    val canonicalExerciseId: String,
    val normalizedNameMatches: Boolean,
    val movementPatternMatches: Boolean,
    val equipmentOverlap: Boolean,
    val muscleTargetOverlap: Boolean,
)

/**
 * Conservative import identity policy. Exact provider identity is the only
 * automatic re-import key. Cross-source name/attribute similarities return
 * review candidates; they never merge or overwrite exercises.
 */
object ExerciseImportIdentityPolicy {
    fun resolve(
        incoming: NormalizedExerciseImport,
        existing: List<ExistingCanonicalExercise>,
    ): ExerciseImportIdentityDecision {
        val identity = incoming.sourceIdentity
        if (identity != null) {
            val exact = existing.firstOrNull { current ->
                current.sourceIdentities.any { sourceIdentity ->
                    sourceIdentity.sourceKey == identity.sourceKey &&
                        sourceIdentity.externalExerciseId == identity.externalExerciseId
                }
            }
            exact?.let {
                return ExerciseImportIdentityDecision.ReimportExisting(
                    canonicalExerciseId = it.exercise.id,
                    preserveLocalEdits = it.isUserEdited ||
                        it.exercise.origin == ExerciseOrigin.USER_CREATED,
                )
            }
        }

        val incomingExercise = incoming.exercise
        val incomingName = normalizeName(incomingExercise.displayName)
        val candidates = existing.mapNotNull { current ->
            val exercise = current.exercise
            val nameMatches = incomingName.isNotEmpty() &&
                incomingName == normalizeName(exercise.displayName)
            val patternMatches = incomingExercise.movementPattern != null &&
                incomingExercise.movementPattern == exercise.movementPattern
            val equipmentOverlaps = incomingExercise.equipmentIds.isNotEmpty() &&
                incomingExercise.equipmentIds.intersect(exercise.equipmentIds).isNotEmpty()
            val incomingMuscles = incomingExercise.targetMuscleIds()
            val existingMuscles = exercise.targetMuscleIds()
            val musclesOverlap = incomingMuscles.isNotEmpty() &&
                incomingMuscles.intersect(existingMuscles).isNotEmpty()

            // Require an exact normalized name and at least one attribute
            // corroborator. Similar names alone are not enough to suggest merge.
            if (nameMatches && (patternMatches || equipmentOverlaps || musclesOverlap)) {
                ExerciseMatchCandidate(
                    canonicalExerciseId = exercise.id,
                    normalizedNameMatches = true,
                    movementPatternMatches = patternMatches,
                    equipmentOverlap = equipmentOverlaps,
                    muscleTargetOverlap = musclesOverlap,
                )
            } else {
                null
            }
        }
        return if (candidates.isNotEmpty()) {
            ExerciseImportIdentityDecision.ReviewCandidates(candidates)
        } else {
            ExerciseImportIdentityDecision.CreateNew
        }
    }

    private fun CanonicalExercise.targetMuscleIds(): Set<String> = buildSet {
        primaryMuscleGroupId?.let(::add)
        addAll(secondaryMuscleGroupIds)
    }

    private fun normalizeName(name: String): String = Normalizer
        .normalize(name.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
