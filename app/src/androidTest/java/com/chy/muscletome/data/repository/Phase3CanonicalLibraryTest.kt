package com.chy.muscletome.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.MuscleTomeMigrations
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase3CanonicalLibraryTest {
    private lateinit var db: MuscleTomeDatabase
    private lateinit var dao: CatalogDao
    private lateinit var repo: CatalogRepository
    private lateinit var canonicalRepo: CanonicalExerciseRepository

    @Before fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MuscleTomeDatabase::class.java)
            .allowMainThreadQueries().addMigrations(*MuscleTomeMigrations.ALL).build()
        dao = db.catalogDao()
        repo = CatalogRepository(dao, db.routineDao(), db.workoutDao(), FamilyRepository(dao))
        lateinit var review: ExerciseImportReviewRepository
        canonicalRepo = CanonicalExerciseRepository(dao, Lazy { review })
        review = ExerciseImportReviewRepository(db, db.exerciseImportReviewDao(), canonicalRepo)
        db.userDao().upsert(UserEntity("local-user", "You"))
        dao.upsertMuscleGroups(listOf(
            MuscleGroupEntity("chest", "Chest"), MuscleGroupEntity("back", "Back"),
            MuscleGroupEntity("triceps", "Triceps"), MuscleGroupEntity("lats", "Lats"),
            MuscleGroupEntity("biceps", "Biceps"),
        ))
        dao.upsertEquipment(listOf(EquipmentEntity("barbell", "Barbell")))
        dao.insertExercises(listOf(
            exercise("family_row", "Row Family", "back", MovementType.COMPOUND),
            exercise("family_curl", "Curl Family", "back", MovementType.ISOLATION),
            exercise("row1", "Barbell Row", "lats", MovementType.COMPOUND),
            exercise("curl", "Barbell Curl", "biceps", MovementType.ISOLATION),
        ))
        dao.insertSecondaryMuscles(listOf(ExerciseSecondaryMuscleCrossRef("row1", "back")))
        dao.upsertCanonicalMetadata(listOf(
            CanonicalExerciseEntity("row1", "lats", movementFamilyId = "row", origin = "BUILT_IN"),
            CanonicalExerciseEntity("curl", "biceps", movementFamilyId = "curl", origin = "BUILT_IN"),
        ))
    }

    @After fun close() { db.close() }

    @Test fun secondaryTargetSearchIsDistinctAndCompoundFirst() = runBlocking {
        val rows = repo.searchLibraryRows("", "back").first()
        assertEquals(listOf("row1", "family_row", "family_curl"), rows.map { it.exercise.id })
        assertEquals(MovementType.COMPOUND, rows.first().exercise.movementType)
        assertEquals(2, repo.searchLibraryRows("row", "back").first().map { it.exercise.id }.distinct().size)
    }

    @Test fun familyTaxonomyGroupsSeedVariationsAndLeavesStandaloneUnknowns() {
        assertEquals("squat", MovementFamilies.familyId("Bulgarian Split Squat"))
        assertEquals("squat", MovementFamilies.familyId("Single-leg Dumbbell Squat"))
        assertEquals("bench_press", MovementFamilies.familyId("Dumbbell Bench Press"))
        assertNull(MovementFamilies.familyId("Reverse Pec Deck"))
    }

    @Test fun customCanonicalCreateThenExactSourceRefreshRemainsProtected() = runBlocking {
        assertEquals(
            CreateExerciseResult.SUCCESS,
            repo.createCustomExercise(
                name = "My Custom Press",
                description = "Personal variation",
                primaryMuscleGroupId = "chest",
                movementType = MovementType.COMPOUND,
                movementPattern = MovementPattern.PUSH,
                difficulty = Difficulty.BEGINNER,
                equipmentIds = listOf("barbell"),
                secondaryMuscleGroupIds = listOf("triceps"),
                instructions = listOf("Set shoulders", "Press smoothly"),
                unilateral = true,
            ),
        )
        val custom = dao.getExerciseByName("My Custom Press")!!
        assertTrue(custom.isCustom)
        val canonical = dao.getCanonicalExercise(custom.id)!!
        assertEquals("USER_CREATED", canonical.metadata?.origin)
        assertEquals(listOf("Set shoulders", "Press smoothly"), canonical.instructions.sortedBy { it.sortOrder }.map { it.instruction })
        assertEquals(setOf("triceps"), canonical.secondaryTargets.map { it.id }.toSet())
        assertEquals(setOf("barbell"), canonical.equipment.map { it.id }.toSet())
    }

    private fun exercise(id: String, name: String, muscle: String, type: MovementType) = ExerciseEntity(
        id = id, name = name, primaryMuscleGroupId = muscle, movementType = type,
        movementPattern = MovementPattern.PULL, difficulty = Difficulty.INTERMEDIATE,
    )
}
