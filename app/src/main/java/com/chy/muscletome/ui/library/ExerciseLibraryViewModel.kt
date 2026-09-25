package com.chy.muscletome.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.repository.CatalogRepository
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
import kotlin.time.Duration.Companion.milliseconds

data class ExerciseLibraryUiState(
    val query: String = "",
    val selectedMuscleId: String? = null,
    val muscleGroups: List<MuscleGroupEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedMuscleId = MutableStateFlow<String?>(null)

    private val exerciseResults = combine(query, selectedMuscleId) { q, m -> q to m }
        .debounce(150.milliseconds)
        .flatMapLatest { (q, m) -> repository.searchExercises(q, m) }

    val uiState = combine(
        exerciseResults,
        repository.observeMuscleGroups(),
        query,
        selectedMuscleId,
    ) { exercises, muscles, currentQuery, muscleId ->
        ExerciseLibraryUiState(
            query = currentQuery,
            selectedMuscleId = muscleId,
            muscleGroups = muscles,
            exercises = exercises,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseLibraryUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onMuscleSelected(muscleId: String?) {
        selectedMuscleId.value = muscleId
    }
}
