package com.chy.muscletome.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.SlotType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 5A/5B: family assignment for custom exercises, family-name duplicate
 * rejection, and in-place custom editing that preserves IDs and provenance.
 */
@RunWith(AndroidJUnit4::class)
class FamilyAndEditTest {
    private lateinit var db: MuscleTomeDatabase
    private lateinit var dao: CatalogDao
    private lateinit var repo: CatalogRepository
    private lateinit var families: FamilyRepository

    @Before fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MuscleTomeDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.catalogDao()
        families = FamilyRepository(dao)
        repo = CatalogRepository(dao, db.routineDao(), db.workoutDao(), families)
        db.userDao().upsert(UserEntity("local-user", "You"))
        dao.upsertMuscleGroups(listOf(
            MuscleGroupEntity("chest", "Chest"), MuscleGroupEntity("triceps", "Triceps"),
        ))
        dao.upsertEquipment(listOf(EquipmentEntity("barbell", "Barbell")))
        families.seedBuiltinFamilies()
    }

    @After fun close() { db.close() }

    @Test fun createFamilyRejectsDuplicateNormalizedNames() = runBlocking {
        val created = families.createFamily("Hip Thrust")
        assertTrue(created is FamilyRepository.CreateFamilyResult.Created)
        // Same name, different spelling → duplicate surfaced, no second row.
        val duplicate = families.createFamily("hip   thrust!")
        assertTrue(duplicate is FamilyRepository.CreateFamilyResult.Duplicate)
        assertEquals(1, families.listFamilies().count { it.displayName == "Hip Thrust" })
        // Seeded family can't be recreated either.
        val legacy = families.createFamily("Bench Press")
        assertTrue(legacy is FamilyRepository.CreateFamilyResult.Duplicate)
        assertEquals("bench_press", (legacy as FamilyRepository.CreateFamilyResult.Duplicate).existing.id)
    }

    @Test fun customExerciseJoinsFamilyAndFamilylessCustomStaysValid() = runBlocking {
        assertEquals(
            CreateExerciseResult.SUCCESS,
            repo.createCustomExercise(
                name = "My Press",
                description = "",
                primaryMuscleGroupId = "chest",
                movementType = MovementType.COMPOUND,
                movementPattern = MovementPattern.PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                equipmentIds = emptyList(),
                secondaryMuscleGroupIds = emptyList(),
                movementFamilyId = "bench_press",
            ),
        )
        val custom = dao.getExerciseByName("My Press")!!
        assertEquals("bench_press", dao.getCanonicalExercise(custom.id)!!.metadata!!.movementFamilyId)

        repo.createCustomExercise(
            name = "Familyless Move",
            description = "",
            primaryMuscleGroupId = "chest",
            movementType = MovementType.ISOLATION,
            movementPattern = MovementPattern.OTHER,
            difficulty = Difficulty.BEGINNER,
            equipmentIds = emptyList(),
            secondaryMuscleGroupIds = emptyList(),
        )
        val bare = dao.getExerciseByName("Familyless Move")!!
        assertNull(dao.getCanonicalExercise(bare.id)!!.metadata!!.movementFamilyId)
        // Familyless custom exercises still appear in library search.
        assertTrue(repo.searchLibraryRows("", null).first().any { it.exercise.id == bare.id })
    }

    @Test fun editCustomExercisePreservesIdAndIdentitiesAndProtectsFromImport() = runBlocking {
        assertEquals(
            CreateExerciseResult.SUCCESS,
            repo.createCustomExercise(
                name = "Old Name",
                description = "old",
                primaryMuscleGroupId = "chest",
                movementType = MovementType.COMPOUND,
                movementPattern = MovementPattern.PUSH,
                difficulty = Difficulty.BEGINNER,
                equipmentIds = listOf("barbell"),
                secondaryMuscleGroupIds = listOf("triceps"),
                instructions = listOf("step one"),
            ),
        )
        val original = dao.getExerciseByName("Old Name")!!
        // Attach a source identity (e.g. backup-merged provenance) to prove it survives.
        dao.upsertSourceIdentities(listOf(
            ExerciseSourceIdentityEntity("wger", "999", original.id),
        ))

        assertEquals(
            CreateExerciseResult.SUCCESS,
            repo.updateCustomExercise(
                exerciseId = original.id,
                name = "New Name",
                description = "new desc",
                primaryMuscleGroupId = "chest",
                movementType = MovementType.ISOLATION,
                movementPattern = MovementPattern.OTHER,
                difficulty = Difficulty.ADVANCED,
                equipmentIds = emptyList(),
                secondaryMuscleGroupIds = emptyList(),
                instructions = listOf("updated step"),
                movementFamilyId = "plank",
            ),
        )

        // Same exercise row, renamed; same canonical ID.
        val edited = dao.getExerciseByName("New Name")!!
        assertEquals(original.id, edited.id)
        assertNull(dao.getExerciseByName("Old Name"))
        assertEquals("new desc", edited.description)
        assertEquals(Difficulty.ADVANCED, edited.difficulty)

        val canonical = dao.getCanonicalExercise(edited.id)!!
        assertEquals("plank", canonical.metadata!!.movementFamilyId)
        assertTrue(canonical.metadata.isUserEdited)
        assertEquals(listOf("updated step"), canonical.instructions.map { it.instruction })
        // Provenance untouched.
        assertEquals("wger", canonical.sourceIdentities.single().sourceKey)
        assertEquals("999", canonical.sourceIdentities.single().externalExerciseId)

        // A later name-clash rename attempt on another exercise is refused.
        assertEquals(
            CreateExerciseResult.SUCCESS,
            repo.createCustomExercise(
                name = "Another One",
                description = "",
                primaryMuscleGroupId = "chest",
                movementType = MovementType.COMPOUND,
                movementPattern = MovementPattern.PUSH,
                difficulty = Difficulty.BEGINNER,
                equipmentIds = emptyList(),
                secondaryMuscleGroupIds = emptyList(),
            ),
        )
        assertEquals(
            CreateExerciseResult.NAME_TAKEN,
            repo.updateCustomExercise(
                exerciseId = dao.getExerciseByName("Another One")!!.id,
                name = "New Name", // clashes with the edited exercise
                description = "",
                primaryMuscleGroupId = "chest",
                movementType = MovementType.COMPOUND,
                movementPattern = MovementPattern.PUSH,
                difficulty = Difficulty.BEGINNER,
                equipmentIds = emptyList(),
                secondaryMuscleGroupIds = emptyList(),
            ),
        )
    }

    @Test fun deleteBlockedWhenReferencedAndAllowedOtherwise() = runBlocking {
        repo.createCustomExercise(
            name = "Deleteme",
            description = "",
            primaryMuscleGroupId = "chest",
            movementType = MovementType.COMPOUND,
            movementPattern = MovementPattern.PUSH,
            difficulty = Difficulty.BEGINNER,
            equipmentIds = emptyList(),
            secondaryMuscleGroupIds = emptyList(),
        )
        val target = dao.getExerciseByName("Deleteme")!!
        dao.upsertExercises(listOf(target))
        // Insert a routine referencing the exercise (schema requires routine/day rows).
        db.routineDao().insertRoutines(listOf(
            RoutineEntity(
                id = "r1", name = "Routine", ownerId = "local-user", createdAtEpochMs = 0,
            ),
        ))
        db.routineDao().insertDays(listOf(
            RoutineDayEntity(
                id = "d1", routineId = "r1", name = "Day", orderIndex = 0,
            ),
        ))
        db.routineDao().insertSlots(listOf(
            RoutineSlotEntity(
                id = "s1", routineDayId = "d1", orderIndex = 0,
                type = SlotType.FIXED,
                exerciseId = target.id, sets = 3, repRangeMin = 8, repRangeMax = 12, restSeconds = 90,
            ),
        ))
        assertTrue(repo.isReferenced(target.id))
        assertFalse(repo.deleteCustomExercise(target.id))
        assertNotNull(dao.getExercise(target.id))

        db.openHelper.writableDatabase.execSQL("DELETE FROM routine_slots WHERE id = 's1'")
        assertFalse(repo.isReferenced(target.id))
        assertTrue(repo.deleteCustomExercise(target.id))
        assertNull(dao.getExercise(target.id))
        // Cascades cleaned canonical + media rows.
        assertNull(dao.getCanonicalExercise(target.id))
    }
}
