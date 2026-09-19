package com.chy.muscletome.domain.selection

import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.TargetMovementType
import javax.inject.Inject
import kotlin.random.Random

data class EngineCandidate(
    val exercise: ExerciseEntity,
    val equipmentIds: Set<String>,
    val secondaryMuscleIds: Set<String>,
    val lastUsedAtEpochMs: Long? = null,
    val affinity: Float = 0f,
)

data class EngineRequest(
    val targetMuscleIds: Set<String>,
    val targetMovementType: TargetMovementType,
    val availableEquipmentIds: Set<String>,
    val excludedExerciseIds: Set<String>,
    val alreadyPickedIds: Set<String>,
    val maxDifficulty: Difficulty,
    val matchStrictness: MatchStrictness,
    val preferCompoundEarly: Boolean,
    val slotIndex: Int,
)

data class EnginePick(
    val exercise: ExerciseEntity,
    val isPrimaryMatch: Boolean,
    val score: Double,
)

class VarietyEngine @Inject constructor(
    private val random: Random = Random.Default
) {

    fun pickWithFallback(
        request: EngineRequest,
        catalog: List<EngineCandidate>,
        muscleParents: Map<String, String?> = emptyMap(),
    ): EnginePick? {
        // 1. Full request
        pick(request, catalog)?.let { return it }

        // 2. Allow duplicates
        val noDuplicates = request.copy(alreadyPickedIds = emptySet())
        pick(noDuplicates, catalog)?.let { return it }

        // 3. Relax movement type
        val anyMovement = noDuplicates.copy(targetMovementType = TargetMovementType.ANY)
        pick(anyMovement, catalog)?.let { return it }

        // 4. Relax difficulty
        val anyDifficulty = anyMovement.copy(maxDifficulty = Difficulty.ADVANCED)
        pick(anyDifficulty, catalog)?.let { return it }

        // 5. Relax strictness + Widen muscle targets
        val widerMuscles = request.targetMuscleIds.flatMap { id ->
            listOfNotNull(id, muscleParents[id])
        }.toSet()
        val finalRequest = anyDifficulty.copy(
            targetMuscleIds = widerMuscles,
            matchStrictness = MatchStrictness.LOOSE,
        )
        return pick(finalRequest, catalog)
    }

    fun pick(request: EngineRequest, catalog: List<EngineCandidate>): EnginePick? {
        val difficultyOk = request.maxDifficulty.ordinal
        val filtered = catalog.filter { candidate ->
            val exercise = candidate.exercise
            val muscles = buildSet {
                add(exercise.primaryMuscleGroupId)
                addAll(candidate.secondaryMuscleIds)
            }
            val hitsMuscle = request.targetMuscleIds.any { it in muscles }
            val hitsEquipment = candidate.equipmentIds.isEmpty() ||
                    candidate.equipmentIds.any { it in request.availableEquipmentIds }
            val hitsMovement = request.targetMovementType == TargetMovementType.ANY ||
                    exercise.movementType.name == request.targetMovementType.name
            val allowed = exercise.id !in request.excludedExerciseIds &&
                    exercise.id !in request.alreadyPickedIds &&
                    exercise.difficulty.ordinal <= difficultyOk
            hitsMuscle && hitsEquipment && hitsMovement && allowed
        }
        if (filtered.isEmpty()) return null

        val scored = filtered.map { candidate ->
            val primary = candidate.exercise.primaryMuscleGroupId in request.targetMuscleIds
            val recency = recencyScore(candidate.lastUsedAtEpochMs)
            val primaryBonus = if (primary) 1.0 else secondaryPenalty(request.matchStrictness)
            val movementFit = movementFit(
                type = candidate.exercise.movementType,
                preferCompoundEarly = request.preferCompoundEarly,
                slotIndex = request.slotIndex,
            )
            val score = (0.30 * recency) +
                    (0.30 * primaryBonus) +
                    (0.20 * movementFit) +
                    (0.20 * ((candidate.affinity + 1f) / 2f).toDouble())
            EnginePick(candidate.exercise, primary, score)
        }.sortedByDescending { it.score }

        val top = scored.take(5)
        val weights = top.map { kotlin.math.exp(it.score) }
        val total = weights.sum()
        var cursor = random.nextDouble() * total
        top.zip(weights).forEach { (pick, weight) ->
            cursor -= weight
            if (cursor <= 0) return pick
        }
        return top.first()
    }

    private fun recencyScore(lastUsed: Long?): Double {
        if (lastUsed == null) return 0.7
        val days = (System.currentTimeMillis() - lastUsed) / 86_400_000.0
        return (days / 14.0).coerceIn(0.0, 1.0)
    }

    private fun secondaryPenalty(strictness: MatchStrictness): Double =
        if (strictness == MatchStrictness.STRICT) 0.05 else 0.65

    private fun movementFit(type: MovementType, preferCompoundEarly: Boolean, slotIndex: Int): Double {
        if (!preferCompoundEarly) return 0.5
        val wantCompound = slotIndex < 2
        return when {
            wantCompound && type == MovementType.COMPOUND -> 1.0
            !wantCompound && type == MovementType.ISOLATION -> 1.0
            else -> 0.35
        }
    }
}