package com.chy.regimen.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.regimen.data.local.entity.ExerciseEntity
import com.chy.regimen.data.local.entity.MuscleGroupEntity
import com.chy.regimen.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExerciseLibraryUiState(
    val query: String = "",
    val selectedMuscleId: String? = null,
    val muscleGroups: List<MuscleGroupEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
)

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    repository: CatalogRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedMuscleId = MutableStateFlow<String?>(null)

    val uiState = combine(
        repository.observeExercises(),
        repository.observeMuscleGroups(),
        query,
        selectedMuscleId,
    ) { exercises, muscles, currentQuery, muscleId ->
        val filtered = exercises.filter { exercise ->
            val matchesQuery = currentQuery.isBlank() ||
                    exercise.name.contains(currentQuery, ignoreCase = true)
            val matchesMuscle = muscleId == null ||
                    exercise.primaryMuscleGroupId == muscleId
            matchesQuery && matchesMuscle
        }
        ExerciseLibraryUiState(
            query = currentQuery,
            selectedMuscleId = muscleId,
            muscleGroups = muscles,
            exercises = filtered,
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