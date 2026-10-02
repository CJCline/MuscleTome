package com.chy.muscletome.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.media.ExerciseMediaRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.repository.ActiveSlotOverrideOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class ExerciseHistory(
    val lastSet: SetLogEntity? = null,
    val bestWeight: Double? = null,
    val sessionCount: Int = 0,
)

data class ExerciseDetailUiState(
    val exercise: ExerciseEntity? = null,
    /** Phase 5B: editable when user-created or user-edited. */
    val userOwned: Boolean = false,
    /** Non-null after a blocked delete attempt (exercise referenced). */
    val deleteBlockedMessage: String? = null,
    /** True after a successful delete — the screen pops back. */
    val deleted: Boolean = false,
    /** Phase 5C: mediaId → local cached file path (blank map when uncached). */
    val cachedMediaUris: Map<String, String> = emptyMap(),
    /** Any license-allowed media exists (download action offered). */
    val downloadableMedia: Boolean = false,
    /** All license-allowed media already cached (offline-ready badge). */
    val mediaFullyCached: Boolean = false,
    val equipment: List<EquipmentEntity> = emptyList(),
    val secondaryMuscles: List<MuscleGroupEntity> = emptyList(),
    val history: ExerciseHistory = ExerciseHistory(),
    val primaryMuscleName: String = "",
    val loaded: Boolean = false,
    /** Sets display in the user's unit (they're stored in it). */
    val weightUnit: WeightUnit = WeightUnit.KG,
    val canonical: ExerciseWithCanonicalRelations? = null,
    val activeSessionSlots: List<ActiveSlotOverrideOption> = emptyList(),
)

