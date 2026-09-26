package com.chy.muscletome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.StartResult
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.data.timer.RestTimerManager
import com.chy.muscletome.domain.template.RoutineTemplate
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
    val activeRoutineId: String? = null,
    val startingWorkout: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository,
    private val routineRepository: RoutineRepository,
    private val restTimerManager: RestTimerManager,
) : ViewModel() {

    private val _startSessionId = MutableSharedFlow<String>()
    val startSessionId: SharedFlow<String> = _startSessionId.asSharedFlow()

    /** Emits when starting a workout failed (e.g. no exercise matches a slot). */
    private val _startError = MutableSharedFlow<String>()
    val startError: SharedFlow<String> = _startError.asSharedFlow()

    /** Emits the template name after a starter program is added from Home. */
    private val _routineAdded = MutableSharedFlow<String>()
    val routineAdded: SharedFlow<String> = _routineAdded.asSharedFlow()

    /** Guard so a double-tap can't add the same template twice. */
    private val applyingTemplate = MutableStateFlow(false)

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
        // Days + the active-routine pointer flow together as one source so
        // the outer combine stays within its typed overload.
        combine(
            routineRepository.observeAllDays(),
            userRepository.observeUser(),
        ) { days, user -> days to user?.activeRoutineId },
        _startingWorkout,
    ) { open, lastCompleted, routines, daysAndActive, starting ->
        val (days, activeRoutineId) = daysAndActive
        // The explicit choice wins; a stale pointer (deleted routine) or no
        // choice yet falls back to the newest routine — "Up next" is always
        // scoped to one routine, never a global interleave of programs.
        val effectiveActive = activeRoutineId
            ?.takeIf { id -> routines.any { it.id == id } }
            ?: routines.firstOrNull()?.id
        val next = nextDay(effectiveActive, lastCompleted, days)
        HomeUiState(
            openSession = open,
            nextDay = next,
            nextRoutineName = routines.find { it.id == next?.routineId }?.name,
            lastCompleted = lastCompleted,
            routines = routines,
            activeRoutineId = effectiveActive,
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
            // A discarded session must not leave a stray rest notification
            // behind or fire a beep later.
            restTimerManager.cancel()
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
                    is StartResult.NoMatch -> _startError.emit(noMatchMessage(result.slotLabel))
                }
            } finally {
                // On success the flag stays set: the navigation transition is still
                // running and the open-session card would flash while fading out.
                // HomeScreen clears it via clearStartingWorkout() once it is gone.
                if (!navigated) _startingWorkout.value = false
            }
        }
    }

    /** Explicit "switch program" control — pins Home's "Up next" to a routine. */
    fun setActiveRoutine(routineId: String) {
        if (uiState.value.activeRoutineId == routineId) return
        viewModelScope.launch { userRepository.setActiveRoutine(routineId) }
    }

    /**
     * Adds a starter program from the templates banner and makes it the
     * active program — you just picked it, so it should show as "Up next".
     */
    fun addRoutineFromTemplate(template: RoutineTemplate) {
        if (applyingTemplate.value) return
        viewModelScope.launch {
            applyingTemplate.value = true
            try {
                val routineId = routineRepository.applyTemplate(template)
                userRepository.setActiveRoutine(routineId)
                _routineAdded.emit(template.name)
            } finally {
                applyingTemplate.value = false
            }
        }
    }

    fun clearStartingWorkout() {
        _startingWorkout.value = false
    }

    private fun noMatchMessage(slotLabel: String): String =
        "No matching exercise for \"$slotLabel\" with your equipment. " +
            "Add equipment in Settings or change the slot."

    /**
     * Next-up day of the [activeRoutineId] program only, advancing from the
     * last completed day with wrap-around. Sessions finished in *other*
     * routines never advance this pointer — programs don't interleave.
     * Pure logic lives in [NextDayPlanner] (unit-tested there).
     */
    private fun nextDay(
        activeRoutineId: String?,
        lastCompleted: WorkoutSessionEntity?,
        days: List<RoutineDayEntity>,
    ): RoutineDayEntity? = NextDayPlanner.nextDay(
        activeRoutineId = activeRoutineId,
        lastCompletedDayId = lastCompleted?.routineDayId,
        days = days,
    )
}
