package com.chy.muscletome.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.dao.ExerciseSetPoint
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveSlot(
    val result: SessionSlotResultEntity,
    val slot: RoutineSlotEntity?,
    val exercise: ExerciseEntity?,
    val sets: List<SetLogEntity>,
)

/** Time span options for the exercise progress chart. */
enum class ProgressSpan(val days: Int?) {
    LAST_30(30),
    LAST_60(60),
    LAST_90(90),
    ALL_TIME(null);

    val label: String
        get() = when (this) {
            LAST_30 -> "30d"
            LAST_60 -> "60d"
            LAST_90 -> "90d"
            ALL_TIME -> "All"
        }
}

/** One past session's worth of history for the on-screen exercise. */
data class ExerciseSessionSummary(
    val sessionStartEpochMs: Long,
    val setCount: Int,
    val topWeight: Double,
    val bestE1rm: Double,
    val volume: Double,
)

/** One point on the progress chart: best e1RM in a session. */
data class ProgressPoint(
    val sessionStartEpochMs: Long,
    val bestE1rm: Double,
)

/** wger reference info for the on-screen exercise, shown in the info sheet. */
data class ExerciseInfo(
    val exercise: ExerciseEntity? = null,
    val equipment: List<EquipmentEntity> = emptyList(),
    val secondaryMuscles: List<MuscleGroupEntity> = emptyList(),
    val primaryMuscleName: String = "",
)

