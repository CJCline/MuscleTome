package com.chy.regimen.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.regimen.data.local.entity.EquipmentEntity
import com.chy.regimen.data.local.entity.MuscleGroupEntity
import com.chy.regimen.data.repository.CatalogRepository
import com.chy.regimen.domain.model.Difficulty
import com.chy.regimen.domain.model.MovementPattern
import com.chy.regimen.domain.model.MovementType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddExerciseUiState(
    val name: String = "",
    val description: String = "",
    val muscles: List<MuscleGroupEntity> = emptyList(),
    val equipment: List<EquipmentEntity> = emptyList(),
    val primaryMuscleGroupId: String? = null,
    val movementType: MovementType = MovementType.COMPOUND,
    val selectedEquipmentIds: Set<String> = emptySet(),
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class AddExerciseViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val name = MutableStateFlow("")
    private val description = MutableStateFlow("")
    private val primaryMuscleGroupId = MutableStateFlow<String?>(null)
    private val movementType = MutableStateFlow(MovementType.COMPOUND)
    private val selectedEquipmentIds = MutableStateFlow<Set<String>>(emptySet())
    private val saved = MutableStateFlow(false)

    val uiState = combine(
        repository.observeMuscleGroups(),
        repository.observeEquipment(),
        name,
        description,
        primaryMuscleGroupId,
        movementType,
        selectedEquipmentIds,
        saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val muscles = values[0] as List<MuscleGroupEntity>
        @Suppress("UNCHECKED_CAST")
        val equipment = values[1] as List<EquipmentEntity>
        val currentName = values[2] as String
        val currentDescription = values[3] as String
        val muscleId = values[4] as String?
        val type = values[5] as MovementType
        @Suppress("UNCHECKED_CAST")
        val equipmentIds = values[6] as Set<String>
        val isSaved = values[7] as Boolean

        AddExerciseUiState(
            name = currentName,
            description = currentDescription,
            muscles = muscles,
            equipment = equipment,
            primaryMuscleGroupId = muscleId,
            movementType = type,
            selectedEquipmentIds = equipmentIds,
            canSave = currentName.isNotBlank() && muscleId != null && equipmentIds.isNotEmpty(),
            saved = isSaved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddExerciseUiState(),
    )

    fun onNameChange(value: String) { name.value = value }
    fun onDescriptionChange(value: String) { description.value = value }
    fun onPrimaryMuscleSelected(id: String) { primaryMuscleGroupId.value = id }
    fun onMovementTypeSelected(type: MovementType) { movementType.value = type }

    fun toggleEquipment(id: String) {
        selectedEquipmentIds.value = selectedEquipmentIds.value.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
    }

    fun save() {
        val state = uiState.value
        val muscleId = state.primaryMuscleGroupId ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            repository.createCustomExercise(
                name = state.name,
                description = state.description,
                primaryMuscleGroupId = muscleId,
                movementType = state.movementType,
                movementPattern = MovementPattern.OTHER,
                difficulty = Difficulty.INTERMEDIATE,
                equipmentIds = state.selectedEquipmentIds.toList(),
                secondaryMuscleGroupIds = emptyList(),
            )
            saved.value = true
        }
    }
}