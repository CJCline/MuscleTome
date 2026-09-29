package com.chy.muscletome.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.ExerciseMediaCacheEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure license gate for the media cache (Phase 5C): only licenses that
 * permit redistribution/caching may be fetched. Kept object-level so the rule
 * is unit-testable without Android dependencies.
 */
object MediaLicenseGate {
    /** Licenses that permit caching/redistribution. Compared case-insensitively. */
    internal val cacheableLicenses = setOf(
        "unlicense", "cc0", "cc0 1.0", "public domain", "mit", "apache-2.0",
        "cc by", "cc by-sa", "cc by 4.0", "cc by-sa 4.0", "creative commons cc by",
    )

    fun isCacheable(licenseName: String?): Boolean {
        val key = licenseName?.trim()?.lowercase() ?: return false
        if (key.isEmpty()) return false
        // ND (no-derivatives) and NC (non-commercial) variants restrict reuse:
        // any CC license carrying them stays reference-only even though the
        // bare "cc by" prefix would otherwise match.
        if (key.startsWith("cc") && ("-nc" in key || "-nd" in key)) return false
        return cacheableLicenses.any { key == it || key.startsWith(it) }
    }
}

/**
 * On-demand offline cache for imported exercise media (Phase 5C).
 *
 * Rules (docs/canonical-exercise-import.md):
 * - Only licenses that permit redistribution/caching are fetched; anything
 *   unclear is skipped with a logged reason and stays reference-only.
 * - The original URI and provenance columns are never mutated; cached files
 *   live in app-internal storage keyed by media ID.
 * - The cache is bounded (default 256 MB) and evicted oldest-first.
 * - Any failure (offline, 404, corrupt bytes, vanished file) falls back to the
 *   reference URI; nothing here can fail backup or restore.
 */
@Singleton
class ExerciseMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
) {
    fun isCacheableLicense(licenseName: String?): Boolean =
        MediaLicenseGate.isCacheable(licenseName)

    private fun cacheDir(): File = File(context.filesDir, "media").apply { mkdirs() }

    /** Aggregated cache status for one exercise's media rows. */
    data class CacheStatus(
        val totalMedia: Int,
        val cached: Int,
        val skippedLicense: Int,
        val cacheable: Int,
    )

    suspend fun cacheStatus(exerciseId: String): CacheStatus = withContext(Dispatchers.IO) {
        val media = catalogDao.getCanonicalExercise(exerciseId)?.media
            ?.filter { it.uri.isNotBlank() }.orEmpty()
        val cachedIds = catalogDao.getMediaCacheForExercise(exerciseId).map { it.mediaId }.toSet()
        val cacheable = media.count { isCacheableLicense(it.licenseName) }
        CacheStatus(
            totalMedia = media.size,
            cached = media.count { it.id in cachedIds },
            skippedLicense = media.size - cacheable,
            cacheable = cacheable,
        )
    }

    suspend fun cachedUriFor(mediaId: String): String? = withContext(Dispatchers.IO) {
        val row = catalogDao.getMediaCache(mediaId) ?: return@withContext null
        val file = File(row.localPath)
        if (file.isFile) row.localPath else null
    }

    /**
     * Downloads all license-allowed images for one exercise. Returns the number
     * newly cached; failures count as skips, never throw.
     */
    suspend fun fetchForExercise(exerciseId: String): Int = withContext(Dispatchers.IO) {
        val media = catalogDao.getCanonicalExercise(exerciseId)?.media
            ?.filter { it.uri.isNotBlank() && isCacheableLicense(it.licenseName) }
            .orEmpty()
        var fetched = 0
        media.forEach { row ->
            if (fetchOne(row.id, row.uri)) fetched++
        }
        fetched
    }

    /** Downloads all license-allowed images for every exercise in a family. */
    suspend fun fetchForFamily(familyId: String): Int = withContext(Dispatchers.IO) {
        val exerciseIds = catalogDao.getCanonicalFamilyMetadata()
            .filter { it.movementFamilyId == familyId }
            .map { it.exerciseId }
        var fetched = 0
        exerciseIds.forEach { id ->
            runCatching { fetched += fetchForExercise(id) }
                .onFailure { Log.w(TAG, "Family fetch failed for $id", it) }
        }
        fetched
    }

    /** Downloads one media row. Returns false (never throws) on any failure. */
    private suspend fun fetchOne(mediaId: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Skip if a valid cached copy already exists.
            if (cachedUriFor(mediaId) != null) return@withContext false
            val url = URL(uri)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Skip $mediaId: HTTP ${connection.responseCode}")
                    return@withContext false
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty() || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
                    Log.w(TAG, "Skip $mediaId: not a decodable image")
                    return@withContext false
                }
                val file = File(cacheDir(), "$mediaId.jpg")
                file.writeBytes(bytes)
                catalogDao.upsertMediaCache(
                    ExerciseMediaCacheEntity(
                        mediaId = mediaId,
                        localPath = file.absolutePath,
                        fetchedAtEpochMs = System.currentTimeMillis(),
                        bytes = bytes.size.toLong(),
                        licenseCheckedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                enforceCacheCap()
                true
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Media fetch failed for $mediaId: ${e.message}")
            false
        }
    }

    /** Deletes cached files and rows; metadata rows on exercise_media survive. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val rows = catalogDao.getMediaCacheOldestFirst()
        rows.forEach { row -> File(row.localPath).delete() }
        catalogDao.clearMediaCache()
        Log.i(TAG, "Cleared ${rows.size} cached media files")
    }

    suspend fun cacheSizeBytes(): Long = catalogDao.mediaCacheBytes()

    /** Oldest-first eviction until the cache is back under [MAX_CACHE_BYTES]. */
    private suspend fun enforceCacheCap() {
        val rows = catalogDao.getMediaCacheOldestFirst()
        var total = catalogDao.mediaCacheBytes()
        for (row in rows) {
            if (total <= MAX_CACHE_BYTES) break
            File(row.localPath).delete()
            catalogDao.deleteMediaCache(row.mediaId)
            total -= row.bytes
        }
    }

    private companion object {
        const val TAG = "ExerciseMediaRepo"
        /** Bounded cache: 256 MB default (Phase 5 decision D3). */
        const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    }
}
