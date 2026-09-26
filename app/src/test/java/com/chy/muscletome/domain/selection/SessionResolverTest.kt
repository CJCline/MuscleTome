package com.chy.muscletome.domain.selection

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.TargetMovementType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session-start resolution: FIXED slots pass through, TARGET slots go through
 * the variety engine with per-session duplicate avoidance, and an unresolvable
 * slot stops the session with a user-facing label.
 */
class SessionResolverTest {

    private val now = 1_760_000_000_000L
    private val resolver = SessionResolver(VarietyEngine(Random(0), now = { now }))

    private val muscles = listOf(
        MuscleGroupEntity("chest", "Chest"),
        MuscleGroupEntity("biceps", "Biceps"),
    )

    private val catalog = listOf(
        candidate("bench", "chest", setOf("barbell"), type = MovementType.COMPOUND),
        candidate("curl", "biceps", setOf("dumbbell")),
        candidate("hammer", "biceps", setOf("dumbbell")),
        candidate("cable_curl", "biceps", setOf("cable")),
    )

    private fun resolve(
        slots: List<RoutineSlotEntity>,
        targets: Map<String, Set<String>> = emptyMap(),
        available: Set<String> = setOf("barbell", "dumbbell", "cable"),
        excluded: Set<String> = emptySet(),
        maxDifficulty: Difficulty = Difficulty.ADVANCED,
        strictness: MatchStrictness = MatchStrictness.LOOSE,
    ) = resolver.resolve(
        slots = slots,
        targetsBySlotId = targets,
        catalog = catalog,
        muscleGroups = muscles,
        availableEquipmentIds = available,
        excludedExerciseIds = excluded,
        maxDifficulty = maxDifficulty,
        matchStrictness = strictness,
        preferCompoundEarly = true,
    )

    private fun fixedSlot(id: String, exerciseId: String?) =
        RoutineSlotEntity(
            id = id, routineDayId = "d1", orderIndex = 0,
            type = SlotType.FIXED, exerciseId = exerciseId,
            targetMovementType = TargetMovementType.ANY,
            sets = 3, repRangeMin = 8, repRangeMax = 12, restSeconds = 90,
        )

    private fun targetSlot(id: String) =
        RoutineSlotEntity(
            id = id, routineDayId = "d1", orderIndex = 0,
            type = SlotType.TARGET, exerciseId = null,
            targetMovementType = TargetMovementType.ANY,
            sets = 3, repRangeMin = 8, repRangeMax = 12, restSeconds = 60,
        )

    @Test
    fun fixedSlotPassesThroughWithFixedReason() {
        val result = resolve(listOf(fixedSlot("s1", "bench")))
        assertTrue(result is SlotResolution.Success)
        val slots = (result as SlotResolution.Success).slots
        assertEquals(1, slots.size)
        assertEquals("bench", slots[0].exerciseId)
        assertEquals(SelectionReason.FIXED, slots[0].reason)
    }

    @Test
    fun fixedSlotWithoutExerciseIsNoMatch() {
        val result = resolve(listOf(fixedSlot("s1", null)))
        assertEquals("Fixed slot missing exercise", (result as SlotResolution.NoMatch).slotLabel)
    }

    @Test
    fun targetSlotsResolveViaEngineWithoutDuplicates() {
        // Two biceps targets in one session must not resolve to the same exercise.
        val result = resolve(
            slots = listOf(targetSlot("t1"), targetSlot("t2")),
            targets = mapOf("t1" to setOf("biceps"), "t2" to setOf("biceps")),
        )
        assertTrue(result is SlotResolution.Success)
        val slots = (result as SlotResolution.Success).slots
        assertEquals(2, slots.size)
        assertEquals(2, slots.map { it.exerciseId }.distinct().size)
        assertTrue(slots.all { it.reason == SelectionReason.AI_ROTATED })
    }

    @Test
    fun unresolvableTargetReportsMuscleName() {
        val result = resolve(
            slots = listOf(targetSlot("t1")),
            targets = mapOf("t1" to setOf("biceps")),
            available = emptySet(), // no equipment → engine can never match
        )
        val label = (result as SlotResolution.NoMatch).slotLabel
        assertTrue("Expected muscle name in label, got: $label", label.startsWith("Biceps"))
    }

    @Test
    fun excludedExercisesAreNeverResolved() {
        val result = resolve(
            slots = listOf(targetSlot("t1")),
            targets = mapOf("t1" to setOf("biceps")),
            excluded = catalog.map { it.exercise.id }.toSet(),
        )
        assertTrue(result is SlotResolution.NoMatch)
    }

    @Test
    fun mixedDayResolvesInSlotOrder() {
        val result = resolve(
            slots = listOf(
                fixedSlot("f1", "bench"),
                targetSlot("t1"),
            ),
            targets = mapOf("t1" to setOf("biceps")),
        )
        val slots = (result as SlotResolution.Success).slots
        assertEquals(listOf("f1", "t1"), slots.map { it.slotId })
        assertEquals(SelectionReason.FIXED, slots[0].reason)
        assertEquals(SelectionReason.AI_ROTATED, slots[1].reason)
    }

    private fun candidate(
        id: String,
        primary: String,
        equipment: Set<String>,
        type: MovementType = MovementType.COMPOUND,
    ) = EngineCandidate(
        exercise = ExerciseEntity(
            id = id,
            name = id,
            movementPattern = MovementPattern.OTHER,
            movementType = type,
            primaryMuscleGroupId = primary,
            difficulty = Difficulty.INTERMEDIATE,
            source = ExerciseSource.SEED,
        ),
        equipmentIds = equipment,
        secondaryMuscleIds = emptySet(),
        lastUsedAtEpochMs = null,
        affinity = 0f,
    )
}
