package com.chy.muscletome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.data.repository.StartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val openSession: WorkoutSessionEntity? = null,
    val nextDay: RoutineDayEntity? = null,
    val nextRoutineName: String? = null,
    val lastCompleted: WorkoutSessionEntity? = null,
    val routines: List<RoutineEntity> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    routineRepository: RoutineRepository,
) : ViewModel() {

    private val _startSessionId = MutableSharedFlow<String>()
    val startSessionId: SharedFlow<String> = _startSessionId.asSharedFlow()

    val uiState = combine(
        workoutRepository.observeOpenSession(),
        workoutRepository.observeLastCompletedSession(),
        routineRepository.observeRoutines(),
        routineRepository.observeAllDays(),
    ) { open, lastCompleted, routines, days ->
        val next = nextDay(lastCompleted, days)
        HomeUiState(
            openSession = open,
            nextDay = next,
            nextRoutineName = routines.find { it.id == next?.routineId }?.name,
            lastCompleted = lastCompleted,
            routines = routines,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun resumeOpenSession() {
        val sessionId = uiState.value.openSession?.id ?: return
        viewModelScope.launch { _startSessionId.emit(sessionId) }
    }

    fun startNextDay() {
        val dayId = uiState.value.nextDay?.id ?: return
        viewModelScope.launch {
            when (val result = workoutRepository.startSession(dayId)) {
                is StartResult.Success -> _startSessionId.emit(result.sessionId)
                is StartResult.NoMatch -> { /* ignore or add an error message later */ }
            }
        }
    }

    private fun nextDay(
        lastCompleted: WorkoutSessionEntity?,
        days: List<RoutineDayEntity>,
    ): RoutineDayEntity? {
        if (days.isEmpty()) return null
        val ordered = days.sortedWith(compareBy({ it.routineId }, { it.orderIndex }))
        val lastDayId = lastCompleted?.routineDayId
        if (lastDayId == null) return ordered.first()
        val index = ordered.indexOfFirst { it.id == lastDayId }
        if (index < 0) return ordered.first()
        return ordered[(index + 1) % ordered.size]
    }
}
