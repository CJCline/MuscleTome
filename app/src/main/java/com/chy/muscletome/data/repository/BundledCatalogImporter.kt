package com.chy.muscletome.data.repository

import android.content.Context
import android.util.Log
import com.chy.muscletome.data.remote.FreeExerciseDbAsset
import com.chy.muscletome.data.remote.FreeExerciseDbImportAdapter
import com.chy.muscletome.domain.model.NormalizedExerciseImport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Result of one [BundledCatalogImporter.runIfNeeded] pass. */
data class BundledImportOutcome(
    val ran: Boolean,
    val stats: CanonicalBatchStats? = null,
    val skippedUnmappedMuscle: Int = 0,
)

/**
 * Imports the bundled free-exercise-db catalog asset through the canonical
 * import pipeline. Offline, deterministic, idempotent: the applied asset
 * revision is persisted in `catalog_import` prefs, and re-imports only run
 * when the bundled revision changes. Records whose primary muscle cannot be
 * mapped are skipped with diagnostics (approved batch gate); the revision is
 * only marked applied when no record failed, so failed records retry on the
 * next launch — all writes are identity-idempotent.
 */
@Singleton
class BundledCatalogImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val canonicalRepository: CanonicalExerciseRepository,
) {
    private val prefs = context.getSharedPreferences("catalog_import", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Fast no-op when the bundled revision was already applied. */
    suspend fun runIfNeeded(): BundledImportOutcome = withContext(Dispatchers.IO) {
        val asset = readAsset() ?: return@withContext BundledImportOutcome(ran = false)
        if (prefs.getString(KEY_REVISION, null) == asset.revision) {
            return@withContext BundledImportOutcome(ran = false)
        }

        val importedAt = System.currentTimeMillis()
        var skippedUnmappedMuscle = 0
        val imports = mutableListOf<NormalizedExerciseImport>()
        asset.records.forEach { record ->
            val normalized = FreeExerciseDbImportAdapter.normalize(record, importedAt)
            if (normalized == null) {
                skippedUnmappedMuscle++
                Log.w(TAG, "Unusable record skipped (blank name): ${record.id}")
            } else if (normalized.exercise.primaryMuscleGroupId == null) {
                // Approved gate: primary target must be mappable for bundled
                // content (diagnostics keep the native value as provenance).
                skippedUnmappedMuscle++
                Log.d(TAG, "Record skipped (unmapped primary muscle): ${record.id}")
            } else {
                imports += normalized
            }
        }

        val stats = canonicalRepository.importBatch(imports)
        if (stats.failed == 0) {
            prefs.edit().putString(KEY_REVISION, asset.revision).apply()
        } else {
            Log.w(TAG, "Batch had ${stats.failed} failures; revision not marked applied")
        }
        Log.i(
            TAG,
            "Bundled catalog import: created=${stats.created} updated=${stats.updated} " +
                "protected=${stats.protected} reviewQueued=${stats.reviewQueued} " +
                "skippedUnmappedMuscle=$skippedUnmappedMuscle failed=${stats.failed}",
        )
        BundledImportOutcome(
            ran = true,
            stats = stats,
            skippedUnmappedMuscle = skippedUnmappedMuscle,
        )
    }

    private fun readAsset(): FreeExerciseDbAsset? = try {
        context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
            json.decodeFromString<FreeExerciseDbAsset>(reader.readText())
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Bundled catalog asset missing or unreadable: ${t.message}")
        null
    }

    private companion object {
        const val TAG = "BundledCatalogImporter"
        const val KEY_REVISION = "revision"
        const val ASSET_PATH = "catalog/free_exercise_db.json"
    }
}
