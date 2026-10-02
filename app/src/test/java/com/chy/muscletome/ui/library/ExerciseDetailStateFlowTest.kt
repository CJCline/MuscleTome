package com.chy.muscletome.ui.library

import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.WeightUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDetailStateFlowTest {
    @Test fun canonicalExerciseCombinesEveryFieldWithoutPositionalCasts() = runBlocking {
        val inputs = Inputs()
        // This actual production flow used to cast the canonical relation to List.
        val state = withTimeout(5_000) { inputs.states.first() }

        assertEquals(
            ExerciseDetailUiState(
                exercise = inputs.exercise.value,
                canonical = inputs.canonical.value,
                userOwned = true,
                deleteBlockedMessage = "Referenced by a workout",
                deleted = false,
                cachedMediaUris = mapOf("image" to "/cache/image.png"),
                downloadableMedia = true,
                mediaFullyCached = false,
                equipment = listOf(EquipmentEntity("barbell", "Barbell")),
                secondaryMuscles = listOf(MuscleGroupEntity("triceps", "Triceps")),
                history = inputs.history.value,
                primaryMuscleName = "Chest",
                loaded = true,
                weightUnit = WeightUnit.LB,
            ),
            state,
        )
        assertEquals("image", state.canonical!!.media.single().id)
    }

    @Test fun absentCanonicalUsesLegacyOwnershipAndMissingUserDefaultsToKg() = runBlocking {
        val inputs = Inputs()
        inputs.canonical.value = null
        inputs.user.value = null
        inputs.exercise.value = inputs.exercise.value!!.copy(isCustom = true)

        val custom = withTimeout(5_000) { inputs.states.first() }
        assertTrue(custom.userOwned)
        assertEquals(null, custom.canonical)
        assertEquals(WeightUnit.KG, custom.weightUnit)
        assertEquals(inputs.equipment.value, custom.equipment)
        assertEquals(inputs.secondaryMuscles.value, custom.secondaryMuscles)
        assertEquals("Chest", custom.primaryMuscleName)
        assertEquals(inputs.history.value, custom.history)

        inputs.exercise.value = inputs.exercise.value!!.copy(isCustom = false)
        assertFalse(withTimeout(5_000) { inputs.states.first() }.userOwned)
    }

    @Test fun canonicalMetadataControlsOwnershipWithLegacyFallbackWhenAbsent() = runBlocking {
        val inputs = Inputs()
        val canonical = inputs.canonical.value!!
        val metadata = canonical.metadata!!
        inputs.canonical.value = canonical.copy(metadata = metadata.copy(origin = "BUILT_IN"))
        inputs.exercise.value = inputs.exercise.value!!.copy(isCustom = true)
        assertFalse(withTimeout(5_000) { inputs.states.first() }.userOwned)

        inputs.canonical.value = canonical.copy(
            metadata = metadata.copy(origin = "BUILT_IN", isUserEdited = true),
        )
        inputs.exercise.value = inputs.exercise.value!!.copy(isCustom = false)
        assertTrue(withTimeout(5_000) { inputs.states.first() }.userOwned)

        inputs.canonical.value = canonical.copy(metadata = null)
        assertFalse(withTimeout(5_000) { inputs.states.first() }.userOwned)
        inputs.exercise.value = inputs.exercise.value!!.copy(isCustom = true)
        assertTrue(withTimeout(5_000) { inputs.states.first() }.userOwned)
    }

    @Test fun missingExerciseIsLoadedAndUnknownPrimaryMuscleHasEmptyName() = runBlocking {
        val inputs = Inputs()
        inputs.muscleGroups.value = emptyList()
        assertEquals("", withTimeout(5_000) { inputs.states.first() }.primaryMuscleName)

        inputs.exercise.value = null
        inputs.canonical.value = null
        inputs.equipment.value = emptyList()
        inputs.secondaryMuscles.value = emptyList()
        inputs.history.value = ExerciseHistory()
        inputs.blocked.value = null
        inputs.deleted.value = true
        inputs.cached.value = emptyMap()
        inputs.downloadable.value = false
        inputs.fullyCached.value = false
        inputs.user.value = null

        assertEquals(
            ExerciseDetailUiState(loaded = true, deleted = true),
            withTimeout(5_000) { inputs.states.first() },
        )
    }

    @Test fun upstreamUpdatesPropagateThroughAllTypedStages() = runBlocking {
        val inputs = Inputs()
        val emissions = Channel<ExerciseDetailUiState>(Channel.UNLIMITED)
        val collection = launch { inputs.states.collect { emissions.send(it) } }
        suspend fun awaitState(predicate: (ExerciseDetailUiState) -> Boolean): ExerciseDetailUiState =
            withTimeout(5_000) {
                var state = emissions.receive()
                while (!predicate(state)) state = emissions.receive()
                state
            }

        try {
            val initial = awaitState { it.loaded }
            inputs.history.value = ExerciseHistory(bestWeight = 120.0, sessionCount = 8)
            val historyUpdated = awaitState { it.history == inputs.history.value }
            assertEquals(initial.copy(history = inputs.history.value), historyUpdated)

            inputs.blocked.value = null
            inputs.deleted.value = true
            val deleted = awaitState { it.deleted && it.deleteBlockedMessage == null }
            assertEquals(historyUpdated.copy(deleted = true, deleteBlockedMessage = null), deleted)

            inputs.cached.value = mapOf("image" to "/cache/new.png", "video" to "/cache/video.mp4")
            inputs.downloadable.value = false
            inputs.fullyCached.value = true
            val mediaUpdated = awaitState {
                it.cachedMediaUris == inputs.cached.value && !it.downloadableMedia && it.mediaFullyCached
            }
            assertEquals(
                deleted.copy(cachedMediaUris = inputs.cached.value, downloadableMedia = false, mediaFullyCached = true),
                mediaUpdated,
            )

            inputs.user.value = inputs.user.value!!.copy(weightUnit = WeightUnit.KG)
            val kg = awaitState { it.weightUnit == WeightUnit.KG }
            assertEquals(mediaUpdated.copy(weightUnit = WeightUnit.KG), kg)
            inputs.user.value = inputs.user.value!!.copy(weightUnit = WeightUnit.LB)
            awaitState { it.weightUnit == WeightUnit.LB }
            inputs.user.value = null
            assertEquals(kg, awaitState { it.weightUnit == WeightUnit.KG })

            inputs.exercise.value = inputs.exercise.value!!.copy(primaryMuscleGroupId = "back", isCustom = true)
            inputs.canonical.value = null
            inputs.equipment.value = listOf(EquipmentEntity("cable", "Cable"))
            inputs.secondaryMuscles.value = listOf(MuscleGroupEntity("biceps", "Biceps"))
            inputs.muscleGroups.value = listOf(MuscleGroupEntity("back", "Back"))
            val catalogUpdated = awaitState {
                it.exercise == inputs.exercise.value && it.canonical == null &&
                    it.equipment == inputs.equipment.value && it.secondaryMuscles == inputs.secondaryMuscles.value &&
                    it.primaryMuscleName == "Back"
            }
            assertEquals(
                kg.copy(
                    exercise = inputs.exercise.value,
                    canonical = null,
                    equipment = inputs.equipment.value,
                    secondaryMuscles = inputs.secondaryMuscles.value,
                    primaryMuscleName = "Back",
                    userOwned = true,
                ),
                catalogUpdated,
            )
        } finally {
            collection.cancel()
            emissions.cancel()
        }
    }

    private class Inputs {
        val exercise = MutableStateFlow<ExerciseEntity?>(
            ExerciseEntity("bench", "Bench press", movementType = MovementType.COMPOUND, primaryMuscleGroupId = "chest"),
        )
        val canonical = MutableStateFlow<ExerciseWithCanonicalRelations?>(
            ExerciseWithCanonicalRelations(
                exercise = exercise.value!!,
                metadata = CanonicalExerciseEntity("bench", "chest", origin = "USER_CREATED"),
                instructions = emptyList(),
                // Deliberately distinct from the separately observed lists below.
                equipment = emptyList(),
                secondaryTargets = emptyList(),
                media = listOf(ExerciseMediaEntity("image", "bench", "IMAGE", "https://example.com/image.png")),
                sourceIdentities = emptyList(),
            ),
        )
        val equipment = MutableStateFlow(listOf(EquipmentEntity("barbell", "Barbell")))
        val secondaryMuscles = MutableStateFlow(listOf(MuscleGroupEntity("triceps", "Triceps")))
        val muscleGroups = MutableStateFlow(
            listOf(MuscleGroupEntity("back", "Back"), MuscleGroupEntity("chest", "Chest")),
        )
        val history = MutableStateFlow(
            ExerciseHistory(
                lastSet = SetLogEntity("set", "slot", 2, 100.0, 6, completedAtEpochMs = 123L),
                bestWeight = 110.0,
                sessionCount = 7,
            ),
        )
        val blocked = MutableStateFlow<String?>("Referenced by a workout")
        val deleted = MutableStateFlow(false)
        val cached = MutableStateFlow(mapOf("image" to "/cache/image.png"))
        val downloadable = MutableStateFlow(true)
        val fullyCached = MutableStateFlow(false)
        val user = MutableStateFlow<UserEntity?>(UserEntity("user", "Lifter", weightUnit = WeightUnit.LB))
        val activeSessionSlots = MutableStateFlow<List<com.chy.muscletome.data.repository.ActiveSlotOverrideOption>>(emptyList())
        val states = exerciseDetailStateFlow(
            exercise = exercise,
            canonical = canonical,
            equipment = equipment,
            secondaryMuscles = secondaryMuscles,
            muscleGroups = muscleGroups,
            history = history,
            deleteBlockedMessage = blocked,
            deleted = deleted,
            cachedMediaUris = cached,
            downloadable = downloadable,
            fullyCached = fullyCached,
            user = user,
            activeSessionSlots = activeSessionSlots,
        )
    }
}
