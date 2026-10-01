package com.chy.muscletome.data.backup

import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseImportResolutionEntity
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.UserEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupDocumentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun selectionHistoryIsIncludedAndRestoredByDocumentSerialization() {
        val history = ExerciseSelectionHistoryEntity(
            userId = "local-user",
            exerciseId = "row_exercise",
            muscleGroupId = "chest",
            lastUsedAtEpochMs = 1_700_000_000_000,
            useCount30d = 4,
            useCount90d = 9,
            completedCount = 7,
            rerollCount = 2,
            affinity = 0.75f,
        )
        val source = emptyDocument().copy(selectionHistory = listOf(history))

        val restored = json.decodeFromString<BackupDocument>(json.encodeToString(source))

        assertEquals(listOf(history), restored.selectionHistory)
    }

    @Test
    fun canonicalExerciseDataRoundTripsInBackupDocument() {
        val source = emptyDocument().copy(
            canonicalExerciseMetadata = listOf(
                CanonicalExerciseEntity("row", "chest", "Step one", "row", "IMPORTED", true),
            ),
            pendingExerciseImports = listOf(
                PendingExerciseImportEntity(
                    id = "pending-1", sourceKey = "wger", externalExerciseId = "88",
                    displayName = "Row", pattern = "PULL", muscleIdsJson = "[]", equipmentIdsJson = "[]",
                    payloadJson = "{}", candidateExerciseIdsJson = "[\"row\"]", createdAtEpochMs = 1L,
                ),
            ),
            exerciseImportResolutions = listOf(
                ExerciseImportResolutionEntity("resolved-1", "DISCARD", null, 2L, "wger", "77"),
            ),
            canonicalInstructions = listOf(ExerciseInstructionEntity("row", 0, "Brace")),
            canonicalEquipment = listOf(ExerciseEquipmentCrossRef("row", "barbell")),
            canonicalSecondaryTargets = listOf(ExerciseSecondaryTargetEntity("row", "triceps")),
            exerciseMedia = listOf(
                ExerciseMediaEntity("row-media", "row", "IMAGE", "content://image", "source", "credit", "author", "CC0", null, 0),
            ),
            exerciseSourceIdentities = listOf(
                ExerciseSourceIdentityEntity("wger", "77", "row", "https://source.test/77", 10L, 20L),
            ),
        )

        val restored = json.decodeFromString<BackupDocument>(json.encodeToString(source))

        assertEquals(source.canonicalExerciseMetadata, restored.canonicalExerciseMetadata)
        assertEquals(source.canonicalInstructions, restored.canonicalInstructions)
        assertEquals(source.canonicalEquipment, restored.canonicalEquipment)
        assertEquals(source.canonicalSecondaryTargets, restored.canonicalSecondaryTargets)
        assertEquals(source.exerciseMedia, restored.exerciseMedia)
        assertEquals(source.exerciseSourceIdentities, restored.exerciseSourceIdentities)
        assertEquals(source.pendingExerciseImports, restored.pendingExerciseImports)
        assertEquals(source.exerciseImportResolutions, restored.exerciseImportResolutions)
        assertEquals("row", restored.canonicalExerciseMetadata.single().movementFamilyId)
    }

    @Test
    fun freeExerciseDbProvenanceRoundTripsInBackupDocument() {
        val source = emptyDocument().copy(
            canonicalExerciseMetadata = listOf(
                CanonicalExerciseEntity("fedb_Barbell_Squat", "quads", "Descend", "squat", "IMPORTED", false),
            ),
            exerciseMedia = listOf(
                ExerciseMediaEntity(
                    id = "fedb_Barbell_Squat_media_0.jpg",
                    exerciseId = "fedb_Barbell_Squat",
                    type = "IMAGE",
                    uri = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Squat/0.jpg",
                    sourceKey = "free_exercise_db",
                    attribution = "free-exercise-db (yuhonas)",
                    creator = "free-exercise-db (yuhonas)",
                    licenseName = "Unlicense",
                    licenseUrl = "https://unlicense.org/",
                ),
            ),
            exerciseSourceIdentities = listOf(
                ExerciseSourceIdentityEntity(
                    "free_exercise_db", "Barbell_Squat", "fedb_Barbell_Squat",
                    "https://github.com/yuhonas/free-exercise-db", 10L, 20L,
                ),
            ),
            pendingExerciseImports = listOf(
                PendingExerciseImportEntity(
                    id = "free_exercise_db:Barbell_Curl", sourceKey = "free_exercise_db",
                    externalExerciseId = "Barbell_Curl", displayName = "Barbell Curl",
                    pattern = "PULL", muscleIdsJson = "[]", equipmentIdsJson = "[]",
                    payloadJson = "{}", candidateExerciseIdsJson = "[\"barbell_curl\"]", createdAtEpochMs = 1L,
                ),
            ),
            exerciseImportResolutions = listOf(
                ExerciseImportResolutionEntity("free_exercise_db:Plank", "DISCARD", null, 2L, "free_exercise_db", "Plank"),
            ),
        )

        val restored = json.decodeFromString<BackupDocument>(json.encodeToString(source))

        assertEquals(source.canonicalExerciseMetadata, restored.canonicalExerciseMetadata)
        assertEquals(source.exerciseMedia, restored.exerciseMedia)
        assertEquals(source.exerciseSourceIdentities, restored.exerciseSourceIdentities)
        assertEquals(source.pendingExerciseImports, restored.pendingExerciseImports)
        assertEquals(source.exerciseImportResolutions, restored.exerciseImportResolutions)
        assertEquals("Unlicense", restored.exerciseMedia.single().licenseName)
        assertEquals("https://unlicense.org/", restored.exerciseMedia.single().licenseUrl)
    }

    @Test
    fun v3BackupWithoutSelectionHistoryImportsWithEmptyHistory() {
        val legacy = emptyDocument().copy(formatVersion = 3)
        val legacyJson = JsonObject(
            (json.encodeToJsonElement(BackupDocument.serializer(), legacy).jsonObject - "selectionHistory" -
                "exerciseImportResolutions" - "pendingExerciseImports") +
                ("formatVersion" to JsonPrimitive(3)),
        )

        val restored = json.decodeFromString<BackupDocument>(legacyJson.toString())

        requireSupportedBackupVersion(restored.formatVersion)
        assertEquals(3, restored.formatVersion)
        assertEquals(emptyList<ExerciseSelectionHistoryEntity>(), restored.selectionHistory)
    }

    @Test
    fun mediaCacheRowsAreExcludedFromBackupDocument() {
        // Phase 5C: cached media files are device-local and license-gated on
        // fetch; the backup document carries metadata only (URI, attribution,
        // license), and a restored device re-fetches on demand. The document
        // schema therefore must have no media-cache field at all.
        val serialized = json.encodeToString(emptyDocument())
        assertFalse(serialized.contains("exercise_media_cache"))
        assertFalse(serialized.contains("mediaCache"))
        // Metadata rows still round-trip untouched (regression guard).
        val withMedia = emptyDocument().copy(
            exerciseMedia = listOf(
                ExerciseMediaEntity(
                    id = "fedb_1_img", exerciseId = "fedb_1", type = "IMAGE",
                    uri = "https://example/1.jpg", sourceKey = "free_exercise_db",
                    attribution = "free-exercise-db (yuhonas)", licenseName = "Unlicense",
                ),
            ),
        )
        val restored = json.decodeFromString<BackupDocument>(json.encodeToString(withMedia))
        assertEquals(withMedia.exerciseMedia, restored.exerciseMedia)
    }

    @Test
    fun userDefaultRepPreferenceRoundTripsInBackupDocument() {
        val userWithMaxReps = UserEntity(
            id = "local-user",
            name = "You",
            defaultRepPreference = com.chy.muscletome.domain.model.DefaultRepPreference.MAXIMUM,
        )
        val doc = emptyDocument().copy(user = userWithMaxReps)
        val restored = json.decodeFromString<BackupDocument>(json.encodeToString(doc))
        assertEquals(com.chy.muscletome.domain.model.DefaultRepPreference.MAXIMUM, restored.user.defaultRepPreference)
    }

    @Test
    fun rejectsBackupFromNewerUnsupportedFormat() {
        val error = assertThrows(IllegalStateException::class.java) {
            requireSupportedBackupVersion(BackupDocument.FORMAT_VERSION + 1)
        }
        assertEquals(true, error.message?.contains("newer than this app supports"))
    }

    private fun emptyDocument() = BackupDocument(
        formatVersion = BackupDocument.FORMAT_VERSION,
        exportedAtEpochMs = 1L,
        user = UserEntity(id = "local-user", name = "You"),
        muscleGroups = emptyList(),
        equipment = emptyList(),
        exercises = emptyList(),
        exerciseEquipment = emptyList(),
        secondaryMuscles = emptyList(),
        availableEquipment = emptyList(),
        excludedExercises = emptyList(),
        volumeTargets = emptyList(),
        routines = emptyList(),
        routineDays = emptyList(),
        routineSlots = emptyList(),
        slotTargets = emptyList(),
        sessions = emptyList(),
        slotResults = emptyList(),
        setLogs = emptyList(),
        canonicalExerciseMetadata = emptyList(),
        canonicalInstructions = emptyList(),
        canonicalEquipment = emptyList(),
        canonicalSecondaryTargets = emptyList(),
        exerciseMedia = emptyList(),
        exerciseSourceIdentities = emptyList(),
    )
}
