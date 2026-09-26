package com.chy.muscletome.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.UserAvailableEquipmentCrossRef
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Progress/result surface for the Settings screen. */
data class BackupState(
    val running: Boolean = false,
    val lastMessage: String = "",
)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MuscleTomeDatabase,
    private val userDao: UserDao,
    private val catalogDao: CatalogDao,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
) {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Exports everything the user can lose — catalog edits, routines and
     * slots, full log history — to the SAF-picked [uri] as UTF-8 JSON.
     */
    suspend fun export(uri: Uri) = runBackup("Exporting…") {
        val doc = buildDocument()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(json.encodeToString(doc).toByteArray(Charsets.UTF_8))
            } ?: error("Could not open $uri for writing")
        }
    }

    /**
     * Imports a backup JSON from [uri]. All writes are upserts — import is
     * idempotent, which is the whole point of a local-first sync story:
     * restoring on a second device merges rather than replaces. Rows whose
     * referenced parent is missing from the document are skipped rather than
     * aborting the import on a foreign-key violation.
     */
    suspend fun import(uri: Uri) = runBackup("Importing…") {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("Could not read $uri")
        }
        val doc = json.decodeFromString<BackupDocument>(text)
        check(doc.formatVersion <= BackupDocument.FORMAT_VERSION) {
            "Backup format ${doc.formatVersion} is newer than this app supports " +
                "(${BackupDocument.FORMAT_VERSION}). Update the app first."
        }
        check(doc.user.id == SeedCatalog.LOCAL_USER_ID) {
            "Backup belongs to user '${doc.user.id}' — this device expects '${SeedCatalog.LOCAL_USER_ID}'."
        }

        val exerciseIds = doc.exercises.map { it.id }.toHashSet()
        val slotIds = doc.routineSlots.map { it.id }.toHashSet()
        val dayIds = doc.routineDays.map { it.id }.toHashSet()
        val sessionIds = doc.sessions.map { it.id }.toHashSet()

        // Filter to rows whose referents exist in the document itself — a
        // dangling reference must never abort the import mid-transaction.
        val slotResults = doc.slotResults.filter {
            it.sessionId in sessionIds &&
                it.resolvedExerciseId in exerciseIds &&
                (it.routineSlotId == null || it.routineSlotId in slotIds)
        }
        val slotResultIds = slotResults.map { it.id }.toHashSet()
        val setLogs = doc.setLogs.filter { it.sessionSlotResultId in slotResultIds }
        val sessions = doc.sessions.filter {
            it.userId == SeedCatalog.LOCAL_USER_ID &&
                (it.routineDayId == null || it.routineDayId in dayIds)
        }

        database.withTransaction {
            // Order matters — everything references the catalog and user first.
            catalogDao.upsertMuscleGroups(doc.muscleGroups)
            catalogDao.upsertEquipment(doc.equipment)
            catalogDao.upsertExercises(doc.exercises)
            catalogDao.upsertExerciseEquipment(doc.exerciseEquipment)
            catalogDao.upsertSecondaryMuscles(doc.secondaryMuscles)

            userDao.upsert(doc.user)
            userDao.clearAvailableEquipment(doc.user.id)
            userDao.insertAvailableEquipment(doc.availableEquipment)
            userDao.clearExcluded(doc.user.id)
            userDao.insertAllExcluded(doc.excludedExercises)

            routineDao.insertRoutines(doc.routines)
            routineDao.insertDays(doc.routineDays)
            routineDao.insertSlots(doc.routineSlots)
            routineDao.insertSlotTargets(doc.slotTargets)

            workoutDao.insertSessions(
                sessions.map { dto ->
                    WorkoutSessionEntity(
                        id = dto.id,
                        userId = dto.userId,
                        routineDayId = dto.routineDayId,
                        startedAtEpochMs = dto.startedAtEpochMs,
                        endedAtEpochMs = dto.endedAtEpochMs,
                    )
                },
            )
            workoutDao.insertSlotResults(slotResults)
            workoutDao.insertSetLogs(setLogs)
        }
    }

    private suspend fun buildDocument(): BackupDocument {
        val user = userDao.getUser(SeedCatalog.LOCAL_USER_ID) ?: error("No user row to export")
        return BackupDocument(
            appVersion = appVersionName(),
            exportedAtEpochMs = System.currentTimeMillis(),
            user = user,
            muscleGroups = catalogDao.getMuscleGroups(),
            equipment = catalogDao.getEquipment(),
            exercises = catalogDao.getExercises(),
            exerciseEquipment = catalogDao.getExerciseEquipment(),
            secondaryMuscles = catalogDao.getSecondaryMuscles(),
            availableEquipment = userDao.getAvailableEquipmentIds(SeedCatalog.LOCAL_USER_ID)
                .map { UserAvailableEquipmentCrossRef(SeedCatalog.LOCAL_USER_ID, it) },
            excludedExercises = userDao.getExcluded(SeedCatalog.LOCAL_USER_ID),
            routines = routineDao.getRoutines(SeedCatalog.LOCAL_USER_ID),
            routineDays = routineDao.getAllDays(),
            routineSlots = routineDao.getAllSlots(),
            slotTargets = routineDao.getAllSlotTargets(),
            sessions = workoutDao.getSessions(SeedCatalog.LOCAL_USER_ID).map {
                WorkoutSessionEntityDto(
                    id = it.id,
                    userId = it.userId,
                    routineDayId = it.routineDayId,
                    startedAtEpochMs = it.startedAtEpochMs,
                    endedAtEpochMs = it.endedAtEpochMs,
                )
            },
            slotResults = workoutDao.getAllSlotResults(),
            setLogs = workoutDao.getAllSetLogs(),
        )
    }

    private fun appVersionName(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    private suspend fun <T> runBackup(message: String, block: suspend () -> T): T {
        check(!_state.value.running) { "A backup operation is already running." }
        _state.value = BackupState(running = true, lastMessage = message)
        try {
            return block()
        } catch (t: Throwable) {
            _state.value = _state.value.copy(running = false, lastMessage = t.message ?: "Failed")
            throw t
        } finally {
            _state.value = _state.value.copy(running = false)
        }
    }
}
