package com.chy.muscletome.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.CreateExerciseResult
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseMedia
import com.chy.muscletome.domain.model.ExerciseMediaType
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
    val instructionsText: String = "",
    val muscles: List<MuscleGroupEntity> = emptyList(),
    val equipment: List<EquipmentEntity> = emptyList(),
    val primaryMuscleGroupId: String? = null,
    val secondaryMuscleIds: Set<String> = emptySet(),
    val movementType: MovementType = MovementType.COMPOUND,
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val unilateral: Boolean = false,
    val selectedEquipmentIds: Set<String> = emptySet(),
    val mediaUri: String = "",
    val nameTaken: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddExerciseViewModel @Inject constructor(private val repository: CatalogRepository) : ViewModel() {
    private val name = MutableStateFlow("")
    private val description = MutableStateFlow("")
    private val instructionsText = MutableStateFlow("")
    private val primaryMuscleGroupId = MutableStateFlow<String?>(null)
    private val secondaryMuscleIds = MutableStateFlow<Set<String>>(emptySet())
    private val movementType = MutableStateFlow(MovementType.COMPOUND)
    private val movementPattern = MutableStateFlow(MovementPattern.OTHER)
    private val difficulty = MutableStateFlow(Difficulty.INTERMEDIATE)
    private val unilateral = MutableStateFlow(false)
    private val selectedEquipmentIds = MutableStateFlow<Set<String>>(emptySet())
    private val mediaUri = MutableStateFlow("")
    private val saved = MutableStateFlow(false)

    private val nameTaken = name.debounce(250.milliseconds).flatMapLatest { value ->
        if (value.isBlank()) flowOf(false) else repository.observeNameTaken(value)
    }

    val uiState = combine(
        repository.observeMuscleGroups(), repository.observeEquipment(), name, description, instructionsText,
        primaryMuscleGroupId, secondaryMuscleIds, movementType, movementPattern, difficulty, unilateral,
        selectedEquipmentIds, mediaUri, nameTaken, saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val muscles = values[0] as List<MuscleGroupEntity>
        @Suppress("UNCHECKED_CAST")
        val equipment = values[1] as List<EquipmentEntity>
        val currentName = values[2] as String
        val currentDescription = values[3] as String
        val instructions = values[4] as String
        val primaryId = values[5] as String?
        @Suppress("UNCHECKED_CAST")
        val secondaryIds = values[6] as Set<String>
        val currentType = values[7] as MovementType
        val pattern = values[8] as MovementPattern
        val level = values[9] as Difficulty
        val oneSided = values[10] as Boolean
        @Suppress("UNCHECKED_CAST")
        val equipmentIds = values[11] as Set<String>
        val imageUri = values[12] as String
        val taken = values[13] as Boolean
        val isSaved = values[14] as Boolean
        AddExerciseUiState(
            name = currentName, description = currentDescription, instructionsText = instructions,
            muscles = muscles, equipment = equipment, primaryMuscleGroupId = primaryId,
            secondaryMuscleIds = secondaryIds, movementType = currentType, movementPattern = pattern,
            difficulty = level, unilateral = oneSided, selectedEquipmentIds = equipmentIds,
            mediaUri = imageUri, nameTaken = taken,
            canSave = currentName.isNotBlank() && !taken && primaryId != null,
            saved = isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExerciseUiState())

    fun onNameChange(value: String) { name.value = value }
    fun onDescriptionChange(value: String) { description.value = value }
    fun onInstructionsChange(value: String) { instructionsText.value = value }
    fun onMediaUriChange(value: String) { mediaUri.value = value }
    fun onPrimaryMuscleSelected(id: String) { primaryMuscleGroupId.value = id }
    fun onMovementTypeSelected(value: MovementType) { movementType.value = value }
    fun onMovementPatternSelected(value: MovementPattern) { movementPattern.value = value }
    fun onDifficultySelected(value: Difficulty) { difficulty.value = value }
    fun onUnilateralChange(value: Boolean) { unilateral.value = value }
    fun toggleSecondaryMuscle(id: String) {
        secondaryMuscleIds.value = secondaryMuscleIds.value.toMutableSet().also { if (!it.add(id)) it.remove(id) }
    }
    fun toggleEquipment(id: String) {
        selectedEquipmentIds.value = selectedEquipmentIds.value.toMutableSet().also { if (!it.add(id)) it.remove(id) }
    }

    fun save() {
        val state = uiState.value
        val primary = state.primaryMuscleGroupId ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            val media = state.mediaUri.trim().takeIf(String::isNotBlank)?.let {
                listOf(ExerciseMedia(ExerciseMediaType.IMAGE, uri = it, sourceKey = "user"))
            }.orEmpty()
            val result = repository.createCustomExercise(
                name = state.name,
                description = state.description,
                primaryMuscleGroupId = primary,
                movementType = state.movementType,
                movementPattern = state.movementPattern,
                difficulty = state.difficulty,
                equipmentIds = state.selectedEquipmentIds.toList(),
                secondaryMuscleGroupIds = state.secondaryMuscleIds.toList(),
                instructions = state.instructionsText.lines().map(String::trim).filter(String::isNotBlank),
                unilateral = state.unilateral,
                media = media,
            )
            if (result == CreateExerciseResult.SUCCESS) saved.value = true
        }
    }
}
