package com.chy.muscletome.ui.routine

import com.chy.muscletome.data.repository.WorkoutRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.StartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Fields on a slot that can be edited inline on the day screen. */
enum class SlotField { SETS, REP_MIN, REP_MAX, REST }

/** In-progress edits for a slot's sets/reps/rest, not yet persisted. */
data class SlotDraft(
    val sets: String,
    val repMin: String,
    val repMax: String,
    val restSeconds: String,
)

data class SlotRow(
    val slot: RoutineSlotEntity,
    val exerciseName: String,
    val draftSets: String,
    val draftRepMin: String,
    val draftRepMax: String,
    val draftRestSeconds: String,
    val isDraftValid: Boolean,
    val hasUnsavedChanges: Boolean,
)

data class DayDetailUiState(
    val day: RoutineDayEntity? = null,
    val slots: List<SlotRow> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val canSave: Boolean = false,
    val savedTick: Int = 0,
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    catalogRepository: CatalogRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val dayId: String = checkNotNull(savedStateHandle["dayId"])

    private val drafts = MutableStateFlow<Map<String, SlotDraft>>(emptyMap())
    private val _savedTick = MutableStateFlow(0)

    val uiState = combine(
        routineRepository.observeDay(dayId),
        routineRepository.observeSlots(dayId),
        catalogRepository.observeExercises(),
        drafts,
        _savedTick,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val day = values[0] as RoutineDayEntity?
        @Suppress("UNCHECKED_CAST")
        val slots = values[1] as List<RoutineSlotEntity>
        @Suppress("UNCHECKED_CAST")
        val exercises = values[2] as List<ExerciseEntity>
        @Suppress("UNCHECKED_CAST")
        val currentDrafts = values[3] as Map<String, SlotDraft>
        val tick = values[4] as Int
        val names = exercises.associate { it.id to it.name }
        DayDetailUiState(
            day = day,
            slots = slots.map { slot ->
                val draft = currentDrafts[slot.id]
                val setsText = draft?.sets ?: slot.sets.toString()
                val minText = draft?.repMin ?: slot.repRangeMin.toString()
                val maxText = draft?.repMax ?: slot.repRangeMax.toString()
                val restText = draft?.restSeconds ?: slot.restSeconds.toString()
                val valid = setsText.toIntOrNull() != null &&
                    minText.toIntOrNull() != null &&
                    maxText.toIntOrNull() != null &&
                    restText.toIntOrNull() != null
                val dirty = draft != null && setOf(
                    setsText,
                    minText,
                    maxText,
                    restText,
                ) != setOf(
                    slot.sets.toString(),
                    slot.repRangeMin.toString(),
                    slot.repRangeMax.toString(),
                    slot.restSeconds.toString(),
                )
                SlotRow(
                    slot = slot,
                    exerciseName = slot.exerciseId?.let { names[it] } ?: "Choose exercise",
                    draftSets = setsText,
                    draftRepMin = minText,
                    draftRepMax = maxText,
                    draftRestSeconds = restText,
                    isDraftValid = valid,
                    hasUnsavedChanges = dirty,
                )
            },
            hasUnsavedChanges = currentDrafts.isNotEmpty(),
            canSave = currentDrafts.isNotEmpty() && slots.all { s ->
                currentDrafts[s.id]?.let { d ->
                    d.sets.toIntOrNull() != null && d.repMin.toIntOrNull() != null &&
                        d.repMax.toIntOrNull() != null && d.restSeconds.toIntOrNull() != null
                } ?: true
            },
            savedTick = tick,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayDetailUiState(),
    )

    fun deleteSlot(id: String) {
        viewModelScope.launch {
            routineRepository.deleteSlot(id)
            drafts.value = drafts.value - id
        }
    }

    /** Persists a drag-reorder; called when the drag gesture ends. */
    fun reorderSlots(orderedIds: List<String>) {
        viewModelScope.launch { routineRepository.reorderSlots(dayId, orderedIds) }
    }

    fun onSlotFieldChange(slotId: String, field: SlotField, value: String) {
        val existing = drafts.value[slotId] ?: run {
            val slot = uiState.value.slots.firstOrNull { it.slot.id == slotId }?.slot ?: return
            SlotDraft(
                sets = slot.sets.toString(),
                repMin = slot.repRangeMin.toString(),
                repMax = slot.repRangeMax.toString(),
                restSeconds = slot.restSeconds.toString(),
            )
        }
        val updated = when (field) {
            SlotField.SETS -> existing.copy(sets = value)
            SlotField.REP_MIN -> existing.copy(repMin = value)
            SlotField.REP_MAX -> existing.copy(repMax = value)
            SlotField.REST -> existing.copy(restSeconds = value)
        }
        drafts.value = drafts.value + (slotId to updated)
    }

    fun discardChanges(slotId: String) {
        drafts.value = drafts.value - slotId
    }

    fun saveChanges() {
        val state = uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            // NonCancellable so the DB write survives the user navigating back
            // mid-save (BackConfirm dialog launches save then pops).
            withContext(NonCancellable) {
                state.slots.filter { it.hasUnsavedChanges }.forEach { row ->
                    val d = drafts.value[row.slot.id] ?: return@forEach
                    routineRepository.updateSlot(
                        id = row.slot.id,
                        sets = d.sets.toIntOrNull() ?: return@forEach,
                        repMin = d.repMin.toIntOrNull() ?: return@forEach,
                        repMax = d.repMax.toIntOrNull() ?: return@forEach,
                        restSeconds = d.restSeconds.toIntOrNull() ?: return@forEach,
                    )
                }
                drafts.value = emptyMap()
                _savedTick.value += 1
            }
        }
    }

    private val _startSessionId = MutableSharedFlow<String>()
    val startSessionId: SharedFlow<String> = _startSessionId.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    fun startWorkout() {
        viewModelScope.launch {
            when (val result = workoutRepository.startSession(dayId)) {
                is StartResult.Success -> {
                    _startSessionId.emit(result.sessionId)
                }
                is StartResult.NoMatch -> {
                    _errorMessage.emit("No matching exercise for \"${result.slotLabel}\" with your equipment. Add equipment in Settings or change the slot.")
                }
            }
        }
    }
}
