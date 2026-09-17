package com.chy.regimen.domain.model

enum class MovementPattern { PUSH, PULL, HINGE, SQUAT, CARRY, OTHER }

enum class MovementType { COMPOUND, ISOLATION }

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

enum class ExerciseSource { SEED, USER_CREATED }

enum class SlotType { FIXED, TARGET }

enum class TargetMovementType { COMPOUND, ISOLATION, ANY }

enum class SelectionReason { FIXED, AI_ROTATED, USER_OVERRIDE, USER_REROLL }

enum class WeightUnit { KG, LB }

enum class MatchStrictness { STRICT, LOOSE }

enum class SubscriptionStatus { FREE, VARIETY, LLM }