/** Typed stages keep heterogeneous sources aligned and allow testing without repositories. */
internal fun exerciseDetailStateFlow(
    exercise: Flow<ExerciseEntity?>,
    canonical: Flow<ExerciseWithCanonicalRelations?>,
    equipment: Flow<List<EquipmentEntity>>,
    secondaryMuscles: Flow<List<MuscleGroupEntity>>,
    muscleGroups: Flow<List<MuscleGroupEntity>>,
    history: Flow<ExerciseHistory>,
    deleteBlockedMessage: Flow<String?>,
    deleted: Flow<Boolean>,
    cachedMediaUris: Flow<Map<String, String>>,
    downloadable: Flow<Boolean>,
    fullyCached: Flow<Boolean>,
    user: Flow<UserEntity?>,
    activeSessionSlots: Flow<List<ActiveSlotOverrideOption>>,
): Flow<ExerciseDetailUiState> {
    val catalog = combine(
        exercise, canonical, equipment, secondaryMuscles, muscleGroups,
    ) { currentExercise, currentCanonical, currentEquipment, currentSecondary, currentMuscles ->
        ExerciseDetailUiState(
            exercise = currentExercise,
            userOwned = currentCanonical?.metadata?.let {
                it.origin == "USER_CREATED" || it.isUserEdited
            } ?: (currentExercise?.isCustom == true),
            equipment = currentEquipment,
            secondaryMuscles = currentSecondary,
            primaryMuscleName = currentExercise?.primaryMuscleGroupId
                ?.let { id -> currentMuscles.find { it.id == id }?.name }
                .orEmpty(),
            loaded = true,
            canonical = currentCanonical,
        )
    }
    val detail = combine(
        catalog, history, deleteBlockedMessage, deleted,
    ) { currentCatalog, currentHistory, blockedMessage, isDeleted ->
        currentCatalog.copy(
            history = currentHistory,
            deleteBlockedMessage = blockedMessage,
            deleted = isDeleted,
        )
    }
    return combine(
        detail, cachedMediaUris, downloadable, fullyCached, user, activeSessionSlots,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val currentDetail = values[0] as ExerciseDetailUiState
        @Suppress("UNCHECKED_CAST")
        val cachedUris = values[1] as Map<String, String>
        val isDownloadable = values[2] as Boolean
        val isFullyCached = values[3] as Boolean
        val currentUser = values[4] as UserEntity?
        @Suppress("UNCHECKED_CAST")
        val slots = values[5] as List<ActiveSlotOverrideOption>

        currentDetail.copy(
            cachedMediaUris = cachedUris,
            downloadableMedia = isDownloadable,
            mediaFullyCached = isFullyCached,
            weightUnit = currentUser?.weightUnit ?: WeightUnit.KG,
            activeSessionSlots = slots,
        )
    }
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val workoutRepository: WorkoutRepository,
    userRepository: UserRepository,
    private val mediaRepository: ExerciseMediaRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])

    private val history = MutableStateFlow(ExerciseHistory())
    private val deleteBlockedMessage = MutableStateFlow<String?>(null)
    private val deleted = MutableStateFlow(false)
    private val cachedMediaUris = MutableStateFlow<Map<String, String>>(emptyMap())
    private val downloadable = MutableStateFlow(false)
    private val fullyCached = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeSessionSlots: Flow<List<ActiveSlotOverrideOption>> =
        workoutRepository.observeOpenSession().flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else {
                combine(
                    workoutRepository.observeSlotResults(session.id),
                    catalogRepository.observeExercises(),
                ) { results, exercises ->
                    val exerciseMap = exercises.associateBy { it.id }
                    results.sortedBy { it.sortOrder }.map { result ->
                        ActiveSlotOverrideOption(
                            result = result,
                            currentExerciseName = exerciseMap[result.resolvedExerciseId]?.name ?: "Exercise",
                            sortOrder = result.sortOrder,
                        )
                    }
                }
            }
        }

    init {
        viewModelScope.launch { refreshMediaCache() }
    }

    /** Phase 5C: (re)load cache status; re-run after a download completes. */
    private suspend fun refreshMediaCache() {
        val status = mediaRepository.cacheStatus(exerciseId)
        cachedMediaUris.value = buildMap {
            catalogRepository.getCanonicalExercise(exerciseId)?.media?.forEach { row ->
                mediaRepository.cachedUriFor(row.id)?.let { put(row.id, it) }
            }
        }
        downloadable.value = status.cacheable > 0
        fullyCached.value = status.cacheable > 0 && status.cached >= status.cacheable
    }

    /** Kicks the on-demand fetch for this exercise, then refreshes status. */
    fun downloadMedia() {
        viewModelScope.launch {
            mediaRepository.fetchForExercise(exerciseId)
            refreshMediaCache()
        }
    }

    fun deleteUserExercise() {
        viewModelScope.launch {
            if (catalogRepository.deleteCustomExercise(exerciseId)) {
                deleted.value = true
            } else {
                deleteBlockedMessage.value =
                    "This exercise is used in routines or logged workouts, so it can't be deleted."
            }
        }
    }

    fun dismissDeleteBlocked() {
        deleteBlockedMessage.value = null
    }

    fun replaceInActiveWorkout(result: SessionSlotResultEntity) {
        viewModelScope.launch {
            workoutRepository.overrideSlot(result, exerciseId)
        }
    }

    init {
        viewModelScope.launch {
            history.value = ExerciseHistory(
                lastSet = workoutRepository.lastSetForExercise(exerciseId),
                bestWeight = workoutRepository.bestWeightForExercise(exerciseId),
                sessionCount = workoutRepository.sessionCountForExercise(exerciseId),
            )
        }
    }

    val uiState = exerciseDetailStateFlow(
        exercise = catalogRepository.observeExercise(exerciseId),
        canonical = catalogRepository.observeCanonicalExercise(exerciseId),
        equipment = catalogRepository.observeEquipmentForExercise(exerciseId),
        secondaryMuscles = catalogRepository.observeSecondaryMusclesForExercise(exerciseId),
        muscleGroups = catalogRepository.observeMuscleGroups(),
        history = history,
        deleteBlockedMessage = deleteBlockedMessage,
        deleted = deleted,
        cachedMediaUris = cachedMediaUris,
        downloadable = downloadable,
        fullyCached = fullyCached,
        user = userRepository.observeUser(),
        activeSessionSlots = activeSessionSlots,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseDetailUiState(),
    )
}