data class ActiveWorkoutUiState(
    val slots: List<ActiveSlot> = emptyList(),
    val currentIndex: Int = 0,
    val weight: String = "0",
    val reps: String = "8",
    val rpe: String = "",
    val restSecondsLeft: Int = 0,
    val finished: Boolean = false,
    val catalogExercises: List<ExerciseEntity> = emptyList(),
    val swapQuery: String = "",
    val lastSessions: List<ExerciseSessionSummary> = emptyList(),
    val progressPoints: List<ProgressPoint> = emptyList(),
    val progressSpan: ProgressSpan = ProgressSpan.LAST_30,
    val note: String = "",
) {
    val current: ActiveSlot? get() = slots.getOrNull(currentIndex)
    val currentSetNumber: Int get() = (current?.sets?.size ?: 0) + 1
    val plannedSets: Int get() = current?.slot?.sets ?: 0
    val isCurrentComplete: Boolean get() =
        current == null || plannedSets == 0 || current!!.sets.size >= plannedSets
    val isLastExercise: Boolean get() =
        slots.isEmpty() || currentIndex == slots.lastIndex
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineDao: RoutineDao,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val slotsByDay = MutableStateFlow<List<RoutineSlotEntity>>(emptyList())
    private val currentIndex = MutableStateFlow(0)
    private val progressSpan = MutableStateFlow(ProgressSpan.LAST_30)
    private val weight = MutableStateFlow("0")
    private val reps = MutableStateFlow("8")
    private val rpe = MutableStateFlow("")
    private val restSecondsLeft = MutableStateFlow(0)
    private val finished = MutableStateFlow(false)
    private val swapQuery = MutableStateFlow("")
    private val note = MutableStateFlow("")
    private var restJob: Job? = null
    private var loadedDefaultsForResultId: String? = null

    // Which exercise the note draft belongs to; notes reload whenever the
    // on-screen exercise changes (reroll/swap), not just per slot result.
    private var noteLoadedForExerciseId: String? = null
    private var noteSaveJob: Job? = null

    init {
        viewModelScope.launch {
            workoutRepository.observeSession(sessionId).collect { session ->
                val dayId = session?.routineDayId ?: return@collect
                slotsByDay.value = routineDao.getSlots(dayId)
            }
        }
    }

    // ID of the exercise shown on screen; follows rerolls and swaps because those
    // rewrite resolvedExerciseId on the slot result, and the slot result flow
    // re-emits whenever that changes.
    private val currentExerciseId: Flow<String?> = combine(
        workoutRepository.observeSlotResults(sessionId),
        slotsByDay,
        currentIndex,
    ) { results, slots, index ->
        val order = slots.associateBy { it.id }
        results.sortedBy { r -> order[r.routineSlotId]?.orderIndex ?: Int.MAX_VALUE }
            .getOrNull(index)?.resolvedExerciseId
    }

    // All logged sets for the on-screen exercise, reactively.
    private val currentExerciseSets: Flow<List<ExerciseSetPoint>> =
        currentExerciseId.flatMapLatest { exerciseId ->
            if (exerciseId == null) flowOf(emptyList())
            else workoutRepository.observeExerciseSetPoints(exerciseId)
        }

    // wger reference info (description, muscles, equipment) for the on-screen
    // exercise; follows rerolls and swaps just like currentExerciseId above.
    val exerciseInfo: StateFlow<ExerciseInfo> = currentExerciseId
        .flatMapLatest { exerciseId ->
            if (exerciseId == null) {
                flowOf(ExerciseInfo())
            } else {
                combine(
                    catalogRepository.observeExercise(exerciseId),
                    catalogRepository.observeEquipmentForExercise(exerciseId),
                    catalogRepository.observeSecondaryMusclesForExercise(exerciseId),
                    catalogRepository.observeMuscleGroups(),
                ) { exercise, equipment, secondaryMuscles, muscleGroups ->
                    ExerciseInfo(
                        exercise = exercise,
                        equipment = equipment,
                        secondaryMuscles = secondaryMuscles,
                        primaryMuscleName = exercise?.primaryMuscleGroupId
                            ?.let { id -> muscleGroups.find { it.id == id }?.name }
                            .orEmpty(),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseInfo())

    val uiState = combine(
        workoutRepository.observeSlotResults(sessionId),
        workoutRepository.observeSets(sessionId),
        slotsByDay,
        catalogRepository.observeExercises(),
        currentIndex,
        weight,
        reps,
        rpe,
        restSecondsLeft,
        finished,
        swapQuery,
        currentExerciseSets,
        progressSpan,
        note,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val results = values[0] as List<SessionSlotResultEntity>
        @Suppress("UNCHECKED_CAST")
        val sets = values[1] as List<SetLogEntity>
        @Suppress("UNCHECKED_CAST")
        val routineSlots = values[2] as List<RoutineSlotEntity>
        @Suppress("UNCHECKED_CAST")
        val exercises = values[3] as List<ExerciseEntity>
        val index = values[4] as Int
        val weightText = values[5] as String
        val repsText = values[6] as String
        val rpeText = values[7] as String
        val rest = values[8] as Int
        val isFinished = values[9] as Boolean
        val swapFilter = values[10] as String
        @Suppress("UNCHECKED_CAST")
        val exerciseSetPoints = values[11] as List<ExerciseSetPoint>
        val span = values[12] as ProgressSpan
        val noteText = values[13] as String

        val exerciseMap = exercises.associateBy { it.id }
        val slotMap = routineSlots.associateBy { it.id }
        val setsByResult = sets.groupBy { it.sessionSlotResultId }
        val orderedResults = results.sortedBy { result ->
            slotMap[result.routineSlotId]?.orderIndex ?: Int.MAX_VALUE
        }
        ActiveWorkoutUiState(
            slots = orderedResults.map { result ->
                ActiveSlot(
                    result = result,
                    slot = slotMap[result.routineSlotId],
                    exercise = exerciseMap[result.resolvedExerciseId],
                    sets = (setsByResult[result.id] ?: emptyList()).sortedBy { it.setNumber },
                )
            },
            currentIndex = index,
            weight = weightText,
            reps = repsText,
            rpe = rpeText,
            restSecondsLeft = rest,
            finished = isFinished,
            catalogExercises = exercises.filter {
                it.name.contains(swapFilter, ignoreCase = true)
            },
            swapQuery = swapFilter,
            lastSessions = buildLastSessions(exerciseSetPoints, limit = 3),
            progressPoints = buildProgressPoints(exerciseSetPoints, span),
            progressSpan = span,
            note = noteText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    private fun buildLastSessions(
        points: List<ExerciseSetPoint>,
        limit: Int,
    ): List<ExerciseSessionSummary> {
        return points
            .filter { it.sessionId != sessionId }
            .groupBy { it.sessionId }
            .map { (_, sessionPoints) ->
                ExerciseSessionSummary(
                    sessionStartEpochMs = sessionPoints.first().sessionStartEpochMs,
                    setCount = sessionPoints.size,
                    topWeight = sessionPoints.maxOf { it.weight },
                    bestE1rm = sessionPoints.maxOf { p -> epley1Rm(p.weight, p.reps) },
                    volume = sessionPoints.sumOf { it.weight * it.reps },
                )
            }
            .sortedByDescending { it.sessionStartEpochMs }
            .take(limit)
    }

    private fun buildProgressPoints(
        points: List<ExerciseSetPoint>,
        span: ProgressSpan,
    ): List<ProgressPoint> {
        val cutoff = span.days?.let { days ->
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        }
        return points
            .filter { it.sessionId != sessionId && (cutoff == null || it.completedAtEpochMs >= cutoff) }
            .groupBy { it.sessionId }
            .map { (_, sessionPoints) ->
                ProgressPoint(
                    sessionStartEpochMs = sessionPoints.first().sessionStartEpochMs,
                    bestE1rm = sessionPoints.maxOf { p -> epley1Rm(p.weight, p.reps) },
                )
            }
            .sortedBy { it.sessionStartEpochMs }
    }

    private fun epley1Rm(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return weight
        return weight * (1.0 + reps / 30.0)
    }

    fun onProgressSpanChange(span: ProgressSpan) {
        progressSpan.value = span
    }

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val current = state.current ?: return@collect
                if (loadedDefaultsForResultId != current.result.id) {
                    loadedDefaultsForResultId = current.result.id
                    val last = workoutRepository.lastSetForExercise(current.result.resolvedExerciseId)
                    if (last != null) {
                        weight.value = last.weight.toString()
                        reps.value = last.reps.toString()
                    } else {
                        reps.value = current.slot?.repRangeMin?.toString() ?: "8"
                    }
                }
                // The note belongs to the exercise itself so it persists
                // between sessions; load it whenever the exercise changes.
                val exerciseId = current.result.resolvedExerciseId
                if (noteLoadedForExerciseId != exerciseId) {
                    noteLoadedForExerciseId = exerciseId
                    note.value = current.exercise?.notes.orEmpty()
                }
            }
        }
    }

    fun onWeightChange(value: String) { weight.value = value }
    fun onRepsChange(value: String) { reps.value = value }
    fun onRpeChange(value: String) { rpe.value = value }
    fun onSwapQueryChange(value: String) { swapQuery.value = value }

    fun onNoteChange(value: String) {
        note.value = value
        scheduleNoteSave()
    }

    // Notes autosave: debounce writes so keystrokes don't hammer Room, and
    // flush immediately whenever the user moves on from the exercise.
    private fun scheduleNoteSave() {
        val exerciseId = uiState.value.current?.result?.resolvedExerciseId ?: return
        noteSaveJob?.cancel()
        noteSaveJob = viewModelScope.launch {
            delay(600)
            catalogRepository.updateExerciseNotes(exerciseId, note.value)
        }
    }

    private fun flushNoteSave() {
        val pending = noteSaveJob ?: return
        val exerciseId = uiState.value.current?.result?.resolvedExerciseId ?: return
        pending.cancel()
        noteSaveJob = null
        viewModelScope.launch {
            catalogRepository.updateExerciseNotes(exerciseId, note.value)
        }
    }

    fun bumpWeight(delta: Double) {
        val current = weight.value.toDoubleOrNull() ?: 0.0
        weight.value = ((current + delta).coerceAtLeast(0.0)).toString()
    }

    fun bumpReps(delta: Int) {
        val current = reps.value.toIntOrNull() ?: 0
        reps.value = (current + delta).coerceAtLeast(0).toString()
    }

    fun logSet() {
        val state = uiState.value
        val current = state.current ?: return
        val weightValue = state.weight.toDoubleOrNull() ?: return
        val repsValue = state.reps.toIntOrNull() ?: return
        val rpeValue = state.rpe.toFloatOrNull()
        viewModelScope.launch {
            workoutRepository.logSet(
                sessionSlotResultId = current.result.id,
                setNumber = state.currentSetNumber,
                weight = weightValue,
                reps = repsValue,
                rpe = rpeValue,
                restSecondsActual = current.slot?.restSeconds,
            )
            startRest(current.slot?.restSeconds ?: 0)
        }
    }

    fun nextExercise() {
        val state = uiState.value
        if (state.isLastExercise) return
        flushNoteSave()
        restJob?.cancel()
        restSecondsLeft.value = 0
        currentIndex.update { it + 1 }
    }

    fun skipRest() {
        restJob?.cancel()
        restSecondsLeft.value = 0
    }

    fun finishWorkout() {
        flushNoteSave()
        viewModelScope.launch {
            workoutRepository.finishSession(sessionId)
            finished.value = true
        }
    }

    fun reasonLabel(): String {
        val current = uiState.value.current ?: return ""
        return when (current.result.selectionReason) {
            SelectionReason.FIXED -> "Pinned in your routine"
            SelectionReason.AI_ROTATED -> "Picked for this target slot"
            SelectionReason.USER_REROLL -> "Rerolled"
            SelectionReason.USER_OVERRIDE -> "You chose this"
        }
    }

    fun canReroll(): Boolean {
        val current = uiState.value.current ?: return false
        return current.slot?.type == SlotType.TARGET && current.sets.isEmpty()
    }

    fun reroll() {
        val state = uiState.value
        val current = state.current ?: return
        if (!canReroll()) return
        flushNoteSave()
        viewModelScope.launch {
            workoutRepository.rerollSlot(current.result, state.currentIndex)
        }
    }

    fun overrideWith(exerciseId: String) {
        val current = uiState.value.current ?: return
        if (current.sets.isNotEmpty()) return
        flushNoteSave()
        viewModelScope.launch {
            workoutRepository.overrideSlot(current.result, exerciseId)
        }
    }

    private fun startRest(seconds: Int) {
        restJob?.cancel()
        if (seconds <= 0) {
            restSecondsLeft.value = 0
            return
        }
        restJob = viewModelScope.launch {
            restSecondsLeft.value = seconds
            while (restSecondsLeft.value > 0) {
                delay(1_000)
                restSecondsLeft.update { it - 1 }
            }
        }
    }
}
