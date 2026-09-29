package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.MovementFamilyEntity
import com.chy.muscletome.domain.model.MovementFamilies
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Persisted, queryable movement families (Phase 5A). Extends the former
 * [MovementFamilyBackfill] behavior: the eight legacy IDs stay stable and are
 * seeded `BUILT_IN` idempotently; user-created families are deduped by
 * `normalizedKey` exactly the way imports normalize names.
 */
@Singleton
class FamilyRepository @Inject constructor(private val catalogDao: CatalogDao) {

    /** Result of a create attempt; on [Duplicate] the existing family is surfaced. */
    sealed interface CreateFamilyResult {
        data class Created(val family: MovementFamilyEntity) : CreateFamilyResult
        data class Duplicate(val existing: MovementFamilyEntity) : CreateFamilyResult
        data class Invalid(val reason: String) : CreateFamilyResult
    }

    fun observeFamilies(): Flow<List<MovementFamilyEntity>> = catalogDao.observeMovementFamilies()

    suspend fun listFamilies(): List<MovementFamilyEntity> = catalogDao.getMovementFamilies()

    suspend fun getFamily(id: String): MovementFamilyEntity? = catalogDao.getMovementFamily(id)

    suspend fun createFamily(displayName: String): CreateFamilyResult {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return CreateFamilyResult.Invalid("Family name is required")
        val key = MovementFamilies.normalizeKey(trimmed)
        if (key.isEmpty()) return CreateFamilyResult.Invalid("Family name must contain letters or digits")
        catalogDao.getMovementFamilyByNormalizedKey(key)?.let {
            return CreateFamilyResult.Duplicate(it)
        }
        // New IDs live in the family namespace; a collision with a legacy ID is
        // practically impossible once the key check above passed, but stay safe.
        var id = key
        var attempt = 1
        while (catalogDao.getMovementFamily(id) != null) {
            id = "${key}_${++attempt}"
        }
        val family = MovementFamilyEntity(
            id = id,
            displayName = trimmed,
            normalizedKey = key,
            createdAtEpochMs = System.currentTimeMillis(),
            origin = "USER_CREATED",
        )
        catalogDao.upsertMovementFamily(family)
        return CreateFamilyResult.Created(family)
    }

    /** Assign (or clear, with null) an exercise's family. No-ops if the family is unknown. */
    suspend fun assignFamily(exerciseId: String, familyId: String?) {
        if (familyId != null && catalogDao.getMovementFamily(familyId) == null) return
        catalogDao.setMovementFamily(exerciseId, familyId)
    }

    /**
     * Seed the eight legacy families idempotently. Called at app startup so both
     * fresh installs and migration-9→10 installs converge on the same rows.
     */
    suspend fun seedBuiltinFamilies() {
        MovementFamilies.all.forEach { family ->
            catalogDao.upsertMovementFamily(
                MovementFamilyEntity(
                    id = family.id,
                    displayName = family.label,
                    normalizedKey = MovementFamilies.normalizeKey(family.label),
                    createdAtEpochMs = 0L,
                    origin = "BUILT_IN",
                ),
            )
        }
    }
}
