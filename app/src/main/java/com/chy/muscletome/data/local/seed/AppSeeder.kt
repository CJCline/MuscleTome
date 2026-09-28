package com.chy.muscletome.data.local.seed

import android.util.Log
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSeeder @Inject constructor(
    private val userDao: UserDao,
    private val catalogDao: CatalogDao,
) {
    suspend fun seedIfEmpty() {
        if (catalogDao.exerciseCount() > 0 && userDao.count() > 0) {
            Log.d(TAG, "Seed already present")
            return
        }

        catalogDao.insertMuscleGroups(SeedCatalog.muscleGroups)
        catalogDao.insertEquipment(SeedCatalog.equipment)
        catalogDao.insertExercises(SeedCatalog.exercises)
        catalogDao.insertExerciseEquipment(SeedCatalog.exerciseEquipment)
        catalogDao.insertSecondaryMuscles(SeedCatalog.secondaryMuscles)
        catalogDao.upsertCanonicalMetadata(
            SeedCatalog.exercises.map { exercise ->
                CanonicalExerciseEntity(
                    exerciseId = exercise.id,
                    primaryMuscleGroupId = exercise.primaryMuscleGroupId,
                    movementFamilyId = SeedCatalog.familyIdsByExerciseId[exercise.id],
                    origin = "BUILT_IN",
                )
            },
        )

        userDao.insert(UserEntity(id = SeedCatalog.LOCAL_USER_ID, name = "You"))
        userDao.insertAvailableEquipment(
            SeedCatalog.equipment.map {
                UserAvailableEquipmentCrossRef(SeedCatalog.LOCAL_USER_ID, it.id)
            },
        )

        Log.d(TAG, "Seeded ${SeedCatalog.exercises.size} exercises")
    }

    private companion object {
        const val TAG = "AppSeeder"
    }
}