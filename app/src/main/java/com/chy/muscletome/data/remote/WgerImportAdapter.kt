package com.chy.muscletome.data.remote

import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.ImportDiagnostic
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import com.chy.muscletome.domain.model.MovementFamilies

/** Maps an already-parsed wger record into the source-neutral import contract. */
object WgerImportAdapter {
    fun normalize(
        item: WgerExerciseInfo,
        importedAtEpochMs: Long? = null,
    ): NormalizedExerciseImport? {
        val name = item.englishName?.trim().orEmpty()
        if (name.isBlank()) return null

        val mappedPrimary = item.muscles.firstNotNullOfOrNull { WgerMapper.muscleId(it.nameEn) }
        val secondaryFallback = if (mappedPrimary == null) {
            item.musclesSecondary.firstNotNullOfOrNull { sourceMuscle ->
                WgerMapper.muscleId(sourceMuscle.nameEn)?.let { sourceMuscle.nameEn to it }
            }
        } else {
            null
        }
        val primary = mappedPrimary ?: secondaryFallback?.second
        val diagnostics = mutableListOf<ImportDiagnostic>()
        if (mappedPrimary == null && secondaryFallback != null) {
            diagnostics += ImportDiagnostic(
                code = "primary_muscle_from_secondary_fallback",
                message = "Primary muscle was taken from a mapped secondary muscle",
                field = "muscles_secondary",
                sourceValue = secondaryFallback.first,
            )
        }
        if (primary == null) {
            diagnostics += ImportDiagnostic(
                "unmapped_primary_muscle",
                "No muscle target could be mapped",
                "muscles",
            )
        }
        if (!item.hasEnglish) {
            diagnostics += ImportDiagnostic(
                "missing_english_translation",
                "Record has no English translation",
                "name",
            )
        }
        val secondary = item.musclesSecondary
            .mapNotNull { WgerMapper.muscleId(it.nameEn) }
            .filter { it != primary }
            .toSet()
        val equipmentIds = item.equipment.mapNotNull { equipment ->
            WgerMapper.knownEquipmentId(equipment.name).also { mapped ->
                if (mapped == null) {
                    diagnostics += ImportDiagnostic(
                        code = "unmapped_equipment",
                        message = "Equipment label could not be mapped",
                        field = "equipment",
                        sourceValue = equipment.name,
                    )
                }
            }
        }.toSet()
        val media = item.mainImageUrl?.trim()?.takeIf(String::isNotEmpty)?.let { uri ->
            listOf(
                ExerciseMedia(
                    type = ExerciseMediaType.IMAGE,
                    uri = uri,
                    sourceKey = SOURCE_KEY,
                    attribution = item.licenseAuthor,
                    creator = item.licenseAuthor,
                    licenseName = item.licenseName,
                ),
            )
        }.orEmpty()

        return NormalizedExerciseImport(
            exercise = CanonicalExercise(
                id = WgerMapper.exerciseId(item.id),
                displayName = name,
                description = item.englishDescription?.takeIf(String::isNotBlank),
                movementPattern = WgerMapper.movementPattern(name, item.categoryName),
                movementType = WgerMapper.movementType(name, item.categoryName),
                difficulty = WgerMapper.difficulty(name),
                primaryMuscleGroupId = primary,
                secondaryMuscleGroupIds = secondary,
                equipmentIds = equipmentIds,
                movementFamilyId = MovementFamilies.familyId(name, WgerMapper.movementPattern(name, item.categoryName)),
                origin = ExerciseOrigin.IMPORTED,
            ),
            sourceIdentity = ExerciseSourceIdentity(
                sourceKey = SOURCE_KEY,
                externalExerciseId = item.id.toString(),
                sourceUrl = "https://wger.de/api/v2/exerciseinfo/${item.id}/",
                importedAtEpochMs = importedAtEpochMs,
            ),
            sourceAttribution = buildString {
                append("Source: wger.de")
                item.licenseName?.let { append(" · $it") }
                item.licenseAuthor?.let { append(" · $it") }
            },
            media = media,
            diagnostics = diagnostics.toList(),
        )
    }

    const val SOURCE_KEY = "wger"
}
