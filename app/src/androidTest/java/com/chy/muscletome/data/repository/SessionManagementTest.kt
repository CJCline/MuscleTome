package com.chy.muscletome.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.MuscleTomeMigrations
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.SelectionHistoryDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.selection.SessionResolver
import com.chy.muscletome.domain.selection.VarietyEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionManagementTest {
    private lateinit var db: MuscleTomeDatabase
    private lateinit var userDao: UserDao
    private lateinit var catalogDao: CatalogDao
    private lateinit var routineDao: RoutineDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var selectionHistoryDao: SelectionHistoryDao
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var routineRepository: RoutineRepository

    private lateinit var dayId: String

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MuscleTomeDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(*MuscleTomeMigrations.ALL)
            .build()

        userDao = db.userDao()
        catalogDao = db.catalogDao()
        routineDao = db.routineDao()
        workoutDao = db.workoutDao()
        selectionHistoryDao = db.selectionHistoryDao()

        userDao.upsert(UserEntity(SeedCatalog.LOCAL_USER_ID, "You"))
        catalogDao.upsertMuscleGroups(
            listOf(
                MuscleGroupEntity("chest", "Chest"),
                MuscleGroupEntity("shoulders", "Shoulders"),
            ),
        )
        catalogDao.upsertEquipment(listOf(EquipmentEntity("barbell", "Barbell")))

        catalogDao.insertExercises(
            listOf(
                exercise("ex1", "Bench Press", "chest"),
                exercise("ex2", "Overhead Press", "shoulders"),
                exercise("ex3", "Cable Flyes", "chest"),
            ),
        )

        val varietyEngine = VarietyEngine()
        val sessionResolver = SessionResolver(varietyEngine)

        workoutRepository = WorkoutRepository(
            workoutDao = workoutDao,
            routineDao = routineDao,
            userDao = userDao,
            catalogDao = catalogDao,
            selectionHistoryDao = selectionHistoryDao,
            varietyEngine = varietyEngine,
            database = db,
            sessionResolver = sessionResolver,
        )

        routineRepository = RoutineRepository(
            routineDao = routineDao,
            catalogDao = catalogDao,
            database = db,
        )

        val routineId = routineRepository.createRoutine("Push Routine")
        dayId = routineRepository.addDay(routineId, "Push Day")
        routineRepository.addFixedSlot(dayId, "ex1", sets = 3)
        routineRepository.addFixedSlot(dayId, "ex2", sets = 3)
        routineRepository.addFixedSlot(dayId, "ex3", sets = 3)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun resumeAfterProcessDeath_returnsToCorrectExercise() = runBlocking {
        // 1. Start workout session
        val startResult = workoutRepository.startSession(dayId)
        assertTrue(startResult is StartResult.Success)
        val sessionId = (startResult as StartResult.Success).sessionId

        val slotResults = workoutDao.getSlotResults(sessionId).sortedBy { it.sortOrder }
        assertEquals(3, slotResults.size)

        // 2. Log set for Exercise 1
        workoutRepository.logSet(slotResults[0].id, 1, 100.0, 8, null, null)

        // 3. Move active position pointer to Exercise 2
        workoutRepository.updateLastActiveResultId(sessionId, slotResults[1].id)

        // 4. Simulate Process Death / Relaunch by fetching open session from DB
        val openSession = workoutRepository.getOpenSession()
        assertNotNull(openSession)
        assertEquals(sessionId, openSession?.id)

        // 5. Verify resume point points to Exercise 2
        assertEquals(slotResults[1].id, openSession?.lastActiveResultId)

        // 6. Verify set log for Exercise 1 remains intact
        val setsForEx1 = workoutDao.getSetsForSlotResult(slotResults[0].id)
        assertEquals(1, setsForEx1.size)
        assertEquals(100.0, setsForEx1[0].weight, 0.01)
    }

    @Test
    fun midSessionReorder_preservesLoggedSetsAndUpdatesPointer() = runBlocking {
        val startResult = workoutRepository.startSession(dayId)
        val sessionId = (startResult as StartResult.Success).sessionId

        val initialResults = workoutDao.getSlotResults(sessionId).sortedBy { it.sortOrder }
        val res0 = initialResults[0] // ex1
        val res1 = initialResults[1] // ex2
        val res2 = initialResults[2] // ex3

        // Log 2 sets for Exercise 1 (res0)
        workoutRepository.logSet(res0.id, 1, 100.0, 8, null, null)
        workoutRepository.logSet(res0.id, 2, 105.0, 6, null, null)

        // Mid-session reorder: Ex 3 (res2) -> pos 0, Ex 1 (res0) -> pos 1, Ex 2 (res1) -> pos 2
        workoutRepository.reorderSlotResults(sessionId, listOf(res2.id, res0.id, res1.id))
        workoutRepository.updateLastActiveResultId(sessionId, res0.id)

        // Verify updated order in database
        val updatedResults = workoutDao.getSlotResults(sessionId).sortedBy { it.sortOrder }
        assertEquals(res2.id, updatedResults[0].id)
        assertEquals(res0.id, updatedResults[1].id)
        assertEquals(res1.id, updatedResults[2].id)

        // Verify logged sets for Ex 1 (res0) are 100% preserved
        val setsEx1 = workoutDao.getSetsForSlotResult(res0.id)
        assertEquals(2, setsEx1.size)
        assertEquals(100.0, setsEx1[0].weight, 0.01)
        assertEquals(105.0, setsEx1[1].weight, 0.01)

        // Verify resume pointer remains attached to res0
        val openSession = workoutRepository.getOpenSession()
        assertEquals(res0.id, openSession?.lastActiveResultId)
    }

    @Test
    fun templateSave_doesNotLeakIntoActiveSessionWithoutLiveEdits() = runBlocking {
        // Start active session from template
        val startResult = workoutRepository.startSession(dayId)
        val sessionId = (startResult as StartResult.Success).sessionId

        val activeBefore = workoutDao.getSlotResults(sessionId)
        assertEquals(3, activeBefore.size)

        // User modifies template for Day 1 (adds 4th slot and changes metric on 1st slot)
        routineRepository.addFixedSlot(dayId, "ex1", sets = 5)
        val templateSlots = routineDao.getSlots(dayId)
        assertEquals(4, templateSlots.size)

        // Active session copy remains untouched by template edit
        val activeAfterTemplateEdit = workoutDao.getSlotResults(sessionId)
        assertEquals(3, activeAfterTemplateEdit.size)
        assertEquals(3, activeAfterTemplateEdit[0].plannedSets)

        // User performs live edit on active session (removes slot 3)
        val res2 = activeAfterTemplateEdit.find { it.resolvedExerciseId == "ex3" }!!
        workoutRepository.removeSlotFromActiveSession(sessionId, res2.id)

        // Active session now has 2 non-removed slots
        val liveActiveSlots = workoutDao.getSlotResults(sessionId).filter { !it.isRemovedFromSession }
        assertEquals(2, liveActiveSlots.size)

        // Saved template retains its 4 slots for future sessions
        val templateSlotsFinal = routineDao.getSlots(dayId)
        assertEquals(4, templateSlotsFinal.size)
    }

    private fun exercise(id: String, name: String, muscle: String) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscleGroupId = muscle,
        movementType = MovementType.COMPOUND,
        movementPattern = MovementPattern.PUSH,
        difficulty = Difficulty.INTERMEDIATE,
    )
}
