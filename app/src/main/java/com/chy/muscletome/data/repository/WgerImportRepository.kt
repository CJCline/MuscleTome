package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEquipmentCrossRef
import com.chy.muscletome.data.local.entity.ExerciseSecondaryMuscleCrossRef
import com.chy.muscletome.data.remote.WgerApiClient
import com.chy.muscletome.data.remote.WgerMapper
import com.chy.muscletome.domain.model.ExerciseSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WgerImportProgress(
    val running: Boolean = false,
    val fetched: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val message: String = "",
    val error: String? = null,
)

@Singleton
class WgerImportRepository @Inject constructor(
    private val api: WgerApiClient,
    private val catalogDao: CatalogDao,
) {
    private val _progress = MutableStateFlow(WgerImportProgress())
    val progress: StateFlow<WgerImportProgress> = _progress.asStateFlow()

    suspend fun importAll(maxPages: Int = 40) {
        if (_progress.value.running) return
        _progress.value = WgerImportProgress(running = true, message = "Starting wger import…")
        var offset = 0
        var imported = 0
        var skipped = 0
        var fetched = 0
        try {
            repeat(maxPages) { page ->
                val pageData = api.fetchPage(offset = offset, limit = 50)
                if (pageData.results.isEmpty()) return@repeat
                fetched += pageData.results.size
                for (item in pageData.results) {
                    val name = item.englishName?.trim().orEmpty()
                    if (name.isBlank()) {
                        skipped++
                        continue
                    }
                    val primary = item.muscles.firstNotNullOfOrNull { WgerMapper.muscleId(it.nameEn) }
                        ?: item.musclesSecondary.firstNotNullOfOrNull { WgerMapper.muscleId(it.nameEn) }
                    if (primary == null) {
                        skipped++
                        continue
                    }
                    val id = WgerMapper.exerciseId(item.id)
                    val equipmentIds = item.equipment.map { WgerMapper.equipmentId(it.name) }.distinct()
                    if (equipmentIds.isNotEmpty()) {
                        catalogDao.insertEquipment(
                            equipmentIds.map { eqId ->
                                val original = item.equipment.first {
                                    WgerMapper.equipmentId(it.name) == eqId
                                }.name
                                EquipmentEntity(eqId, WgerMapper.equipmentDisplayName(original))
                            },
                        )
                    }
                    val attribution = buildString {
                        append("Source: wger.de")
                        item.licenseName?.let { append(" · $it") }
                        item.licenseAuthor?.let { append(" · $it") }
                    }
                    catalogDao.insertExercises(
                        listOf(
                            ExerciseEntity(
                                id = id,
                                name = name,
                                description = listOfNotNull(item.englishDescription, attribution)
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n\n"),
                                movementPattern = WgerMapper.movementPattern(name, item.categoryName),
                                movementType = WgerMapper.movementType(name, item.categoryName),
                                primaryMuscleGroupId = primary,
                                difficulty = WgerMapper.difficulty(name),
                                isCustom = false,
                                createdByUserId = null,
                                source = ExerciseSource.SEED,
                                notes = attribution,
                            ),
                        ),
                    )
                    catalogDao.insertExerciseEquipment(
                        equipmentIds.map { ExerciseEquipmentCrossRef(id, it) },
                    )
                    val secondary = item.musclesSecondary
                        .mapNotNull { WgerMapper.muscleId(it.nameEn) }
                        .filter { it != primary }
                        .distinct()
                    if (secondary.isNotEmpty()) {
                        catalogDao.insertSecondaryMuscles(
                            secondary.map { ExerciseSecondaryMuscleCrossRef(id, it) },
                        )
                    }
                    imported++
                }
                _progress.value = WgerImportProgress(
                    running = true,
                    fetched = fetched,
                    imported = imported,
                    skipped = skipped,
                    message = "Imported $imported (page ${page + 1})",
                )
                if (pageData.next.isNullOrBlank()) return@repeat
                offset += 50
            }
            _progress.value = WgerImportProgress(
                running = false,
                fetched = fetched,
                imported = imported,
                skipped = skipped,
                message = "Done. Imported $imported, skipped $skipped.",
            )
        } catch (t: Throwable) {
            _progress.value = WgerImportProgress(
                running = false,
                fetched = fetched,
                imported = imported,
                skipped = skipped,
                message = "Stopped after $imported imports.",
                error = t.message ?: "Import failed",
            )
        }
    }
}