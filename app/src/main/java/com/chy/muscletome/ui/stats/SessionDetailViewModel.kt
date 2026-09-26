package com.chy.muscletome.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SessionExerciseLog(
    val exerciseName: String,
    val reason: String,
    val sets: List<SetLogEntity>,
    /** Non-null when this row is part of a superset/circuit group. */
    val supersetGroupId: String? = null,
)

data class SessionDetailUiState(
    val exercises: List<SessionExerciseLog> = emptyList(),
    /** Sets display in the user's unit (they're stored in it). */
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    catalogRepository: CatalogRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val uiState = combine(
        workoutRepository.observeSlotResults(sessionId),
        workoutRepository.observeSets(sessionId),
        catalogRepository.observeExercises(),
        userRepository.observeUser(),
    ) { results, sets, exercises, user ->
        val names = exercises.associate { it.id to it.name }
        val setsByResult = sets.groupBy { it.sessionSlotResultId }
        SessionDetailUiState(
            // sortOrder (the session's own snapshot) — not slot order: ad-hoc
            // rows have no slot, and routine edits must not rewrite history.
            exercises = results.sortedBy { it.sortOrder }.map { result ->
                SessionExerciseLog(
                    exerciseName = names[result.resolvedExerciseId] ?: result.resolvedExerciseId,
                    reason = result.selectionReason.name,
                    sets = setsByResult[result.id].orEmpty().sortedBy { it.setNumber },
                    supersetGroupId = result.supersetGroupId,
                )
            },
            weightUnit = user?.weightUnit ?: WeightUnit.KG,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())
}
