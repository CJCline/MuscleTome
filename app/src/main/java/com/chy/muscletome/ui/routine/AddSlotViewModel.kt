package com.chy.muscletome.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.RoutineRepository
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

    val uiState = combine(
        catalogRepository.observeExercises(),
        query,
        selectedExerciseId,
        sets,
        repMin,
        repMax,
        restSeconds,
        saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val all = values[0] as List<ExerciseEntity>
        val currentQuery = values[1] as String
        val selectedId = values[2] as String?
        val setsText = values[3] as String
        val minText = values[4] as String
        val maxText = values[5] as String
        val restText = values[6] as String
        val isSaved = values[7] as Boolean
        val filtered = all.filter { it.name.contains(currentQuery, ignoreCase = true) }
        AddSlotUiState(
            query = currentQuery,
            exercises = filtered,
            selectedExerciseId = selectedId,
            sets = setsText,
            repMin = minText,
            repMax = maxText,
            restSeconds = restText,
            canSave = selectedId != null &&
                    setsText.toIntOrNull() != null &&
                    minText.toIntOrNull() != null &&
                    maxText.toIntOrNull() != null &&
                    restText.toIntOrNull() != null,
            saved = isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddSlotUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onExerciseSelected(id: String) { selectedExerciseId.value = id }
    fun onSetsChange(value: String) { sets.value = value }
    fun onRepMinChange(value: String) { repMin.value = value }
    fun onRepMaxChange(value: String) { repMax.value = value }
    fun onRestChange(value: String) { restSeconds.value = value }

    fun save() {
        val state = uiState.value
        val exerciseId = state.selectedExerciseId ?: return
        val setCount = state.sets.toIntOrNull() ?: return
        val min = state.repMin.toIntOrNull() ?: return
        val max = state.repMax.toIntOrNull() ?: return
        val rest = state.restSeconds.toIntOrNull() ?: return
        viewModelScope.launch {
            routineRepository.addFixedSlot(dayId, exerciseId, setCount, min, max, rest)
            saved.value = true
        }
    }
}
