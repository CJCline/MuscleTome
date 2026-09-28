package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.domain.model.MovementFamilies
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementFamilyBackfill @Inject constructor(private val catalogDao: CatalogDao) {
    suspend fun run() {
        catalogDao.getExercises().forEach { exercise ->
            val family = MovementFamilies.familyId(exercise.name, exercise.movementPattern)
            if (family != null) catalogDao.setMovementFamily(exercise.id, family)
        }
    }
}
