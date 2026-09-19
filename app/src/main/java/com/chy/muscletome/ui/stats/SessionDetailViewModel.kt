package com.chy.muscletome.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SessionExerciseLog(
    val exerciseName: String,
    val reason: String,
    val sets: List<SetLogEntity>,
)

data class SessionDetailUiState(
    val exercises: List<SessionExerciseLog> = emptyList(),
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val uiState = combine(
        workoutRepository.observeSlotResults(sessionId),
        workoutRepository.observeSets(sessionId),
        catalogRepository.observeExercises(),
    ) { results, sets, exercises ->
        val names = exercises.associate { it.id to it.name }
        val setsByResult = sets.groupBy { it.sessionSlotResultId }
        SessionDetailUiState(
            exercises = results.map { result ->
                SessionExerciseLog(
                    exerciseName = names[result.resolvedExerciseId] ?: result.resolvedExerciseId,
                    reason = result.selectionReason.name,
                    sets = setsByResult[result.id].orEmpty().sortedBy { it.setNumber },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())
}
