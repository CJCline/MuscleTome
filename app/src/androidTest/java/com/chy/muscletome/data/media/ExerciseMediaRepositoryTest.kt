package com.chy.muscletome.data.media

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chy.muscletome.data.backup.BackupDocument
import com.chy.muscletome.data.backup.BackupRepository
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.entity.CanonicalExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaCacheEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.ExerciseSourceIdentityEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.domain.model.MovementType
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 5C regression tests: real Room and private files, no network or new dependencies.
 * Cache-cap sizes are SYNTHETIC database accounting values backed by tiny PNGs,
 * not a 256 MiB transfer. Reflection exercises the production eviction routine
 * without adding a production test hook; it does not test its download call site.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseMediaRepositoryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var db: MuscleTomeDatabase
    private lateinit var dao: CatalogDao
    private lateinit var repository: ExerciseMediaRepository

    @Before
    fun setUp() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "media-regression-${UUID.randomUUID()}")
        val files = File(root, "files").apply { check(mkdirs()) }
        // Never read, clear, or overwrite the running app's actual media directory.
        context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = files
        }
        db = newDatabase()
        dao = db.catalogDao()
        repository = ExerciseMediaRepository(context, dao)
        db.userDao().upsert(UserEntity("local-user", "You"))
        dao.upsertMuscleGroups(listOf(MuscleGroupEntity("chest", "Chest")))
        dao.upsertExercises(listOf(ExerciseEntity(
            id = EXERCISE_ID, name = "Media regression press",
            movementType = MovementType.COMPOUND, primaryMuscleGroupId = "chest",
        )))
        dao.upsertCanonicalMetadata(listOf(CanonicalExerciseEntity(
            EXERCISE_ID, "chest", "Brace and press", FAMILY_ID, "IMPORTED", false,
        )))
        dao.upsertSourceIdentities(listOf(ExerciseSourceIdentityEntity(
            "test-source", "external-press", EXERCISE_ID,
            "https://example.invalid/source", 10L, 20L,
        )))
    }

    @After
    fun tearDown() {
        try {
            if (::db.isInitialized) db.close()
        } finally {
            if (::root.isInitialized) root.deleteRecursively()
        }
    }

    @Test
    fun disallowedLicensesSkipFetchPathForExerciseAndFamily() = runBlocking {
        val licenses = listOf(null, "", "   ", "unknown", "All rights reserved",
            "CC BY-NC 4.0", "CC BY-ND 4.0", "CC BY-NC-SA 4.0")
        val blocked = licenses.mapIndexed { index, license -> media("blocked-$index", license) }
        val allowed = media("allowed", "Unlicense")
        dao.upsertExerciseMedia(blocked + allowed)
        val cached = seedCache(allowed.id, 10L, 100L)
        val metadataBefore = dao.getAllExerciseMedia()
        val fetchPathIds = mutableListOf<String>()
        val guardedDao = object : CatalogDao by dao {
            override suspend fun getMediaCache(mediaId: String): ExerciseMediaCacheEntity? {
                fetchPathIds += mediaId
                // fetchOne starts here, before opening a URL. Fail before any network
                // if a blocked row gets through the gate. AssertionError is deliberately
                // not swallowed by fetchOne's catch(Exception).
                if (mediaId != allowed.id) throw AssertionError("Disallowed media entered fetch: $mediaId")
                return dao.getMediaCache(mediaId)
            }
        }
        val guardedRepository = ExerciseMediaRepository(context, guardedDao)
        assertEquals(ExerciseMediaRepository.CacheStatus(9, 1, 8, 1),
            guardedRepository.cacheStatus(EXERCISE_ID))
        assertEquals(0, guardedRepository.fetchForExercise(EXERCISE_ID))
        assertEquals(listOf(allowed.id), fetchPathIds)
        fetchPathIds.clear()
        assertEquals(0, guardedRepository.fetchForFamily(FAMILY_ID))
        assertEquals(listOf(allowed.id), fetchPathIds)
        assertEquals(listOf(cached), dao.getMediaCacheOldestFirst())
        assertEquals(metadataBefore, dao.getAllExerciseMedia())
        assertEquals(listOf("${allowed.id}.jpg"), File(context.filesDir, "media").list()!!.toList())
    }

    @Test
    fun clearCacheDeletesFilesAndRowsButPreservesMetadataAndProvenance() = runBlocking {
        val rows = listOf(media("first"), media("second"), media("reference-only", null))
        dao.upsertExerciseMedia(rows)
        val first = seedCache("first", 1L, 40L)
        val second = seedCache("second", 2L, 60L)
        val exerciseBefore = dao.getExercise(EXERCISE_ID)
        val metadataBefore = dao.getAllCanonicalMetadata()
        val mediaBefore = dao.getAllExerciseMedia()
        val identitiesBefore = dao.getAllSourceIdentities()
        val unrelated = File(context.filesDir, "keep.txt").apply { writeText("not media") }

        // A decodable cached image resolves using only local storage, with no fetch.
        assertEquals(first.localPath, repository.cachedUriFor("first"))
        val bitmap = BitmapFactory.decodeFile(first.localPath)
        assertNotNull(bitmap)
        bitmap?.recycle()
        assertEquals(100L, repository.cacheSizeBytes())
        repository.clearCache()

        assertFalse(File(first.localPath).exists())
        assertFalse(File(second.localPath).exists())
        assertTrue(dao.getMediaCacheOldestFirst().isEmpty())
        assertEquals(0L, repository.cacheSizeBytes())
        assertNull(repository.cachedUriFor("first"))
        assertEquals(exerciseBefore, dao.getExercise(EXERCISE_ID))
        assertEquals(metadataBefore, dao.getAllCanonicalMetadata())
        assertEquals(mediaBefore, dao.getAllExerciseMedia())
        assertEquals(identitiesBefore, dao.getAllSourceIdentities())
        assertEquals("not media", unrelated.readText())
        assertEquals(ExerciseMediaRepository.CacheStatus(3, 0, 1, 2), repository.cacheStatus(EXERCISE_ID))
        repository.clearCache() // Idempotent when already empty.
        assertEquals(mediaBefore, dao.getAllExerciseMedia())
    }

    @Test
    fun syntheticCapEvictsOldestFirstUntilUnderLimitNotInsertionOrIdOrder() = runBlocking {
        dao.upsertExerciseMedia(listOf(media("z-oldest"), media("m-middle"), media("a-newest")))
        // 340 MiB accounted: evicting the oldest 40 leaves 300, so the middle
        // 100 must also go. Newest 200 survives. Insert in a different order.
        val newest = seedCache("a-newest", 300L, 200L * MIB)
        val oldest = seedCache("z-oldest", 100L, 40L * MIB)
        val middle = seedCache("m-middle", 200L, 100L * MIB)
        val metadataBefore = dao.getAllExerciseMedia()
        assertEquals(340L * MIB, repository.cacheSizeBytes())

        enforceProductionCacheCap()

        assertEquals(listOf(newest), dao.getMediaCacheOldestFirst())
        assertEquals(200L * MIB, repository.cacheSizeBytes())
        assertFalse(File(oldest.localPath).exists())
        assertFalse(File(middle.localPath).exists())
        assertTrue(File(newest.localPath).isFile)
        assertNull(repository.cachedUriFor(oldest.mediaId))
        assertEquals(newest.localPath, repository.cachedUriFor(newest.mediaId))
        assertEquals(metadataBefore, dao.getAllExerciseMedia())
    }

    @Test
    fun syntheticCapKeepsExact256MiBBoundaryAndEvictsAtOneByteOver() = runBlocking {
        dao.upsertExerciseMedia(listOf(media("old"), media("new")))
        val old = seedCache("old", 1L, 1L)
        val newest = seedCache("new", 2L, 256L * MIB - 1L)
        enforceProductionCacheCap()
        assertEquals(listOf(old, newest), dao.getMediaCacheOldestFirst())
        assertTrue(File(old.localPath).isFile)
        assertTrue(File(newest.localPath).isFile)

        dao.upsertMediaCache(newest.copy(bytes = 256L * MIB))
        enforceProductionCacheCap()
        assertEquals(listOf(newest.copy(bytes = 256L * MIB)), dao.getMediaCacheOldestFirst())
        assertFalse(File(old.localPath).exists())
        assertTrue(File(newest.localPath).isFile)
        assertEquals(256L * MIB, repository.cacheSizeBytes())
    }

    @Test
    fun realJsonExportExcludesCacheAndRestoresOnlyMediaMetadata() = runBlocking {
        val row = media("backup-image")
        dao.upsertExerciseMedia(listOf(row))
        val cached = seedCache(row.id, 123L, 456L)
        val identitiesBefore = dao.getAllSourceIdentities()
        val backup = backupRepository(db)
        // Real ContentResolver file-URI output, not a hand-built BackupDocument.
        // This deliberately does not exercise the SAF picker/provider UI.
        val withCache = File(root, "with-cache.json")
        backup.export(Uri.fromFile(withCache))
        val withCacheText = withCache.readText()
        val document = Json.decodeFromString<BackupDocument>(withCacheText)
        assertEquals(listOf(row), document.exerciseMedia)
        assertEquals(identitiesBefore, document.exerciseSourceIdentities)
        assertFalse(withCacheText.contains(cached.localPath))
        listOf("exercise_media_cache", "mediaCache", "localPath", "fetchedAtEpochMs", "licenseCheckedAtEpochMs")
            .forEach { assertFalse("Cache field leaked: $it", withCacheText.contains("\"$it\"")) }
        assertEquals(listOf(cached), dao.getMediaCacheOldestFirst())
        assertTrue(File(cached.localPath).isFile)

        repository.clearCache()
        val withoutCache = File(root, "without-cache.json")
        backup.export(Uri.fromFile(withoutCache))
        // Stronger than checking field names: only the export timestamp may
        // differ when cache rows/files are present versus absent. This also
        // detects asset bytes exported under an unexpected field name.
        assertEquals(
            Json.parseToJsonElement(withCacheText).jsonObject - "exportedAtEpochMs",
            Json.parseToJsonElement(withoutCache.readText()).jsonObject - "exportedAtEpochMs",
        )
        val restored = newDatabase()
        try {
            backupRepository(restored).import(Uri.fromFile(withCache))
            assertEquals(listOf(row), restored.catalogDao().getAllExerciseMedia())
            assertEquals(identitiesBefore, restored.catalogDao().getAllSourceIdentities())
            assertTrue(restored.catalogDao().getMediaCacheOldestFirst().isEmpty())
            assertEquals(0L, restored.catalogDao().mediaCacheBytes())
            assertNull(ExerciseMediaRepository(context, restored.catalogDao()).cachedUriFor(row.id))
            assertFalse(File(cached.localPath).exists())
        } finally {
            restored.close()
        }
    }

    private fun newDatabase(): MuscleTomeDatabase =
        Room.inMemoryDatabaseBuilder(context, MuscleTomeDatabase::class.java)
            .allowMainThreadQueries().build()

    private fun backupRepository(database: MuscleTomeDatabase) = BackupRepository(
        context, database, database.userDao(), database.catalogDao(), database.routineDao(),
        database.workoutDao(), database.selectionHistoryDao(), database.exerciseImportReviewDao(),
    )

    private fun media(id: String, license: String? = "Unlicense") = ExerciseMediaEntity(
        id = id, exerciseId = EXERCISE_ID, type = "IMAGE",
        uri = "https://example.invalid/$id.jpg", sourceKey = "test-source",
        attribution = "Original credit", creator = "Original creator",
        licenseName = license, licenseUrl = "https://example.invalid/license",
    )

    private suspend fun seedCache(id: String, fetchedAt: Long, accountedBytes: Long): ExerciseMediaCacheEntity {
        val file = File(File(context.filesDir, "media").apply { mkdirs() }, "$id.jpg")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return ExerciseMediaCacheEntity(id, file.absolutePath, fetchedAt, accountedBytes, 99L)
            .also { dao.upsertMediaCache(it) }
    }

    /** Kotlin suspend ABI bridge; handles both immediate and suspended completion. */
    private suspend fun enforceProductionCacheCap(): Unit = suspendCoroutineUninterceptedOrReturn { continuation ->
        val method = ExerciseMediaRepository::class.java.getDeclaredMethod(
            "enforceCacheCap", Continuation::class.java,
        ).apply { isAccessible = true }
        try {
            method.invoke(repository, continuation)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }

    private companion object {
        const val EXERCISE_ID = "media-regression-press"
        const val FAMILY_ID = "media-regression-family"
        const val MIB = 1024L * 1024L
    }
}
