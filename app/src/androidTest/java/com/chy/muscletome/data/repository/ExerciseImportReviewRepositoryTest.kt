package com.chy.muscletome.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.MuscleTomeMigrations
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseImportReviewDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.NormalizedExerciseImport
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
class ExerciseImportReviewRepositoryTest {
    private lateinit var db: MuscleTomeDatabase
    private lateinit var catalog: CatalogDao
    private lateinit var reviewDao: ExerciseImportReviewDao
    private lateinit var canonical: CanonicalExerciseRepository
    private lateinit var review: ExerciseImportReviewRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MuscleTomeDatabase::class.java,
        ).allowMainThreadQueries().addMigrations(*MuscleTomeMigrations.ALL).build()
        catalog = db.catalogDao()
        reviewDao = db.exerciseImportReviewDao()
        db.userDao().upsert(UserEntity("local-user", "You"))
        catalog.upsertMuscleGroups(listOf(MuscleGroupEntity("chest", "Chest"), MuscleGroupEntity("triceps", "Triceps")))
        catalog.upsertEquipment(listOf(EquipmentEntity("barbell", "Barbell")))
        lateinit var reviewRef: ExerciseImportReviewRepository
        canonical = CanonicalExerciseRepository(catalog) { reviewRef }
        review = ExerciseImportReviewRepository(db, reviewDao, canonical)
        reviewRef = review
    }

    @After fun tearDown() { db.close() }

    @Test fun reviewPendingDoesNotWriteAndEachResolutionPersistsExpectedOutcome() = runBlocking {
        val original = normalized("original", "source-a", "1", "Same Name")
        assertTrue(canonical.import(original) is CanonicalImportResult.Created)
        canonical.import(
            original.copy(
                exercise = original.exercise.copy(displayName = "Third-source identity"),
                sourceIdentity = ExerciseSourceIdentity("source-z", "99"),
            ),
        )
        val before = catalog.exerciseCount()

        val incoming = normalized("incoming", "source-b", "2", "Same Name")
        assertTrue(canonical.import(incoming) is CanonicalImportResult.ReviewRequired)
        assertTrue(canonical.import(incoming) is CanonicalImportResult.ReviewRequired)
        assertEquals(before, catalog.exerciseCount())
        val pendingId = review.observePending().first().single().id
        assertEquals("source-b:2", pendingId)
        assertEquals(1, review.observePending().first().size)

        review.keepBoth(pendingId)
        assertEquals(before + 1, catalog.exerciseCount())
        assertEquals("KEEP_BOTH", review.resolutions().single().action)
        assertTrue(review.observePending().first().isEmpty())

        val mergeIncoming = normalized("incoming-merge", "source-c", "3", "Same Name")
        canonical.import(mergeIncoming)
        val mergePending = review.observePending().first().single().id
        val originalBeforeMerge = requireNotNull(canonical.get("original"))
        review.mergeIntoExisting(mergePending, "original")
        assertEquals(before + 1, catalog.exerciseCount())
        assertEquals("MERGE", review.resolutions().last().action)
        assertEquals("original", review.resolutions().last().resolvedExerciseId)
        val originalAfterMerge = requireNotNull(canonical.get("original"))
        assertEquals(originalBeforeMerge.exercise.name, originalAfterMerge.exercise.name)
        assertEquals(originalBeforeMerge.instructions, originalAfterMerge.instructions)
        assertEquals(
            setOf("source-a", "source-z", "source-c"),
            originalAfterMerge.sourceIdentities.asSequence().map { it.sourceKey }.toSet(),
        )
        assertTrue(canonical.import(mergeIncoming) is CanonicalImportResult.Updated)
        assertEquals(before + 1, catalog.exerciseCount())

        val discardIncoming = normalized("incoming-discard", "source-d", "4", "Same Name")
        canonical.import(discardIncoming)
        val discardPending = review.observePending().first().single().id
        review.discardIncoming(discardPending)
        assertTrue(canonical.import(discardIncoming) is CanonicalImportResult.Protected)
        assertTrue(review.observePending().first().isEmpty())
        assertEquals(before + 1, catalog.exerciseCount())
        assertEquals("DISCARD", review.resolutions().last().action)
        assertNull(catalog.getExercise("incoming-discard"))
    }

    private fun normalized(id: String, source: String, external: String, name: String) = NormalizedExerciseImport(
        exercise = CanonicalExercise(
            id = id,
            displayName = name,
            instructions = listOf("Step one"),
            movementPattern = MovementPattern.PUSH,
            movementType = MovementType.COMPOUND,
            difficulty = Difficulty.INTERMEDIATE,
            primaryMuscleGroupId = "chest",
            secondaryMuscleGroupIds = setOf("triceps"),
            equipmentIds = setOf("barbell"),
            origin = ExerciseOrigin.IMPORTED,
        ),
        sourceIdentity = ExerciseSourceIdentity(source, external),
        media = listOf(ExerciseMedia(ExerciseMediaType.IMAGE, uri = "content://$id")),
    )
}
