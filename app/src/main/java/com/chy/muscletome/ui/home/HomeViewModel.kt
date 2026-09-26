package com.chy.muscletome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.StartResult
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.data.timer.RestTimerManager
import com.chy.muscletome.domain.template.RoutineTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One entry in the "Up next" exercise preview list. */
data class PreviewExercise(
    val name: String,
    /** "AI pick" badge text for TARGET slots; null for fixed exercises. */
    val targetLabel: String? = null,
)

data class HomeUiState(
    val openSession: WorkoutSessionEntity? = null,
    val nextDay: RoutineDayEntity? = null,
    val nextRoutineName: String? = null,
    /** First few entries of the next day, as preview text. */
    val preview: List<PreviewExercise> = emptyList(),
    /** How many preview entries were collapsed into "+N more". */
    val previewMore: Int = 0,
    val lastCompleted: WorkoutSessionEntity? = null,
    /** Total routines the user owns (gates the starter-programs banner). */
    val routineCount: Int = 0,
    /** Finished sessions since the start of the ISO week (consistency). */
    val weekSessionCount: Int = 0,
    /** True when the last completed session happened this ISO week. */
    val lastCompletedThisWeek: Boolean = false,
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

    /** Emits when a starter program is added (no longer shown on Home). */
    val routineAdded: SharedFlow<String> get() = _routineAdded
    private val _routineAdded = MutableSharedFlow<String>()

    /** Guard so a double-tap can't add the same template twice. */
    private val applyingTemplate = MutableStateFlow(false)

    // True while a fresh workout start is in flight. The open session is inserted
    // into the DB before navigation completes, which would otherwise make the
    // home screen briefly flash the "Workout in progress" card (Resume/Discard)
    // on the way to the workout screen. HomeScreen clears the flag once it has
    // fully left composition (i.e. after the navigation transition finishes).
    private val _startingWorkout = MutableStateFlow(false)

    private val refreshTick = MutableStateFlow(0)

    /** Local zone week window, recomputed only when the day flips. */
    private var weekWindow: Pair<Long, Long> = computeWeekWindow()

    val uiState = combine(
        // Open + last-completed session flow together (keeps the outer
        // combine within its typed overload).
        combine(
            workoutRepository.observeOpenSession(),
            workoutRepository.observeLastCompletedSession(),
        ) { open, last -> open to last },
        // Days + the active-routine pointer flow together as one source.
        combine(
            routineRepository.observeAllDays(),
            userRepository.observeUser(),
        ) { days, user -> days to user?.activeRoutineId },
        _startingWorkout,
        // Re-emits when the user returns to Home, so the preview and the
        // weekly count pick up changes made elsewhere (routines, workouts).
        refreshTick,
        routineRepository.observeRoutines(),
    ) { openLast, daysAndActive, starting, _, routines ->
        val (open, lastCompleted) = openLast
        val (days, activeRoutineId) = daysAndActive
        // The explicit choice wins; a stale pointer (deleted routine) or no
        // choice yet falls back to the newest routine — "Up next" is always
        // scoped to one routine, never a global interleave of programs.
        val effectiveActiveId = activeRoutineId
            ?.takeIf { id -> routines.any { it.id == id } }
            ?: routines.firstOrNull()?.id
        val next = nextDay(effectiveActiveId, lastCompleted, days)
        val preview = next?.let { day ->
            workoutRepository.dayPreview(day.id).let { (shown, more) ->
                shown.map {
                    PreviewExercise(name = it.name, targetLabel = it.targetLabel)
                } to more
            }
        }
        val window = currentWeekWindow()
        val weekSessions = workoutRepository
            .getSessionsSince(SeedCatalog.LOCAL_USER_ID, window.first)
        HomeUiState(
            openSession = open,
            nextDay = next,
            nextRoutineName = routines.find { it.id == next?.routineId }?.name,
            preview = preview?.first.orEmpty(),
            previewMore = preview?.second ?: 0,
            lastCompleted = lastCompleted,
            routineCount = routines.size,
            weekSessionCount = weekSessions.size,
            lastCompletedThisWeek = lastCompleted != null &&
                ((lastCompleted.startedAtEpochMs >= window.first)),
            startingWorkout = starting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Called when Home becomes visible again; refreshes day-scoped data. */
    fun refresh() {
        refreshTick.value += 1
    }

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

    /**
     * Adds a starter program and makes it the active program — you just
     * picked it, so it becomes "Up next" immediately.
     */
    fun addRoutineFromTemplate(template: RoutineTemplate) {
        if (applyingTemplate.value) return
        viewModelScope.launch {
            applyingTemplate.value = true
            try {
                val routineId = routineRepository.applyTemplate(template)
                userRepository.setActiveRoutine(routineId)
                _routineAdded.emit(template.name)
                refresh()
            } finally {
                applyingTemplate.value = false
            }
        }
    }

    fun clearStartingWorkout() {
        _startingWorkout.value = false
    }


    private fun nextDay(
        activeRoutineId: String?,
        lastCompleted: WorkoutSessionEntity?,
        days: List<RoutineDayEntity>,
    ): RoutineDayEntity? = NextDayPlanner.nextDay(
        activeRoutineId = activeRoutineId,
        lastCompletedDayId = lastCompleted?.routineDayId,
        days = days,
    )

    private fun currentWeekWindow(): Pair<Long, Long> {
        val (start, end) = weekWindow
        val now = System.currentTimeMillis()
        return if (now >= end) {
            weekWindow = computeWeekWindow()
            weekWindow
        } else {
            start to end
        }
    }

    /** Start-of-ISO-week (Monday 00:00 local) → start of next week. */
    private fun computeWeekWindow(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val monday = today.with(DayOfWeek.MONDAY)
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plus(1, ChronoUnit.WEEKS).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun noMatchMessage(slotLabel: String): String =
        "No matching exercise for \"$slotLabel\" with your equipment. " +
            "Add equipment in Settings or change the slot."
}
