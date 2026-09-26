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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class AddSlotUiState(
    val query: String = "",
    val exercises: List<ExerciseEntity> = emptyList(),
    val selectedExerciseIds: Set<String> = emptySet(),
    val muscleGroups: List<MuscleGroupEntity> = emptyList(),
    val selectedMuscleIds: Set<String> = emptySet(),
    val targetMovement: TargetMovementType = TargetMovementType.ANY,
    val isTargetMode: Boolean = false,
    /** Insert all picked fixed exercises as one round-robin group. */
    val asSuperset: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddSlotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])

    private val query = MutableStateFlow("")
    private val selectedExerciseIds = MutableStateFlow<Set<String>>(emptySet())
    private val saved = MutableStateFlow(false)

    private val isTarget = MutableStateFlow(false)
    private val selectedMuscleIds = MutableStateFlow<Set<String>>(emptySet())
    private val targetMovement = MutableStateFlow(TargetMovementType.ANY)
    private val asSuperset = MutableStateFlow(false)

    private val exerciseResults = query
        .debounce(150.milliseconds)
        .flatMapLatest { q -> catalogRepository.searchExercises(q, null) }

    val uiState = combine(
        exerciseResults,
        catalogRepository.observeMuscleGroups(),
        query,
        selectedExerciseIds,
        selectedMuscleIds,
        targetMovement,
        isTarget,
        asSuperset,
        saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val searchResults = values[0] as List<ExerciseEntity>
        @Suppress("UNCHECKED_CAST")
        val allMuscles = values[1] as List<MuscleGroupEntity>
        val currentQuery = values[2] as String
        @Suppress("UNCHECKED_CAST")
        val selectedIds = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val selectedMuscles = values[4] as Set<String>
        val currentTargetMovement = values[5] as TargetMovementType
        val isTargetMode = values[6] as Boolean
        val wantsSuperset = values[7] as Boolean
        val isSaved = values[8] as Boolean

        val filteredMuscles = allMuscles.filter { it.name.contains(currentQuery, ignoreCase = true) }

        val canSave = if (isTargetMode) selectedMuscles.isNotEmpty() else selectedIds.isNotEmpty()

        AddSlotUiState(
            query = currentQuery,
            exercises = searchResults,
            selectedExerciseIds = selectedIds,
            muscleGroups = filteredMuscles,
            selectedMuscleIds = selectedMuscles,
            targetMovement = currentTargetMovement,
            isTargetMode = isTargetMode,
            // Only meaningful with 2+ fixed picks; auto-clears otherwise so a
            // stale toggle can't silently group a single exercise.
            asSuperset = wantsSuperset && (!isTargetMode) && (selectedIds.size > 1),
            canSave = canSave,
            saved = isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddSlotUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onExerciseSelected(id: String) {
        selectedExerciseIds.value = selectedExerciseIds.value.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
    }

    fun setTargetMode(value: Boolean) { isTarget.value = value }

    fun setAsSuperset(value: Boolean) { asSuperset.value = value }
    fun toggleMuscle(id: String) {
        selectedMuscleIds.value = selectedMuscleIds.value.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
    }
    fun setTargetMovement(value: TargetMovementType) { targetMovement.value = value }

    fun save() {
        val state = uiState.value
        viewModelScope.launch {
            if (state.isTargetMode) {
                if (state.selectedMuscleIds.isEmpty()) return@launch
                // One TARGET slot per selected muscle group
                state.selectedMuscleIds.forEach { muscleId ->
                    routineRepository.addTargetSlot(
                        dayId = dayId,
                        muscleGroupIds = listOf(muscleId),
                        movementType = state.targetMovement,
                    )
                }
            } else {
                if (state.selectedExerciseIds.isEmpty()) return@launch
                routineRepository.addFixedSlots(
                    dayId = dayId,
                    exerciseIds = state.selectedExerciseIds.toList(),
                    asSuperset = state.asSuperset,
                )
            }
            saved.value = true
        }
    }
}
