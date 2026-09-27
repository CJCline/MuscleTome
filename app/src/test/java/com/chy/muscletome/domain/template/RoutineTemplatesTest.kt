package com.chy.muscletome.domain.template

import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Template integrity: first-run must not break because a template references
 * an exercise or muscle the seed catalog doesn't actually contain.
 */
class RoutineTemplatesTest {

    private val seedExerciseIds = SeedCatalog.exercises.map { it.id }.toSet()
    private val seedMuscleIds = SeedCatalog.muscleGroups.map { it.id }.toSet()

    @Test
    fun shipsFiveTemplates() {
        assertEquals(5, RoutineTemplates.ALL.size)
        assertEquals(5, RoutineTemplates.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun fixedSlotsReferenceSeedExercises() {
        RoutineTemplates.ALL.forEach { template ->
            template.days.forEach { day ->
                day.slots.filter { it.type == SlotType.FIXED }.forEach { slot ->
                    assertTrue(
                        "${template.id}/${day.name}: FIXED slot ${slot.exerciseId} is not in the seed catalog",
                        slot.exerciseId in seedExerciseIds,
                    )
                }
            }
        }
    }

    @Test
    fun fixedSlotsAlwaysHaveAnExercise() {
        RoutineTemplates.ALL.forEach { template ->
            template.days.flatMap { it.slots }
                .filter { it.type == SlotType.FIXED }
                .forEach { slot ->
                    assertTrue("${template.id}: FIXED slot without exerciseId", slot.exerciseId != null)
                }
        }
    }

    @Test
    fun targetSlotsUseKnownMuscles() {
        RoutineTemplates.ALL.forEach { template ->
            template.days.flatMap { it.slots }
                .filter { it.type == SlotType.TARGET }
                .forEach { slot ->
                    assertTrue("${template.id}: TARGET slot has no muscles", slot.targetMuscleIds.isNotEmpty())
                    slot.targetMuscleIds.forEach { muscle ->
                        assertTrue("${template.id}: unknown muscle $muscle", muscle in seedMuscleIds)
                    }
                }
        }
    }

    @Test
    fun everyTemplateShowsTheVarietyEngine() {
        RoutineTemplates.ALL.forEach { template ->
            assertTrue(
                "${template.id} has no TARGET slots — the variety engine would be invisible",
                template.days.flatMap { it.slots }.any { it.type == SlotType.TARGET },
            )
        }
    }

    @Test
    fun selectionFocusedProgramsHaveMostlyAutoPickedSlots() {
        listOf(
            RoutineTemplates.AUTO_PICKED_FULL_BODY_3X,
            RoutineTemplates.ROTATING_UPPER_LOWER_4X,
        ).forEach { template ->
            template.days.forEach { day ->
                val targetCount = day.slots.count { it.type == SlotType.TARGET }
                assertTrue("${template.id}/${day.name} should demonstrate selection", targetCount >= 3)
                assertEquals(SlotType.FIXED, day.slots.first().type)
            }
        }
    }

    @Test
    fun everyDayLeadsWithAFixedCompound() {
        RoutineTemplates.ALL.forEach { template ->
            template.days.forEach { day ->
                assertTrue(
                    "${template.id}/${day.name} must lead with a FIXED slot",
                    day.slots.first().type == SlotType.FIXED,
                )
            }
        }
    }

    @Test
    fun metricsAreSane() {
        RoutineTemplates.ALL.forEach { template ->
            template.days.forEach { day ->
                assertTrue("${template.id}/${day.name} has no slots", day.slots.isNotEmpty())
                day.slots.forEach { slot ->
                    assertTrue(slot.sets in 1..10)
                    assertTrue(slot.repRangeMin in 1..30)
                    assertTrue("rep max must cover rep min", slot.repRangeMax >= slot.repRangeMin)
                    assertTrue(slot.restSeconds in 15..600)
                }
            }
        }
    }

    @Test
    fun scratchIsEmptySoItCreatesNothing() {
        assertTrue(RoutineTemplates.SCRATCH.days.isEmpty())
    }
}
