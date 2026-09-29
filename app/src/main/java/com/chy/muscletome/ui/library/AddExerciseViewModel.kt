package com.chy.muscletome.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.MovementFamilyEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.CreateExerciseResult
import com.chy.muscletome.data.repository.FamilyRepository
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
    val isEdit: Boolean = false,
    val name: String = "",
    val description: String = "",
    val instructionsText: String = "",
    val muscles: List<MuscleGroupEntity> = emptyList(),
    val equipment: List<EquipmentEntity> = emptyList(),
    val families: List<MovementFamilyEntity> = emptyList(),
    val primaryMuscleGroupId: String? = null,
    val secondaryMuscleIds: Set<String> = emptySet(),
    val movementType: MovementType = MovementType.COMPOUND,
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val unilateral: Boolean = false,
    val selectedEquipmentIds: Set<String> = emptySet(),
    val selectedFamilyId: String? = null,
    val newFamilyName: String = "",
    val newFamilyError: String? = null,
    val mediaUri: String = "",
    val nameTaken: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddExerciseViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val familyRepository: FamilyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** Non-null in edit mode (route: add_exercise?exerciseId={exerciseId}). */
    private val editExerciseId: String? =
        savedStateHandle.get<String>("exerciseId")?.takeIf { it.isNotBlank() }

    private val isEdit = MutableStateFlow(editExerciseId != null)
    private val prefilled = MutableStateFlow(false)
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
    private val selectedFamilyId = MutableStateFlow<String?>(null)
    private val newFamilyName = MutableStateFlow("")
    private val newFamilyError = MutableStateFlow<String?>(null)
    private val mediaUri = MutableStateFlow("")
    private val saved = MutableStateFlow(false)

    private val nameTaken = name.debounce(250.milliseconds).flatMapLatest { value ->
        if (value.isBlank()) flowOf(false) else repository.observeNameTaken(value)
    }

    init {
        if (editExerciseId != null) {
            viewModelScope.launch {
                // One-shot prefill from the current canonical record.
                val snapshot = repository.getCanonicalExercise(editExerciseId)
                if (snapshot != null) {
                    name.value = snapshot.exercise.name
                    description.value = snapshot.exercise.description
                    instructionsText.value = snapshot.metadata?.instructions.orEmpty()
                    primaryMuscleGroupId.value = snapshot.exercise.primaryMuscleGroupId
                    secondaryMuscleIds.value = snapshot.secondaryTargets.map { it.id }.toSet()
                    movementType.value = snapshot.exercise.movementType
                    movementPattern.value = snapshot.exercise.movementPattern
                    difficulty.value = snapshot.exercise.difficulty
                    unilateral.value = snapshot.exercise.unilateral
                    selectedEquipmentIds.value = snapshot.equipment.map { it.id }.toSet()
                    selectedFamilyId.value = snapshot.metadata?.movementFamilyId
                    mediaUri.value = snapshot.media.firstOrNull()?.uri.orEmpty()
                }
                prefilled.value = true
            }
        } else {
            prefilled.value = true
        }
    }

    val uiState = combine(
        repository.observeMuscleGroups(), repository.observeEquipment(),
        familyRepository.observeFamilies(),
        isEdit, prefilled, name, description, instructionsText,
        primaryMuscleGroupId, secondaryMuscleIds, movementType, movementPattern, difficulty, unilateral,
        selectedEquipmentIds, selectedFamilyId, newFamilyName, newFamilyError, mediaUri, nameTaken, saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val muscles = values[0] as List<MuscleGroupEntity>
        @Suppress("UNCHECKED_CAST")
        val equipment = values[1] as List<EquipmentEntity>
        @Suppress("UNCHECKED_CAST")
        val families = values[2] as List<MovementFamilyEntity>
        val edit = values[3] as Boolean
        val ready = values[4] as Boolean
        val currentName = values[5] as String
        val currentDescription = values[6] as String
        val instructions = values[7] as String
        val primaryId = values[8] as String?
        @Suppress("UNCHECKED_CAST")
        val secondaryIds = values[9] as Set<String>
        val currentType = values[10] as MovementType
        val pattern = values[11] as MovementPattern
        val level = values[12] as Difficulty
        val oneSided = values[13] as Boolean
        @Suppress("UNCHECKED_CAST")
        val equipmentIds = values[14] as Set<String>
        val familyId = values[15] as String?
        val newFamily = values[16] as String
        val familyError = values[17] as String?
        val imageUri = values[18] as String
        val taken = values[19] as Boolean
        val isSaved = values[20] as Boolean
        AddExerciseUiState(
            isEdit = edit,
            name = currentName, description = currentDescription, instructionsText = instructions,
            muscles = muscles, equipment = equipment, families = families,
            primaryMuscleGroupId = primaryId,
            secondaryMuscleIds = secondaryIds, movementType = currentType, movementPattern = pattern,
            difficulty = level, unilateral = oneSided, selectedEquipmentIds = equipmentIds,
            selectedFamilyId = familyId, newFamilyName = newFamily, newFamilyError = familyError,
            mediaUri = imageUri, nameTaken = taken,
            canSave = ready && currentName.isNotBlank() && !taken && primaryId != null,
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
    fun onFamilySelected(id: String?) { selectedFamilyId.value = id }
    fun onNewFamilyNameChange(value: String) {
        newFamilyName.value = value
        newFamilyError.value = null
    }
    fun toggleSecondaryMuscle(id: String) {
        secondaryMuscleIds.value = secondaryMuscleIds.value.toMutableSet().also { if (!it.add(id)) it.remove(id) }
    }
    fun toggleEquipment(id: String) {
        selectedEquipmentIds.value = selectedEquipmentIds.value.toMutableSet().also { if (!it.add(id)) it.remove(id) }
    }

    /** Creates the typed family name, or surfaces the existing family on collision. */
    fun createFamily() {
        val candidate = newFamilyName.value
        if (candidate.isBlank()) {
            newFamilyError.value = "Family name is required"
            return
        }
        viewModelScope.launch {
            when (val result = familyRepository.createFamily(candidate)) {
                is FamilyRepository.CreateFamilyResult.Created -> {
                    newFamilyName.value = ""
                    selectedFamilyId.value = result.family.id
                }
                is FamilyRepository.CreateFamilyResult.Duplicate -> {
                    newFamilyError.value =
                        "\"${result.existing.displayName}\" already exists (same name, different spelling)"
                    selectedFamilyId.value = result.existing.id
                }
                is FamilyRepository.CreateFamilyResult.Invalid ->
                    newFamilyError.value = result.reason
            }
        }
    }

    fun save() {
        val state = uiState.value
        val primary = state.primaryMuscleGroupId ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            val media = state.mediaUri.trim().takeIf(String::isNotBlank)?.let {
                listOf(ExerciseMedia(ExerciseMediaType.IMAGE, uri = it, sourceKey = "user"))
            }.orEmpty()
            val instructions = state.instructionsText.lines().map(String::trim).filter(String::isNotBlank)
            val result = if (state.isEdit && editExerciseId != null) {
                repository.updateCustomExercise(
                    exerciseId = editExerciseId,
                    name = state.name,
                    description = state.description,
                    primaryMuscleGroupId = primary,
                    movementType = state.movementType,
                    movementPattern = state.movementPattern,
                    difficulty = state.difficulty,
                    equipmentIds = state.selectedEquipmentIds.toList(),
                    secondaryMuscleGroupIds = state.secondaryMuscleIds.toList(),
                    instructions = instructions,
                    unilateral = state.unilateral,
                    movementFamilyId = state.selectedFamilyId,
                    media = media,
                )
            } else {
                repository.createCustomExercise(
                    name = state.name,
                    description = state.description,
                    primaryMuscleGroupId = primary,
                    movementType = state.movementType,
                    movementPattern = state.movementPattern,
                    difficulty = state.difficulty,
                    equipmentIds = state.selectedEquipmentIds.toList(),
                    secondaryMuscleGroupIds = state.secondaryMuscleIds.toList(),
                    instructions = instructions,
                    unilateral = state.unilateral,
                    movementFamilyId = state.selectedFamilyId,
                    media = media,
                )
            }
            if (result == CreateExerciseResult.SUCCESS) saved.value = true
        }
    }
}
