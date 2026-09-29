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
import com.chy.muscletome.data.local.entity.ExerciseEquipmentLinkEntity
import com.chy.muscletome.data.local.entity.ExerciseInstructionEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.CanonicalExercise
import com.chy.muscletome.data.repository.CanonicalImportResult as RepositoryImportResult
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.model.ExerciseOrigin
import com.chy.muscletome.domain.model.ExerciseSourceIdentity
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalExercisePersistenceTest {
    private lateinit var database: MuscleTomeDatabase
    private lateinit var catalogDao: CatalogDao
    private lateinit var reviewRepository: ExerciseImportReviewRepository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MuscleTomeDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(*MuscleTomeMigrations.ALL)
            .build()
        catalogDao = database.catalogDao()
        database.userDao().upsert(UserEntity(id = "local-user", name = "You"))
        catalogDao.upsertMuscleGroups(
            listOf(
                MuscleGroupEntity("chest", "Chest"),
                MuscleGroupEntity("triceps", "Triceps"),
                MuscleGroupEntity("back", "Back"),
            ),
        )
        val canonicalRepository = CanonicalExerciseRepository(
            catalogDao,
            Lazy { reviewRepository },
        )
        reviewRepository = ExerciseImportReviewRepository(
            database,
            database.exerciseImportReviewDao(),
            canonicalRepository,
        )
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun createReadAndUpdateReplaceOnlyTargetExerciseRelations() = runBlocking {
        val repository = CanonicalExerciseRepository(catalogDao, Lazy { reviewRepository })
        val first = importRecord("canonical_press", "press-1", "Canonical Press")
        assertTrue(repository.import(first) is RepositoryImportResult.Created)

        val loaded = requireNotNull(repository.get("canonical_press"))
        assertEquals("Canonical Press", loaded.exercise.name)
        assertEquals(listOf("Brace", "Press"), loaded.instructions.sortedBy { it.sortOrder }.map { it.instruction })
        assertEquals(setOf("triceps", "back"), loaded.secondaryTargets.map { it.id }.toSet())
        assertEquals(setOf("barbell", "cable"), loaded.equipment.map { it.id }.toSet())
        assertEquals(
            listOf("canonical_press_media_one", "canonical_press_media_two"),
            loaded.media.sortedBy { it.sortOrder }.map { it.id },
        )
        assertEquals("catalog-source", loaded.sourceIdentities.single().sourceKey)
        assertEquals("press-1", loaded.sourceIdentities.single().externalExerciseId)
        assertEquals("https://source.example/press-1", loaded.sourceIdentities.single().sourceUrl)
        assertEquals(100L, loaded.sourceIdentities.single().importedAtEpochMs)
        assertEquals(200L, loaded.sourceIdentities.single().updatedAtEpochMs)
        assertEquals("credit", loaded.media.first().attribution)
        assertEquals("creator", loaded.media.first().creator)
        assertEquals("CC-BY", loaded.media.first().licenseName)
        assertEquals("https://license.example", loaded.media.first().licenseUrl)

        assertTrue(repository.import(importRecord("other_exercise", "other-1", "Other Exercise"))
            is RepositoryImportResult.Created)
        val changed = first.copy(
            exercise = first.exercise.copy(
                secondaryMuscleGroupIds = setOf("back"),
                equipmentIds = setOf("barbell"),
                instructions = listOf("Updated instruction"),
            ),
            media = listOf(media("replacement")),
            sourceIdentity = first.sourceIdentity?.copy(updatedAtEpochMs = 300L),
        )
        assertTrue(repository.import(changed) is RepositoryImportResult.Updated)
        val after = requireNotNull(repository.get("canonical_press"))
        assertEquals(listOf("Updated instruction"), after.instructions.map { it.instruction })
        assertEquals(setOf("back"), after.secondaryTargets.map { it.id }.toSet())
        assertEquals(setOf("barbell"), after.equipment.map { it.id }.toSet())
        assertEquals(listOf("canonical_press_media_replacement"), after.media.map { it.id })
        assertEquals(300L, after.sourceIdentities.single().updatedAtEpochMs)
        val untouched = requireNotNull(repository.get("other_exercise"))
        assertEquals(setOf("triceps", "back"), untouched.secondaryTargets.map { it.id }.toSet())
        assertEquals(setOf("barbell", "cable"), untouched.equipment.map { it.id }.toSet())
        assertEquals(
            listOf("other_exercise_media_one", "other_exercise_media_two"),
            untouched.media.sortedBy { it.sortOrder }.map { it.id },
        )
    }

    @Test
    fun canonicalWriteReloadsMovementFamily() = runBlocking {
        val repository = CanonicalExerciseRepository(catalogDao, Lazy { reviewRepository })
        catalogDao.insertExercises(listOf(exerciseRow("squat")))
        val item = importRecord("goblet_squat", "goblet-1", "Goblet Squat").let { record ->
            record.copy(exercise = record.exercise.copy(movementFamilyId = "squat"))
        }

        assertTrue(repository.import(item) is RepositoryImportResult.Created)
        assertEquals("squat", requireNotNull(repository.get("goblet_squat")).metadata?.movementFamilyId)
    }

    @Test
    fun canonicalFamilyIdsPersistWithoutMatchingExerciseRows() = runBlocking {
        val repository = CanonicalExerciseRepository(catalogDao, Lazy { reviewRepository })
        val cases = listOf(
            "bench_press" to "Dumbbell Bench Press",
            "row" to "Chest-Supported Row",
            "curl" to "Cable Curl",
            "lunge" to "Reverse Lunge",
        )

        cases.forEachIndexed { index, (familyId, name) ->
            val id = "family_case_$index"
            val item = importRecord(id, "family-external-$index", name).let { record ->
                record.copy(exercise = record.exercise.copy(movementFamilyId = familyId))
            }
            assertTrue(repository.import(item) is RepositoryImportResult.Created)
            assertEquals(familyId, requireNotNull(repository.get(id)).metadata?.movementFamilyId)
        }
    }

    @Test
    fun invalidChildWriteRollsBackEntireBundle() = runBlocking {
        val id = "rollback_exercise"
        val exercise = exerciseRow(id)
        val equipment = EquipmentEntity("rollback-barbell", "Rollback barbell")
        val metadata = CanonicalExerciseEntity(id, "chest", "cue", null, "IMPORTED", false)
        catalogDao.insertMuscleGroups(listOf(MuscleGroupEntity("core", "Core")))
        catalogDao.insertMuscleGroups(listOf(MuscleGroupEntity("core", "Core")))
        val failure = runCatching {
            catalogDao.saveCanonicalExerciseBundle(
                exercise = exercise,
                metadata = metadata,
                instructions = listOf(ExerciseInstructionEntity(id, 0, "Partial instruction")),
                muscleGroups = emptyList(),
                equipment = listOf(equipment),
                equipmentLinks = listOf(ExerciseEquipmentLinkEntity(id, equipment.id)),
                secondaryTargets = listOf(ExerciseSecondaryTargetEntity(id, "missing-muscle")),
                media = listOf(mediaEntity(id, "rollback-media", 0)),
                sourceIdentities = listOf(ExerciseSourceIdentityEntity("rollback", "id", id)),
                update = false,
            )
        }.exceptionOrNull()

        assertNotNull("Invalid target FK should fail", failure)
        assertEquals(null, catalogDao.getExercise(id))
        assertFalse(catalogDao.getAllCanonicalMetadata().any { it.exerciseId == id })
        assertFalse(catalogDao.getAllInstructions().any { it.exerciseId == id })
        assertFalse(catalogDao.getAllCanonicalEquipment().any { it.exerciseId == id })
        assertFalse(catalogDao.getAllSecondaryTargets().any { it.exerciseId == id })
        assertFalse(catalogDao.getAllExerciseMedia().any { it.exerciseId == id })
        assertFalse(catalogDao.getAllSourceIdentities().any { it.exerciseId == id })
        assertFalse(catalogDao.getEquipment().any { it.id == equipment.id })
    }

    @Test
    fun importIsIdempotentProtectsEditedRecordsAndReturnsReviewWithoutWriting() = runBlocking {
        val repository = CanonicalExerciseRepository(catalogDao, Lazy { reviewRepository })
        catalogDao.insertExercises(listOf(exerciseRow("stable_press")))
        val incoming = importRecord("stable_press", "stable-1", "Stable Press")
        assertTrue(repository.import(incoming) is RepositoryImportResult.Created)
        val count = catalogDao.exerciseCount()
        assertTrue(repository.import(incoming) is RepositoryImportResult.Updated)
        assertEquals(count, catalogDao.exerciseCount())

        catalogDao.insertExercises(listOf(exerciseRow("local_custom")))
        val local = importRecord("local_custom", "custom-1", "Custom Local Press", ExerciseOrigin.USER_CREATED)
            .copy(sourceIdentity = ExerciseSourceIdentity("custom-source", "custom-1"))
        assertTrue(repository.import(local) is RepositoryImportResult.Created)
        val before = requireNotNull(repository.get("local_custom"))
        val refresh = local.copy(exercise = local.exercise.copy(displayName = "Overwritten", instructions = listOf("Remote")))
        assertTrue(repository.import(refresh) is RepositoryImportResult.Protected)
        val after = requireNotNull(repository.get("local_custom"))
        assertEquals(before.exercise.name, after.exercise.name)
        assertEquals(before.instructions, after.instructions)

        val candidate = importRecord("candidate_press", "candidate-1", "Stable Press")
            .copy(sourceIdentity = ExerciseSourceIdentity("other-source", "candidate-1"))
        val beforeReview = catalogDao.exerciseCount()
        assertTrue(repository.import(candidate) is RepositoryImportResult.ReviewRequired)
        assertEquals(beforeReview, catalogDao.exerciseCount())
        assertEquals(null, catalogDao.getExercise("candidate_press"))
    }

    private fun importRecord(
        id: String,
        externalId: String,
        name: String,
        origin: ExerciseOrigin = ExerciseOrigin.IMPORTED,
    ) = NormalizedExerciseImport(
        exercise = CanonicalExercise(
            id = id,
            displayName = name,
            instructions = listOf("Brace", "Press"),
            movementPattern = MovementPattern.PUSH,
            movementType = MovementType.COMPOUND,
            difficulty = Difficulty.INTERMEDIATE,
            primaryMuscleGroupId = "chest",
            secondaryMuscleGroupIds = setOf("triceps", "back"),
            equipmentIds = setOf("barbell", "cable"),
            origin = origin,
        ),
        sourceIdentity = ExerciseSourceIdentity(
            "catalog-source", externalId, "https://source.example/$externalId", 100L, 200L,
        ),
        media = listOf(media("one"), media("two")),
    )

    private fun media(name: String) = ExerciseMedia(
        ExerciseMediaType.IMAGE,
        uri = "content://$name",
        sourceKey = "media-source",
        attribution = "credit",
        creator = "creator",
        licenseName = "CC-BY",
        licenseUrl = "https://license.example",
    )

    private fun mediaEntity(exerciseId: String, id: String, order: Int) = ExerciseMediaEntity(
        id, exerciseId, "IMAGE", "content://$id", sortOrder = order,
    )

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = "Rollback exercise",
        movementType = MovementType.COMPOUND,
        primaryMuscleGroupId = "chest",
    )
}
