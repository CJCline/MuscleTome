package com.chy.muscletome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: UserEntity? = null,
    val equipment: List<EquipmentEntity> = emptyList(),
    val availableEquipmentIds: Set<String> = emptySet(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val excludedExerciseIds: Set<String> = emptySet(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    val uiState = combine(
        userRepository.observeUser(),
        catalogRepository.observeEquipment(),
        userRepository.observeAvailableEquipmentIds(),
        catalogRepository.observeExercises(),
        userRepository.observeExcludedExerciseIds(),
    ) { user, equipment, available, exercises, excluded ->
        SettingsUiState(
            user = user,
            equipment = equipment,
            availableEquipmentIds = available.toSet(),
            exercises = exercises,
            excludedExerciseIds = excluded.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setUnit(unit: WeightUnit) = viewModelScope.launch { userRepository.setWeightUnit(unit) }
    fun setStrictness(value: MatchStrictness) = viewModelScope.launch { userRepository.setMatchStrictness(value) }
    fun setPreferCompoundEarly(value: Boolean) = viewModelScope.launch { userRepository.setPreferCompoundEarly(value) }

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
}