package com.chy.muscletome.data.local.seed

import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType

object SeedCatalog {
    const val LOCAL_USER_ID = "local-user"

    val muscleGroups = listOf(
        MuscleGroupEntity("chest", "Chest"),
        MuscleGroupEntity("back", "Back"),
        MuscleGroupEntity("shoulders", "Shoulders"),
        MuscleGroupEntity("arms", "Arms"),
        MuscleGroupEntity("legs", "Legs"),
        MuscleGroupEntity("core", "Core"),
        MuscleGroupEntity("lats", "Lats", "back"),
        MuscleGroupEntity("traps", "Traps", "back"),
        MuscleGroupEntity("rhomboids", "Rhomboids", "back"),
        MuscleGroupEntity("front_delts", "Front Delts", "shoulders"),
        MuscleGroupEntity("side_delts", "Side Delts", "shoulders"),
        MuscleGroupEntity("rear_delts", "Rear Delts", "shoulders"),
        MuscleGroupEntity("biceps", "Biceps", "arms"),
        MuscleGroupEntity("triceps", "Triceps", "arms"),
        MuscleGroupEntity("quads", "Quads", "legs"),
        MuscleGroupEntity("hamstrings", "Hamstrings", "legs"),
        MuscleGroupEntity("glutes", "Glutes", "legs"),
        MuscleGroupEntity("calves", "Calves", "legs"),
        MuscleGroupEntity("abs", "Abs", "core"),
    )

    val equipment = listOf(
        EquipmentEntity("barbell", "Barbell"),
        EquipmentEntity("dumbbell", "Dumbbell"),
        EquipmentEntity("cable", "Cable"),
        EquipmentEntity("machine", "Machine"),
        EquipmentEntity("bodyweight", "Bodyweight"),
        EquipmentEntity("band", "Band"),
    )

    val exercises = listOf(
        ex("barbell_bench_press", "Barbell Bench Press", MovementPattern.PUSH, MovementType.COMPOUND, "chest", Difficulty.INTERMEDIATE),
        ex("dumbbell_bench_press", "Dumbbell Bench Press", MovementPattern.PUSH, MovementType.COMPOUND, "chest", Difficulty.INTERMEDIATE),
        ex("push_up", "Push-up", MovementPattern.PUSH, MovementType.COMPOUND, "chest", Difficulty.BEGINNER),
        ex("cable_fly", "Cable Fly", MovementPattern.PUSH, MovementType.ISOLATION, "chest", Difficulty.BEGINNER),
        ex("overhead_press", "Overhead Press", MovementPattern.PUSH, MovementType.COMPOUND, "front_delts", Difficulty.INTERMEDIATE),
        ex("lateral_raise", "Dumbbell Lateral Raise", MovementPattern.OTHER, MovementType.ISOLATION, "side_delts", Difficulty.BEGINNER),
        ex("rear_delt_fly", "Rear Delt Fly", MovementPattern.PULL, MovementType.ISOLATION, "rear_delts", Difficulty.BEGINNER),
        ex("pull_up", "Pull-up", MovementPattern.PULL, MovementType.COMPOUND, "lats", Difficulty.INTERMEDIATE),
        ex("lat_pulldown", "Lat Pulldown", MovementPattern.PULL, MovementType.COMPOUND, "lats", Difficulty.BEGINNER),
        ex("barbell_row", "Barbell Row", MovementPattern.PULL, MovementType.COMPOUND, "lats", Difficulty.INTERMEDIATE),
        ex("squat", "Barbell Back Squat", MovementPattern.SQUAT, MovementType.COMPOUND, "quads", Difficulty.INTERMEDIATE),
        ex("romanian_deadlift", "Romanian Deadlift", MovementPattern.HINGE, MovementType.COMPOUND, "hamstrings", Difficulty.INTERMEDIATE),
        ex("deadlift", "Conventional Deadlift", MovementPattern.HINGE, MovementType.COMPOUND, "hamstrings", Difficulty.ADVANCED),
        ex("leg_press", "Leg Press", MovementPattern.SQUAT, MovementType.COMPOUND, "quads", Difficulty.BEGINNER),
        ex("leg_extension", "Leg Extension", MovementPattern.SQUAT, MovementType.ISOLATION, "quads", Difficulty.BEGINNER),
        ex("leg_curl", "Lying Leg Curl", MovementPattern.HINGE, MovementType.ISOLATION, "hamstrings", Difficulty.BEGINNER),
        ex("barbell_curl", "Barbell Curl", MovementPattern.PULL, MovementType.ISOLATION, "biceps", Difficulty.BEGINNER),
        ex("tricep_pushdown", "Tricep Pushdown", MovementPattern.PUSH, MovementType.ISOLATION, "triceps", Difficulty.BEGINNER),
        ex("plank", "Plank", MovementPattern.OTHER, MovementType.ISOLATION, "abs", Difficulty.BEGINNER),
    )

    val exerciseEquipment = listOf(
        ExerciseEquipmentCrossRef("barbell_bench_press", "barbell"),
        ExerciseEquipmentCrossRef("dumbbell_bench_press", "dumbbell"),
        ExerciseEquipmentCrossRef("push_up", "bodyweight"),
        ExerciseEquipmentCrossRef("cable_fly", "cable"),
        ExerciseEquipmentCrossRef("overhead_press", "barbell"),
        ExerciseEquipmentCrossRef("lateral_raise", "dumbbell"),
        ExerciseEquipmentCrossRef("rear_delt_fly", "dumbbell"),
        ExerciseEquipmentCrossRef("pull_up", "bodyweight"),
        ExerciseEquipmentCrossRef("lat_pulldown", "machine"),
        ExerciseEquipmentCrossRef("barbell_row", "barbell"),
        ExerciseEquipmentCrossRef("squat", "barbell"),
        ExerciseEquipmentCrossRef("romanian_deadlift", "barbell"),
        ExerciseEquipmentCrossRef("deadlift", "barbell"),
        ExerciseEquipmentCrossRef("leg_press", "machine"),
        ExerciseEquipmentCrossRef("leg_extension", "machine"),
        ExerciseEquipmentCrossRef("leg_curl", "machine"),
        ExerciseEquipmentCrossRef("barbell_curl", "barbell"),
        ExerciseEquipmentCrossRef("tricep_pushdown", "cable"),
        ExerciseEquipmentCrossRef("plank", "bodyweight"),
    )

    val secondaryMuscles = listOf(
        ExerciseSecondaryMuscleCrossRef("barbell_bench_press", "front_delts"),
        ExerciseSecondaryMuscleCrossRef("barbell_bench_press", "triceps"),
        ExerciseSecondaryMuscleCrossRef("overhead_press", "triceps"),
        ExerciseSecondaryMuscleCrossRef("pull_up", "biceps"),
        ExerciseSecondaryMuscleCrossRef("barbell_row", "biceps"),
        ExerciseSecondaryMuscleCrossRef("squat", "glutes"),
        ExerciseSecondaryMuscleCrossRef("romanian_deadlift", "glutes"),
        ExerciseSecondaryMuscleCrossRef("deadlift", "glutes"),
        ExerciseSecondaryMuscleCrossRef("deadlift", "back"),
    )

    private fun ex(
        id: String,
        name: String,
        pattern: MovementPattern,
        type: MovementType,
        primary: String,
        difficulty: Difficulty,
    ) = ExerciseEntity(
        id = id,
        name = name,
        movementPattern = pattern,
        movementType = type,
        primaryMuscleGroupId = primary,
        difficulty = difficulty,
        source = ExerciseSource.SEED,
    )
}