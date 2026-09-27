package com.chy.muscletome.data.backup

import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
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
    fun v3BackupWithoutSelectionHistoryImportsWithEmptyHistory() {
        val legacy = emptyDocument().copy(formatVersion = 3)
        val legacyJson = JsonObject(
            json.encodeToJsonElement(BackupDocument.serializer(), legacy).jsonObject - "selectionHistory" +
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
    )
}
