package com.chy.muscletome.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
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
    val familyIds: Map<String, String> = emptyMap(),
    val libraryRows: List<ExerciseLibraryRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val allExercises = query
        .debounce(150.milliseconds)
        .flatMapLatest { q -> repository.observeAllExercises(q) }

    val uiState = combine(
        allExercises,
        query,
    ) { exercisesList, currentQuery ->
        ExerciseLibraryUiState(
            query = currentQuery,
            exercises = exercisesList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseLibraryUiState(),
    )

    fun onQueryChange(value: String) { query.value = value }
}
