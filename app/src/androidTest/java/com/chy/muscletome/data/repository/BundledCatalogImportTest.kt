package com.chy.muscletome.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.MuscleTomeMigrations
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseImportReviewDao
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.data.remote.FreeExerciseDbImportAdapter
import com.chy.muscletome.data.remote.FreeExerciseDbRecord
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 4 end-to-end: bundled free-exercise-db catalog through the canonical
 * import pipeline. Fixtures are tiny in tests (the real 657-record asset is
 * exercised by the manual verification); these prove the wiring and rules.
 */
@RunWith(AndroidJUnit4::class)
class BundledCatalogImportTest {
    private lateinit var context: Context
    private lateinit var db: MuscleTomeDatabase
    private lateinit var dao: CatalogDao
    private lateinit var reviewDao: ExerciseImportReviewDao
    private lateinit var canonical: CanonicalExerciseRepository

    @Before fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MuscleTomeDatabase::class.java)
            .allowMainThreadQueries().addMigrations(*MuscleTomeMigrations.ALL).build()
        dao = db.catalogDao()
        reviewDao = db.exerciseImportReviewDao()
        db.userDao().upsert(SeedCatalog.LOCAL_USER_ID.let { UserEntity(it, "You") })
        dao.upsertMuscleGroups(SeedCatalog.muscleGroups)
        dao.upsertEquipment(SeedCatalog.equipment)
        dao.insertExercises(SeedCatalog.exercises)
        dao.insertExerciseEquipment(SeedCatalog.exerciseEquipment)
        dao.insertSecondaryMuscles(SeedCatalog.secondaryMuscles)
        dao.upsertCanonicalMetadata(
            SeedCatalog.exercises.map {
                CanonicalExerciseEntity(
                    exerciseId = it.id,
                    primaryMuscleGroupId = it.primaryMuscleGroupId,
                    movementFamilyId = SeedCatalog.familyIdsByExerciseId[it.id],
                    origin = "BUILT_IN",
                )
            },
        )
        lateinit var reviewRef: ExerciseImportReviewRepository
        canonical = CanonicalExerciseRepository(dao, Lazy { reviewRef })
        reviewRef = ExerciseImportReviewRepository(db, reviewDao, canonical)
    }

    @After fun tearDown() { db.close() }

    @Test fun batchImportCreatesReviewsCandidatesAndPreservesEverything() = runBlocking {
        val before = dao.exerciseCount()
        val seedSquat = dao.getExercise("squat")!!
        val seedRowNotesBefore = dao.getExercise("barbell_row")!!.notes

        val stats = canonical.importBatch(bundledFixture())

        assertEquals(before + NEW_RECORDS, dao.exerciseCount())
        assertEquals(NEW_RECORDS, stats.created)
        assertEquals(1, stats.reviewQueued)

        // Candidate (FEDB Barbell Curl vs seed barbell_curl) is queued, not merged
        val pending = reviewDao.observePending().first()
        assertEquals(listOf("free_exercise_db:Barbell_Curl"), pending.map { it.id })
        assertNull(dao.getExercise("fedb_Barbell_Curl"))
        val seedCurl = dao.getExercise("barbell_curl")!!
        assertEquals(seedCurl.name, "Barbell Curl")

        // Created records: source label, legacy mirror, provenance
        val squat = dao.getCanonicalExercise("fedb_Barbell_Squat")!!
        assertEquals("Barbell Squat", squat.exercise.name)
        assertEquals(ExerciseSource.FREE_EXERCISE_DB, squat.exercise.source)
        assertEquals(setOf("barbell"), squat.equipment.map { it.id }.toSet())
        assertEquals(setOf("calves", "glutes", "hamstrings", "back"), squat.secondaryTargets.map { it.id }.toSet())
        assertEquals(
            setOf("barbell"),
            dao.getExerciseEquipment().filter { it.exerciseId == "fedb_Barbell_Squat" }.map { it.equipmentId }.toSet(),
        )
        assertEquals(
            setOf("calves", "glutes", "hamstrings", "back"),
            dao.getSecondaryMuscles().filter { it.exerciseId == "fedb_Barbell_Squat" }.map { it.muscleGroupId }.toSet(),
        )
        assertTrue(squat.media.all { it.licenseName == "Unlicense" })
        assertTrue(squat.instructions.isNotEmpty())
        assertEquals("free_exercise_db", squat.sourceIdentities.single().sourceKey)
        assertEquals("Barbell_Squat", squat.sourceIdentities.single().externalExerciseId)

        // Seed rows untouched: IDs, notes, references
        assertEquals(seedSquat, dao.getExercise("squat"))
        assertEquals(seedRowNotesBefore, dao.getExercise("barbell_row")!!.notes)
        assertEquals("BUILT_IN", dao.getCanonicalExercise("squat")!!.metadata?.origin)
    }

    @Test fun reimportWithSameRevisionIsNoOp() = runBlocking {
        canonical.importBatch(bundledFixture())
        val before = dao.exerciseCount()
        val pendingBefore = reviewDao.observePending().first().size
        val mediaBefore = dao.getAllExerciseMedia().size
        val instructionsBefore = dao.getAllInstructions().size

        val stats = canonical.importBatch(bundledFixture())

        assertEquals(before, dao.exerciseCount())
        assertEquals(0, stats.created)
        assertEquals(0, stats.failed)
        assertEquals(pendingBefore, reviewDao.observePending().first().size)
        assertEquals(mediaBefore, dao.getAllExerciseMedia().size)
        assertEquals(instructionsBefore, dao.getAllInstructions().size)
        // But content refresh flows: updated bundle rewrite is allowed since
        // identity resolves exact and seed records aren't user-edited
        assertTrue(stats.updated >= 1)
    }

    @Test fun userNotesSurviveContentRefreshUpdate() = runBlocking {
        val first = canonical.importBatch(bundledFixture())
        assertTrue(first.failed == 0)

        dao.updateExerciseNotes("fedb_Barbell_Squat", "My personal squat cue")
        val refreshed = FreeExerciseDbImportAdapter.normalize(squatRecord(instructions = listOf("Changed step")))
        val stats = canonical.importBatch(listOf(refreshed!!))

        assertTrue(stats.updated == 1)
        assertEquals("My personal squat cue", dao.getExercise("fedb_Barbell_Squat")!!.notes)
    }

    @Test fun resolutionSuppressesRepeatPromptForSameIdentity() = runBlocking {
        canonical.importBatch(bundledFixture())
        val pendingId = reviewDao.observePending().first().single().id

        ExerciseImportReviewRepository(db, reviewDao, canonical).discardIncoming(pendingId)

        // Re-import after Discard: same identity must not prompt again
        val stats = canonical.importBatch(listOf(curlRecord().let { FreeExerciseDbImportAdapter.normalize(it)!! }))
        assertEquals(0, stats.reviewQueued)
        assertEquals(0, stats.created)
        assertTrue(reviewDao.observePending().first().isEmpty())
        assertNull(dao.getExercise("fedb_Barbell_Curl"))
    }

    private fun bundledFixture(): List<NormalizedExerciseImport> = listOfNotNull(
        squatRecord(), curlRecord(), skullcrusherRecord(),
    ).map { FreeExerciseDbImportAdapter.normalize(it)!! }

    private fun squatRecord(instructions: List<String> = listOf("Descend", "Rise")) = FreeExerciseDbRecord(
        id = "Barbell_Squat",
        name = "Barbell Squat",
        force = "push",
        level = "beginner",
        mechanic = "compound",
        equipment = "barbell",
        primaryMuscles = listOf("quadriceps"),
        secondaryMuscles = listOf("calves", "glutes", "hamstrings", "lower back"),
        instructions = instructions,
        category = "strength",
        images = listOf("Barbell_Squat/0.jpg"),
    )

    private fun curlRecord() = FreeExerciseDbRecord(
        id = "Barbell_Curl",
        name = "Barbell Curl",
        force = "pull",
        level = "beginner",
        mechanic = "isolation",
        equipment = "barbell",
        primaryMuscles = listOf("biceps"),
        instructions = listOf("Curl"),
        category = "strength",
        images = emptyList(),
    )

    private fun skullcrusherRecord() = FreeExerciseDbRecord(
        id = "Skullcrusher",
        name = "Skullcrusher",
        force = "push",
        level = "intermediate",
        mechanic = "isolation",
        equipment = "e-z curl bar",
        primaryMuscles = listOf("triceps"),
        instructions = listOf("Extend"),
        category = "strength",
        images = emptyList(),
    )

    private companion object {
        const val NEW_RECORDS = 2 // squat + skullcrusher (curl is queued for review)
    }
}
