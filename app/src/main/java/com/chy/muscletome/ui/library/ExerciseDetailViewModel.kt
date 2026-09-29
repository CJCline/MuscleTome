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
        detail, cachedMediaUris, downloadable, fullyCached, user,
    ) { currentDetail, cachedUris, isDownloadable, isFullyCached, currentUser ->
        currentDetail.copy(
            cachedMediaUris = cachedUris,
            downloadableMedia = isDownloadable,
            mediaFullyCached = isFullyCached,
            weightUnit = currentUser?.weightUnit ?: WeightUnit.KG,
        )
    }
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    workoutRepository: WorkoutRepository,
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
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseDetailUiState(),
    )
}
