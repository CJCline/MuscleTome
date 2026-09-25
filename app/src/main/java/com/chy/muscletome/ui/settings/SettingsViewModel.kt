package com.chy.muscletome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WgerImportRepository
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: UserEntity? = null,
    val equipment: List<EquipmentEntity> = emptyList(),
    val availableEquipmentIds: Set<String> = emptySet(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val excludedExerciseIds: Set<String> = emptySet(),
    val excludedExercises: List<ExerciseEntity> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    catalogRepository: CatalogRepository,
    private val wgerImportRepository: WgerImportRepository,
) : ViewModel() {

    val uiState = combine(
        userRepository.observeUser(),
        catalogRepository.observeEquipment(),
        userRepository.observeAvailableEquipmentIds(),
        catalogRepository.observeExercises(),
        userRepository.observeExcludedExerciseIds(),
    ) { user, equipment, available, exercises, excluded ->
        val excludedIds = excluded.toSet()
        SettingsUiState(
            user = user,
            equipment = equipment,
            availableEquipmentIds = available.toSet(),
            exercises = exercises,
            excludedExerciseIds = excludedIds,
            excludedExercises = exercises.filter { it.id in excludedIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val excludeQuery = MutableStateFlow("")

    val excludeSearchQuery: StateFlow<String> = excludeQuery.asStateFlow()

    /** Exercises matching the search query that are not excluded yet. */
    val excludeSearchResults: StateFlow<List<ExerciseEntity>> = combine(
        uiState,
        excludeQuery,
    ) { state, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            state.exercises.asSequence()
                .filter { it.id !in state.excludedExerciseIds }
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(MAX_EXCLUDE_SEARCH_RESULTS)
                .toList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importProgress = wgerImportRepository.progress

    fun importFromWger() {
        viewModelScope.launch {
            wgerImportRepository.importAll()
        }
    }

    fun setUnit(unit: WeightUnit) = viewModelScope.launch { userRepository.setWeightUnit(unit) }
    fun setStrictness(value: MatchStrictness) = viewModelScope.launch { userRepository.setMatchStrictness(value) }
    fun setPreferCompoundEarly(value: Boolean) = viewModelScope.launch { userRepository.setPreferCompoundEarly(value) }

    fun onExcludeSearchQueryChange(value: String) {
        excludeQuery.value = value
    }

    fun toggleEquipment(id: String) {
        val next = uiState.value.availableEquipmentIds.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
        viewModelScope.launch { userRepository.setAvailableEquipment(next) }
    }

    fun toggleExcluded(id: String) {
        viewModelScope.launch {
            if (id in uiState.value.excludedExerciseIds) userRepository.includeExercise(id)
            else userRepository.excludeExercise(id)
        }
    }

    private companion object {
        const val MAX_EXCLUDE_SEARCH_RESULTS = 20
    }
}
