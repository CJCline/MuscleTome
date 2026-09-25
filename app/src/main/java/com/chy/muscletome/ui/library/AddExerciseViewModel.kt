package com.chy.muscletome.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.CreateExerciseResult
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class AddExerciseUiState(
    val name: String = "",
    val description: String = "",
    val muscles: List<MuscleGroupEntity> = emptyList(),
    val equipment: List<EquipmentEntity> = emptyList(),
    val primaryMuscleGroupId: String? = null,
    val movementType: MovementType = MovementType.COMPOUND,
    val selectedEquipmentIds: Set<String> = emptySet(),
    val nameTaken: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    private val nameTaken = name
        .debounce(250.milliseconds)
        .flatMapLatest { value ->
            if (value.isBlank()) flowOf(false) else repository.observeNameTaken(value)
        }

    val uiState = combine(
        repository.observeMuscleGroups(),
        repository.observeEquipment(),
        name,
        description,
        primaryMuscleGroupId,
        movementType,
        selectedEquipmentIds,
        nameTaken,
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
        val isNameTaken = values[7] as Boolean
        val isSaved = values[8] as Boolean

        AddExerciseUiState(
            name = currentName,
            description = currentDescription,
            muscles = muscles,
            equipment = equipment,
            primaryMuscleGroupId = muscleId,
            movementType = type,
            selectedEquipmentIds = equipmentIds,
            nameTaken = isNameTaken,
            canSave = currentName.isNotBlank() && !isNameTaken && muscleId != null && equipmentIds.isNotEmpty(),
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
            val result = repository.createCustomExercise(
                name = state.name,
                description = state.description,
                primaryMuscleGroupId = muscleId,
                movementType = state.movementType,
                movementPattern = MovementPattern.OTHER,
                difficulty = Difficulty.INTERMEDIATE,
                equipmentIds = state.selectedEquipmentIds.toList(),
                secondaryMuscleGroupIds = emptyList(),
            )
            if (result == CreateExerciseResult.SUCCESS) {
                saved.value = true
            }
        }
    }
}
