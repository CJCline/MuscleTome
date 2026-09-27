package com.chy.muscletome.domain.model

import com.chy.muscletome.data.remote.WgerExerciseInfo
import com.chy.muscletome.data.remote.WgerEquipment
import com.chy.muscletome.data.remote.WgerImportAdapter
import com.chy.muscletome.data.remote.WgerMuscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalExerciseImportTest {

    @Test
    fun canonicalImportAllowsMissingOptionalMetadata() {
        val imported = NormalizedExerciseImport(
            exercise = CanonicalExercise(id = "imported_variation", displayName = "Cable Press"),
        )

        assertEquals("Cable Press", imported.exercise.displayName)
        assertNull(imported.exercise.description)
        assertNull(imported.exercise.movementPattern)
        assertTrue(imported.exercise.instructions.isEmpty())
        assertTrue(imported.media.isEmpty())
        assertNull(imported.sourceIdentity)
    }

    @Test
    fun wgerAdapterCreatesSourceNeutralRecordAndKeepsUnmappedDataWithDiagnostics() {
        val normalized = WgerImportAdapter.normalize(
            WgerExerciseInfo(
                id = 500,
                categoryName = "Chest",
                muscles = listOf(WgerMuscle(1, "Unmapped muscle")),
                musclesSecondary = listOf(WgerMuscle(2, "Pectoralis major")),
                equipment = listOf(WgerEquipment(1, "Cable")),
                licenseAuthor = "Creator",
                licenseName = "CC-BY-SA",
                englishName = "Cable Fly Variation",
                englishDescription = "Description",
                hasEnglish = true,
                mainImageUrl = "https://example.test/demo.jpg",
            ),
            importedAtEpochMs = 123L,
        ) ?: error("Valid name should be importable")

        assertEquals("wger_500", normalized.exercise.id)
        assertEquals("chest", normalized.exercise.primaryMuscleGroupId)
        assertEquals(setOf("cable"), normalized.exercise.equipmentIds)
        assertEquals("wger", normalized.sourceIdentity?.sourceKey)
        assertEquals("500", normalized.sourceIdentity?.externalExerciseId)
        assertEquals(123L, normalized.sourceIdentity?.importedAtEpochMs)
        assertEquals(1, normalized.media.size)
        assertEquals("CC-BY-SA", normalized.media.single().licenseName)
        assertEquals(
            "Pectoralis major",
            normalized.diagnostics.single { it.code == "primary_muscle_from_secondary_fallback" }.sourceValue,
        )
    }

    @Test
    fun repeatedWgerImportKeepsStableNamespacedCanonicalId() {
        val record = wgerRecord(id = 600, muscles = listOf(WgerMuscle(1, "Pectoralis major")))

        val first = WgerImportAdapter.normalize(record)
        val second = WgerImportAdapter.normalize(record.copy(englishDescription = "Updated description"))

        assertEquals("wger_600", first?.exercise?.id)
        assertEquals(first?.exercise?.id, second?.exercise?.id)
        assertEquals("600", first?.sourceIdentity?.externalExerciseId)
        assertEquals(first?.sourceIdentity, second?.sourceIdentity)
    }

    @Test
    fun knownEquipmentMapsToExistingIdWithoutDiagnostic() {
        val normalized = WgerImportAdapter.normalize(
            wgerRecord(id = 601, equipment = listOf(WgerEquipment(1, "Cable machine"))),
        ) ?: error("Valid record should be importable")

        assertEquals(setOf("cable"), normalized.exercise.equipmentIds)
        assertTrue(normalized.diagnostics.none { it.code == "unmapped_equipment" })
    }

    @Test
    fun unknownEquipmentIsRetainedAsDiagnosticAndNotAConfirmedEquipmentId() {
        val normalized = WgerImportAdapter.normalize(
            wgerRecord(id = 602, equipment = listOf(WgerEquipment(9, "Suspension trainer XYZ"))),
        ) ?: error("Valid record should be importable")
        val diagnostic = normalized.diagnostics.single { it.code == "unmapped_equipment" }

        assertEquals("Suspension trainer XYZ", diagnostic.sourceValue)
        assertTrue(normalized.exercise.equipmentIds.isEmpty())
    }

    @Test
    fun mappedPrimaryMuscleDoesNotEmitFallbackDiagnostic() {
        val normalized = WgerImportAdapter.normalize(
            wgerRecord(
                id = 603,
                muscles = listOf(WgerMuscle(1, "Pectoralis major")),
                musclesSecondary = listOf(WgerMuscle(2, "Triceps")),
            ),
        ) ?: error("Valid record should be importable")

        assertEquals("chest", normalized.exercise.primaryMuscleGroupId)
        assertTrue(normalized.diagnostics.none { it.code == "primary_muscle_from_secondary_fallback" })
    }

    @Test
    fun secondaryMuscleFallbackIsAssignedAndReported() {
        val normalized = WgerImportAdapter.normalize(
            wgerRecord(
                id = 604,
                muscles = listOf(WgerMuscle(1, "Unmapped primary")),
                musclesSecondary = listOf(WgerMuscle(2, "Triceps")),
            ),
        ) ?: error("Valid record should be importable")
        val diagnostic = normalized.diagnostics.single { it.code == "primary_muscle_from_secondary_fallback" }

        assertEquals("triceps", normalized.exercise.primaryMuscleGroupId)
        assertEquals("Triceps", diagnostic.sourceValue)
    }

    @Test
    fun noMappableMusclesStillReportsUnmappedPrimary() {
        val normalized = WgerImportAdapter.normalize(
            wgerRecord(
                id = 605,
                muscles = listOf(WgerMuscle(1, "Unknown primary")),
                musclesSecondary = listOf(WgerMuscle(2, "Unknown secondary")),
            ),
        ) ?: error("Valid record should be importable")

        assertNull(normalized.exercise.primaryMuscleGroupId)
        assertTrue(normalized.diagnostics.any { it.code == "unmapped_primary_muscle" })
        assertTrue(normalized.diagnostics.none { it.code == "primary_muscle_from_secondary_fallback" })
    }

    @Test
    fun userCreatedExactSourceMatchPreservesLocalEdits() {
        val existing = ExistingCanonicalExercise(
            exercise = CanonicalExercise(
                id = "user_local_exercise",
                displayName = "Custom Fly",
                origin = ExerciseOrigin.USER_CREATED,
            ),
            sourceIdentities = listOf(ExerciseSourceIdentity("wger", "606")),
        )
        val incoming = NormalizedExerciseImport(
            exercise = CanonicalExercise(id = "wger_606", displayName = "Custom Fly"),
            sourceIdentity = ExerciseSourceIdentity("wger", "606"),
        )

        assertEquals(
            ExerciseImportIdentityDecision.ReimportExisting("user_local_exercise", preserveLocalEdits = true),
            ExerciseImportIdentityPolicy.resolve(incoming, listOf(existing)),
        )
    }

    @Test
    fun missingEnglishTranslationIsReportedWithoutRejectingNamedRecord() {
        val normalized = WgerImportAdapter.normalize(
            wgerRecord(id = 607, hasEnglish = false),
        ) ?: error("Named record should still be representable")

        assertEquals("wger_607", normalized.exercise.id)
        assertTrue(normalized.diagnostics.any { it.code == "missing_english_translation" })
    }

    @Test
    fun wgerAdapterRetainsRecordWhenMusclesCannotBeMappedAndAddsDiagnostic() {
        val normalized = WgerImportAdapter.normalize(
            WgerExerciseInfo(
                id = 501,
                categoryName = null,
                muscles = listOf(WgerMuscle(1, "Unknown")),
                musclesSecondary = emptyList(),
                equipment = emptyList(),
                licenseAuthor = null,
                licenseName = null,
                englishName = "Unknown Muscle Exercise",
                englishDescription = null,
                hasEnglish = true,
                mainImageUrl = null,
            ),
        ) ?: error("Valid name should be importable")

        assertEquals("wger_501", normalized.exercise.id)
        assertNull(normalized.exercise.primaryMuscleGroupId)
        assertTrue(normalized.diagnostics.any { it.code == "unmapped_primary_muscle" })
    }

    private fun wgerRecord(
        id: Int,
        muscles: List<WgerMuscle> = listOf(WgerMuscle(1, "Pectoralis major")),
        musclesSecondary: List<WgerMuscle> = emptyList(),
        equipment: List<WgerEquipment> = emptyList(),
        hasEnglish: Boolean = true,
    ) = WgerExerciseInfo(
        id = id,
        categoryName = "Chest",
        muscles = muscles,
        musclesSecondary = musclesSecondary,
        equipment = equipment,
        licenseAuthor = null,
        licenseName = null,
        englishName = "Exercise $id",
        englishDescription = null,
        hasEnglish = hasEnglish,
        mainImageUrl = null,
    )

    @Test
    fun exactSourceIdentityResolvesSameCanonicalExerciseAndProtectsUserEdits() {
        val existing = ExistingCanonicalExercise(
            exercise = CanonicalExercise(
                id = "stable_muscletome_id",
                displayName = "Cable Fly",
                origin = ExerciseOrigin.IMPORTED,
            ),
            sourceIdentities = listOf(ExerciseSourceIdentity("wger", "12")),
            isUserEdited = true,
        )
        val incoming = NormalizedExerciseImport(
            exercise = CanonicalExercise(id = "wger_12", displayName = "Cable Fly"),
            sourceIdentity = ExerciseSourceIdentity("wger", "12"),
        )

        assertEquals(
            ExerciseImportIdentityDecision.ReimportExisting("stable_muscletome_id", preserveLocalEdits = true),
            ExerciseImportIdentityPolicy.resolve(incoming, listOf(existing)),
        )
    }

    @Test
    fun crossSourceSimilarNameAndAttributesBecomeReviewCandidateNotMerge() {
        val existing = ExistingCanonicalExercise(
            exercise = CanonicalExercise(
                id = "cable_fly_existing",
                displayName = "Cable Fly",
                movementPattern = MovementPattern.PUSH,
                primaryMuscleGroupId = "chest",
                equipmentIds = setOf("cable"),
                origin = ExerciseOrigin.BUILT_IN,
            ),
        )
        val incoming = NormalizedExerciseImport(
            exercise = CanonicalExercise(
                id = "source_specific_id",
                displayName = "Cable Fly",
                movementPattern = MovementPattern.PUSH,
                primaryMuscleGroupId = "chest",
                equipmentIds = setOf("cable"),
            ),
            sourceIdentity = ExerciseSourceIdentity("another-source", "fly-1"),
        )

        val decision = ExerciseImportIdentityPolicy.resolve(incoming, listOf(existing))

        assertTrue(decision is ExerciseImportIdentityDecision.ReviewCandidates)
        assertEquals(
            "cable_fly_existing",
            (decision as ExerciseImportIdentityDecision.ReviewCandidates).candidates.single().canonicalExerciseId,
        )
    }

    @Test
    fun similarButDifferentVariationIsNotAutomaticallyMerged() {
        val existing = ExistingCanonicalExercise(
            exercise = CanonicalExercise(
                id = "barbell_bench_press",
                displayName = "Bench Press",
                movementPattern = MovementPattern.PUSH,
                primaryMuscleGroupId = "chest",
                equipmentIds = setOf("barbell"),
            ),
        )
        val incoming = NormalizedExerciseImport(
            exercise = CanonicalExercise(
                id = "dumbbell_bench_variation",
                displayName = "Dumbbell Bench Press",
                movementPattern = MovementPattern.PUSH,
                primaryMuscleGroupId = "chest",
                equipmentIds = setOf("dumbbell"),
            ),
            sourceIdentity = ExerciseSourceIdentity("another-source", "variation-2"),
        )

        assertEquals(
            ExerciseImportIdentityDecision.CreateNew,
            ExerciseImportIdentityPolicy.resolve(incoming, listOf(existing)),
        )
    }
}
