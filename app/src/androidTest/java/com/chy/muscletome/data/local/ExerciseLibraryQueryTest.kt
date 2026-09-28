package com.chy.muscletome.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryTargetEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseLibraryQueryTest {
    private lateinit var database: MuscleTomeDatabase

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MuscleTomeDatabase::class.java,
        ).allowMainThreadQueries().addMigrations(*MuscleTomeMigrations.ALL).build()
        val dao = database.catalogDao()
        dao.upsertMuscleGroups(listOf(MuscleGroupEntity("back", "Back"), MuscleGroupEntity("lats", "Lats"), MuscleGroupEntity("arms", "Arms")))
        dao.upsertEquipment(listOf(EquipmentEntity("barbell", "Barbell")))
        dao.insertExercises(listOf(
            ExerciseEntity("row", "Barbell Row", movementPattern = MovementPattern.PULL, movementType = MovementType.COMPOUND, primaryMuscleGroupId = "lats"),
            ExerciseEntity("curl", "Barbell Curl", movementPattern = MovementPattern.PULL, movementType = MovementType.ISOLATION, primaryMuscleGroupId = "arms"),
        ))
        dao.insertSecondaryMuscles(listOf(ExerciseSecondaryMuscleCrossRef("row", "back")))
        dao.upsertSecondaryTargets(listOf(ExerciseSecondaryTargetEntity("row", "back")))
    }

    @After fun tearDown() { database.close() }

    @Test fun selectsSecondaryTargetOnceAndKeepsCompoundBeforeIsolation() = runBlocking {
        val rows = database.catalogDao().searchLibraryRows("", "back").first()
        assertEquals(listOf("row"), rows.map { it.exercise.id })
        val queryRows = database.catalogDao().searchLibraryRows("", null).first()
        assertEquals(listOf("row", "curl"), queryRows.map { it.exercise.id })
        assertEquals(MovementType.COMPOUND, queryRows.first().exercise.movementType)
    }
}
