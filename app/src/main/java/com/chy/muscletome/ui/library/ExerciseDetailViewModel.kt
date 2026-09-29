package com.chy.muscletome.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.dao.ExerciseWithCanonicalRelations
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.media.ExerciseMediaRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    private val downloadable = MutableStateFlow(false)
    private val fullyCached = MutableStateFlow(false)

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

    val uiState = combine(
        combine(
            catalogRepository.observeExercise(exerciseId),
            catalogRepository.observeCanonicalExercise(exerciseId),
            catalogRepository.observeEquipmentForExercise(exerciseId),
            catalogRepository.observeSecondaryMusclesForExercise(exerciseId),
            catalogRepository.observeMuscleGroups(),
            history,
            deleteBlockedMessage,
            deleted,
            cachedMediaUris,
            downloadable,
            fullyCached,
        ) { values ->
            val exercise = values[0] as ExerciseEntity?
            @Suppress("UNCHECKED_CAST")
            val equipment = values[1] as List<EquipmentEntity>
            @Suppress("UNCHECKED_CAST")
            val secondaryMuscles = values[2] as List<MuscleGroupEntity>
            @Suppress("UNCHECKED_CAST")
            val muscleGroups = values[3] as List<MuscleGroupEntity>
            val canonical = values[4] as ExerciseWithCanonicalRelations?
            val currentHistory = values[5] as ExerciseHistory
            val blockedMessage = values[6] as String?
            val isDeleted = values[7] as Boolean
            @Suppress("UNCHECKED_CAST")
            val cachedUris = values[8] as Map<String, String>
            val isDownloadable = values[9] as Boolean
            val isFullyCached = values[10] as Boolean

            ExerciseDetailUiState(
                exercise = exercise,
                userOwned = canonical?.metadata?.let {
                    it.origin == "USER_CREATED" || it.isUserEdited
                } ?: (exercise?.isCustom == true),
                deleteBlockedMessage = blockedMessage,
                deleted = isDeleted,
                cachedMediaUris = cachedUris,
                downloadableMedia = isDownloadable,
                mediaFullyCached = isFullyCached,
                equipment = equipment,
                secondaryMuscles = secondaryMuscles,
                history = currentHistory,
                primaryMuscleName = exercise?.primaryMuscleGroupId
                    ?.let { id -> muscleGroups.find { it.id == id }?.name }
                    .orEmpty(),
                loaded = true,
                canonical = canonical,
            )
        },
        userRepository.observeUser(),
    ) { detail, user ->
        detail.copy(weightUnit = user?.weightUnit ?: WeightUnit.KG)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseDetailUiState(),
    )
}
