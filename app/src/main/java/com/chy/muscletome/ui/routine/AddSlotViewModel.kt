package com.chy.muscletome.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.domain.model.TargetMovementType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddSlotUiState(
    val query: String = "",
    val exercises: List<ExerciseEntity> = emptyList(),
    val selectedExerciseId: String? = null,
    val muscleGroups: List<MuscleGroupEntity> = emptyList(),
    val selectedMuscleIds: Set<String> = emptySet(),
    val targetMovement: TargetMovementType = TargetMovementType.ANY,
    val isTargetMode: Boolean = false,
    val sets: String = "3",
    val repMin: String = "8",
    val repMax: String = "12",
    val restSeconds: String = "90",
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class AddSlotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])

    private val query = MutableStateFlow("")
    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val sets = MutableStateFlow("3")
    private val repMin = MutableStateFlow("8")
    private val repMax = MutableStateFlow("12")
    private val restSeconds = MutableStateFlow("90")
    private val saved = MutableStateFlow(false)

    private val isTarget = MutableStateFlow(false)
    private val selectedMuscleIds = MutableStateFlow<Set<String>>(emptySet())
    private val targetMovement = MutableStateFlow(TargetMovementType.ANY)

    val uiState = combine(
        catalogRepository.observeExercises(),
        catalogRepository.observeMuscleGroups(),
        query,
        selectedExerciseId,
        selectedMuscleIds,
        targetMovement,
        isTarget,
        sets,
        repMin,
        repMax,
        restSeconds,
        saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allExercises = values[0] as List<ExerciseEntity>
        @Suppress("UNCHECKED_CAST")
        val allMuscles = values[1] as List<MuscleGroupEntity>
        val currentQuery = values[2] as String
        val selectedId = values[3] as String?
        @Suppress("UNCHECKED_CAST")
        val selectedMuscles = values[4] as Set<String>
        val currentTargetMovement = values[5] as TargetMovementType
        val isTargetMode = values[6] as Boolean
        val setsText = values[7] as String
        val minText = values[8] as String
        val maxText = values[9] as String
        val restText = values[10] as String
        val isSaved = values[11] as Boolean

        val filteredExercises = allExercises.filter { it.name.contains(currentQuery, ignoreCase = true) }
        val filteredMuscles = allMuscles.filter { it.name.contains(currentQuery, ignoreCase = true) }

        val canSave = (if (isTargetMode) selectedMuscles.isNotEmpty() else selectedId != null) &&
                setsText.toIntOrNull() != null &&
                minText.toIntOrNull() != null &&
                maxText.toIntOrNull() != null &&
                restText.toIntOrNull() != null

        AddSlotUiState(
            query = currentQuery,
            exercises = filteredExercises,
            selectedExerciseId = selectedId,
            muscleGroups = filteredMuscles,
            selectedMuscleIds = selectedMuscles,
            targetMovement = currentTargetMovement,
            isTargetMode = isTargetMode,
            sets = setsText,
            repMin = minText,
            repMax = maxText,
            restSeconds = restText,
            canSave = canSave,
            saved = isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddSlotUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onExerciseSelected(id: String) { selectedExerciseId.value = id }
    fun onSetsChange(value: String) { sets.value = value }
    fun onRepMinChange(value: String) { repMin.value = value }
    fun onRepMaxChange(value: String) { repMax.value = value }
    fun onRestChange(value: String) { restSeconds.value = value }

    fun setTargetMode(value: Boolean) { isTarget.value = value }
    fun toggleMuscle(id: String) {
        selectedMuscleIds.value = selectedMuscleIds.value.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
    }
    fun setTargetMovement(value: TargetMovementType) { targetMovement.value = value }

    fun save() {
        val state = uiState.value
        val setCount = state.sets.toIntOrNull() ?: return
        val min = state.repMin.toIntOrNull() ?: return
        val max = state.repMax.toIntOrNull() ?: return
        val rest = state.restSeconds.toIntOrNull() ?: return
        viewModelScope.launch {
            if (state.isTargetMode) {
                routineRepository.addTargetSlot(
                    dayId = dayId,
                    muscleGroupIds = state.selectedMuscleIds.toList(),
                    movementType = state.targetMovement,
                    sets = setCount,
                    repMin = min,
                    repMax = max,
                    restSeconds = rest,
                )
            } else {
                val exerciseId = state.selectedExerciseId ?: return@launch
                routineRepository.addFixedSlot(dayId, exerciseId, setCount, min, max, rest)
            }
            saved.value = true
        }
    }
}
