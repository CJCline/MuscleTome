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
