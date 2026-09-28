package com.chy.muscletome.ui.library

import org.junit.Assert.assertEquals
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.domain.model.MovementType
import org.junit.Test

class ExerciseDetailContentTest {
    @Test fun detailProjectionKeepsInstructionOrderAndLegacyMediaFallbackData() {
        val exercise = ExerciseEntity("custom", "Custom", movementType = MovementType.COMPOUND, primaryMuscleGroupId = "chest", demoUri = "https://legacy/image.png")
        val canonical = ExerciseWithCanonicalRelations(
            exercise = exercise,
            metadata = CanonicalExerciseEntity("custom", "chest", instructions = "First\nSecond", origin = "USER_CREATED"),
            instructions = listOf(ExerciseInstructionEntity("custom", 1, "Second"), ExerciseInstructionEntity("custom", 0, "First")),
            equipment = emptyList(), secondaryTargets = emptyList(),
            media = listOf(ExerciseMediaEntity("image1", "custom", "IMAGE", "https://image/example.png", creator = "Creator", licenseName = "CC-BY")),
            sourceIdentities = emptyList(),
        )
        assertEquals(listOf("First", "Second"), canonical.instructions.sortedBy { it.sortOrder }.map { it.instruction })
        assertEquals("https://legacy/image.png", exercise.demoUri)
        assertEquals("Creator", canonical.media.single().creator)
        assertEquals("CC-BY", canonical.media.single().licenseName)
    }
}
