package com.chy.muscletome.domain.selection

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.TargetMovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VarietyEngineTest {

    /** Fixed "now" so recency scoring is deterministic. */
    private val now: Long = 1_760_000_000_000L
    private val engine = VarietyEngine(Random(0), now = { now })

    @Test
    fun picksOnlyCandidateThatHitsTargetMuscle() {
        val pick = engine.pick(
            request(targetMuscleIds = setOf("chest")),
            listOf(
                candidate("bench", primary = "chest", equipment = setOf("barbell")),
                candidate("squat", primary = "quads", equipment = setOf("barbell")),
            ),
        )
        assertNotNull(pick)
        assertEquals("bench", pick?.exercise?.id)
    }

    @Test
    fun rejectsWrongEquipment() {
        val pick = engine.pick(
            request(
                targetMuscleIds = setOf("chest"),
                availableEquipmentIds = setOf("band"),
            ),
            listOf(candidate("bench", primary = "chest", equipment = setOf("barbell"))),
        )
        assertNull(pick)
    }

    @Test
    fun rejectsExcludedExercise() {
        val pick = engine.pick(
            request(
                targetMuscleIds = setOf("chest"),
                excludedExerciseIds = setOf("bench"),
            ),
            listOf(candidate("bench", primary = "chest", equipment = setOf("barbell"))),
        )
        assertNull(pick)
    }

    @Test
    fun rejectsAlreadyPickedExercise() {
        val pick = engine.pick(
            request(
                targetMuscleIds = setOf("chest"),
                alreadyPickedIds = setOf("bench"),
            ),
            listOf(candidate("bench", primary = "chest", equipment = setOf("barbell"))),
        )
        assertNull(pick)
    }

    @Test
    fun movementTypeFilterDropsCompoundsWhenIsolationRequested() {
        val pick = engine.pick(
            request(
                targetMuscleIds = setOf("chest"),
                targetMovementType = TargetMovementType.ISOLATION,
            ),
            listOf(
                candidate("bench", primary = "chest", type = MovementType.COMPOUND, equipment = setOf("barbell")),
                candidate("fly", primary = "chest", type = MovementType.ISOLATION, equipment = setOf("cable")),
            ),
        )
        assertEquals("fly", pick?.exercise?.id)
    }

    @Test
    fun secondaryMuscleCanMatch() {
        val pick = engine.pick(
            request(
                targetMuscleIds = setOf("triceps"),
                matchStrictness = MatchStrictness.LOOSE,
            ),
            listOf(
                candidate(
                    "bench",
                    primary = "chest",
                    secondary = setOf("triceps"),
                    equipment = setOf("barbell"),
                ),
            ),
        )
        assertEquals("bench", pick?.exercise?.id)
        assertEquals(false, pick?.isPrimaryMatch)
    }

    @Test
    fun difficultyFilterDropsAdvancedWhenMaxIsBeginner() {
        val pick = engine.pick(
            request(
                targetMuscleIds = setOf("hamstrings"),
                maxDifficulty = Difficulty.BEGINNER,
            ),
            listOf(
                candidate(
                    "deadlift",
                    primary = "hamstrings",
                    difficulty = Difficulty.ADVANCED,
                    equipment = setOf("barbell"),
                ),
            ),
        )
        assertNull(pick)
    }

    @Test
    fun emptyCatalogReturnsNull() {
        assertNull(engine.pick(request(targetMuscleIds = setOf("chest")), emptyList()))
    }

    @Test
    fun higherAffinityBeatsUnusedPeerWhenOtherScoresEqual() {
        val low = candidate("a", primary = "chest", equipment = setOf("barbell"), affinity = -1f)
        val high = candidate("b", primary = "chest", equipment = setOf("barbell"), affinity = 1f)
        val wins = mutableMapOf<String, Int>()
        repeat(30) {
            val pick = VarietyEngine(Random(it)).pick(
                request(targetMuscleIds = setOf("chest")),
                listOf(low, high),
            )
            wins[pick!!.exercise.id] = (wins[pick.exercise.id] ?: 0) + 1
        }
        assertTrue((wins["b"] ?: 0) > (wins["a"] ?: 0))
    }

    @Test
    fun neverUsedScoresFullFreshness() {
        assertEquals(1.0, engine.recencyScore(null), 1e-9)
    }

    @Test
    fun usedTodayScoresZeroFreshness() {
        assertEquals(0.0, engine.recencyScore(now), 1e-9)
    }

    @Test
    fun twoWeeksStaleRecoversFullFreshness() {
        assertEquals(1.0, engine.recencyScore(now - 14L * 86_400_000L), 1e-9)
    }

    @Test
    fun neverUsedOutscoresRecentlyUsedWhenOtherwiseEqual() {
        val recent = candidate("recent", primary = "chest", equipment = setOf("barbell"), lastUsedAt = now)
        val unseen = candidate("unseen", primary = "chest", equipment = setOf("barbell"), lastUsedAt = null)
        val recentScore = engine.pick(request(targetMuscleIds = setOf("chest")), listOf(recent))!!.score
        val unseenScore = engine.pick(request(targetMuscleIds = setOf("chest")), listOf(unseen))!!.score
        assertTrue(unseenScore > recentScore)
    }

    private fun request(
        targetMuscleIds: Set<String>,
        targetMovementType: TargetMovementType = TargetMovementType.ANY,
        availableEquipmentIds: Set<String> = setOf("barbell", "cable", "band", "dumbbell", "machine", "bodyweight"),
        excludedExerciseIds: Set<String> = emptySet(),
        alreadyPickedIds: Set<String> = emptySet(),
        maxDifficulty: Difficulty = Difficulty.ADVANCED,
        matchStrictness: MatchStrictness = MatchStrictness.LOOSE,
    ) = EngineRequest(
        targetMuscleIds = targetMuscleIds,
        targetMovementType = targetMovementType,
        availableEquipmentIds = availableEquipmentIds,
        excludedExerciseIds = excludedExerciseIds,
        alreadyPickedIds = alreadyPickedIds,
        maxDifficulty = maxDifficulty,
        matchStrictness = matchStrictness,
        preferCompoundEarly = true,
        slotIndex = 0,
    )

    private fun candidate(
        id: String,
        primary: String,
        secondary: Set<String> = emptySet(),
        equipment: Set<String>,
        type: MovementType = MovementType.COMPOUND,
        difficulty: Difficulty = Difficulty.INTERMEDIATE,
        affinity: Float = 0f,
        lastUsedAt: Long? = null,
    ) = EngineCandidate(
        exercise = ExerciseEntity(
            id = id,
            name = id,
            movementPattern = MovementPattern.OTHER,
            movementType = type,
            primaryMuscleGroupId = primary,
            difficulty = difficulty,
            source = ExerciseSource.SEED,
        ),
        equipmentIds = equipment,
        secondaryMuscleIds = secondary,
        lastUsedAtEpochMs = lastUsedAt,
        affinity = affinity,
    )
}
