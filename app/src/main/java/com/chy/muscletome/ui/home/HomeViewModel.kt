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
import kotlinx.coroutines.flow.MutableStateFlow
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
    val startingWorkout: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    routineRepository: RoutineRepository,
) : ViewModel() {

    private val _startSessionId = MutableSharedFlow<String>()
    val startSessionId: SharedFlow<String> = _startSessionId.asSharedFlow()

    // True while a fresh workout start is in flight. The open session is inserted
    // into the DB before navigation completes, which would otherwise make the
    // home screen briefly flash the "Workout in progress" card (Resume/Discard)
    // on the way to the workout screen. HomeScreen clears the flag once it has
    // fully left composition (i.e. after the navigation transition finishes).
    private val _startingWorkout = MutableStateFlow(false)

    val uiState = combine(
        workoutRepository.observeOpenSession(),
        workoutRepository.observeLastCompletedSession(),
        routineRepository.observeRoutines(),
        routineRepository.observeAllDays(),
        _startingWorkout,
    ) { open, lastCompleted, routines, days, starting ->
        val next = nextDay(lastCompleted, days)
        HomeUiState(
            openSession = open,
            nextDay = next,
            nextRoutineName = routines.find { it.id == next?.routineId }?.name,
            lastCompleted = lastCompleted,
            routines = routines,
            startingWorkout = starting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun resumeOpenSession() {
        val sessionId = uiState.value.openSession?.id ?: return
        viewModelScope.launch { _startSessionId.emit(sessionId) }
    }

    fun discardOpenSession() {
        val sessionId = uiState.value.openSession?.id ?: return
        viewModelScope.launch {
            workoutRepository.discardSession(sessionId)
        }
    }

    fun startNextDay() {
        val dayId = uiState.value.nextDay?.id ?: return
        if (_startingWorkout.value) return
        viewModelScope.launch {
            _startingWorkout.value = true
            var navigated = false
            try {
                when (val result = workoutRepository.startSession(dayId)) {
                    is StartResult.Success -> {
                        _startSessionId.emit(result.sessionId)
                        navigated = true
                    }
                    is StartResult.NoMatch -> { /* ignore or add an error message later */ }
                }
            } finally {
                // On success the flag stays set: the navigation transition is still
                // running and the open-session card would flash while fading out.
                // HomeScreen clears it via clearStartingWorkout() once it is gone.
                if (!navigated) _startingWorkout.value = false
            }
        }
    }

    fun clearStartingWorkout() {
        _startingWorkout.value = false
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
