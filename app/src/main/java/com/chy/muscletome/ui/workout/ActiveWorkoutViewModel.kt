package com.chy.muscletome.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveSlot(
    val result: SessionSlotResultEntity,
    val slot: RoutineSlotEntity?,
    val exercise: ExerciseEntity?,
    val sets: List<SetLogEntity>,
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
) {
    val current: ActiveSlot? get() = slots.getOrNull(currentIndex)
    val currentSetNumber: Int get() = (current?.sets?.size ?: 0) + 1
    val plannedSets: Int get() = current?.slot?.sets ?: 0
    val isCurrentComplete: Boolean get() =
        current == null || plannedSets == 0 || current!!.sets.size >= plannedSets
    val isLastExercise: Boolean get() =
        slots.isEmpty() || currentIndex == slots.lastIndex
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineDao: RoutineDao,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val slotsByDay = MutableStateFlow<List<RoutineSlotEntity>>(emptyList())
    private val currentIndex = MutableStateFlow(0)
    private val weight = MutableStateFlow("0")
    private val reps = MutableStateFlow("8")
    private val rpe = MutableStateFlow("")
    private val restSecondsLeft = MutableStateFlow(0)
    private val finished = MutableStateFlow(false)
    private val swapQuery = MutableStateFlow("")
    private var restJob: Job? = null
    private var loadedDefaultsForResultId: String? = null

    init {
        viewModelScope.launch {
            workoutRepository.observeSession(sessionId).collect { session ->
                val dayId = session?.routineDayId ?: return@collect
                slotsByDay.value = routineDao.getSlots(dayId)
            }
        }
    }

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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val current = state.current ?: return@collect
                if (loadedDefaultsForResultId == current.result.id) return@collect
                loadedDefaultsForResultId = current.result.id
                val last = workoutRepository.lastSetForExercise(current.result.resolvedExerciseId)
                if (last != null) {
                    weight.value = last.weight.toString()
                    reps.value = last.reps.toString()
                } else {
                    reps.value = current.slot?.repRangeMin?.toString() ?: "8"
                }
            }
        }
    }

    fun onWeightChange(value: String) { weight.value = value }
    fun onRepsChange(value: String) { reps.value = value }
    fun onRpeChange(value: String) { rpe.value = value }
    fun onSwapQueryChange(value: String) { swapQuery.value = value }

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
        restJob?.cancel()
        restSecondsLeft.value = 0
        currentIndex.update { it + 1 }
    }

    fun skipRest() {
        restJob?.cancel()
        restSecondsLeft.value = 0
    }

    fun finishWorkout() {
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
        viewModelScope.launch {
            workoutRepository.rerollSlot(current.result, state.currentIndex)
        }
    }

    fun overrideWith(exerciseId: String) {
        val current = uiState.value.current ?: return
        if (current.sets.isNotEmpty()) return
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
