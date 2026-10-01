package com.chy.muscletome.ui.routine

import com.chy.muscletome.data.repository.WorkoutRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.StartResult
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.routine.TargetSlotLabel
import com.chy.muscletome.domain.session.EffortScales
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
enum class SlotField { SETS, REP_MIN, REP_MAX, REST, TARGET_EFFORT }

/**
 * In-progress edits for a slot's metrics, not yet persisted.
 * [targetEffort] holds the user's scale value (RPE or RIR per the user
 * preference); it converts to RPE at save time.
 */
data class SlotDraft(
    val sets: String,
    val repMin: String,
    val repMax: String,
    val restSeconds: String,
    val targetEffort: String = "",
)

data class SlotRow(
    val slot: RoutineSlotEntity,
    val exerciseName: String,
    /**
     * Human label for TARGET slots ("Chest isolation · AI pick"); the
     * exerciseName for fixed slots is used as the title verbatim.
     */
    val targetLabel: String? = null,
    val draftSets: String,
    val draftRepMin: String,
    val draftRepMax: String,
    val draftRestSeconds: String,
    /** Draft text for target effort, expressed on the user's scale. */
    val draftTargetEffort: String,
    val isDraftValid: Boolean,
    val hasUnsavedChanges: Boolean,
)

data class DayDetailUiState(
    val day: RoutineDayEntity? = null,
    val slots: List<SlotRow> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val canSave: Boolean = false,
    val savedTick: Int = 0,
    /** Which scale the target-effort field speaks (user preference). */
    val effortScale: EffortScale = EffortScale.RPE,
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    catalogRepository: CatalogRepository,
    userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val dayId: String = checkNotNull(savedStateHandle["dayId"])

    private val drafts = MutableStateFlow<Map<String, SlotDraft>>(emptyMap())
    private val _savedTick = MutableStateFlow(0)

    val uiState = combine(
        routineRepository.observeDay(dayId),
        routineRepository.observeSlots(dayId),
        catalogRepository.observeExercises(),
        routineRepository.observeAllSlotTargets(),
        userRepository.observeUser(),
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
        val slotTargets = values[3] as List<SlotTargetMuscleCrossRef>
        val user = values[4] as UserEntity?
        @Suppress("UNCHECKED_CAST")
        val currentDrafts = values[5] as Map<String, SlotDraft>
        val tick = values[6] as Int
        val effortScalePref = user?.effortScale ?: EffortScale.RPE
        val names = exercises.associate { it.id to it.name }
        val muscleNames = routineRepository.getMuscleGroupNames()
        val targetsBySlot = slotTargets.groupBy { it.slotId }
        val mappedSlots = slots.map { slot ->
            val draft = currentDrafts[slot.id]
            val setsText = draft?.sets ?: slot.sets.toString()
            val minText = draft?.repMin ?: slot.repRangeMin.toString()
            val maxText = draft?.repMax ?: slot.repRangeMax.toString()
            val restText = draft?.restSeconds ?: slot.restSeconds.toString()
            val effortText = draft?.targetEffort
                ?: slot.targetRpe?.let { EffortScales.toScaleText(it, effortScalePref) }.orEmpty()
            val valid = setsText.toIntOrNull() != null &&
                minText.toIntOrNull() != null &&
                maxText.toIntOrNull() != null &&
                restText.toIntOrNull() != null
            val slotEffortText = slot.targetRpe?.let { EffortScales.toScaleText(it, effortScalePref) }.orEmpty()
            val dirty = draft != null && (
                setsText != slot.sets.toString() ||
                minText != slot.repRangeMin.toString() ||
                maxText != slot.repRangeMax.toString() ||
                restText != slot.restSeconds.toString() ||
                effortText != slotEffortText
            )
            val targetLabel = if (slot.type == SlotType.TARGET) {
                TargetSlotLabel.label(
                    muscleNames = (targetsBySlot[slot.id] ?: emptyList())
                        .mapNotNull { muscleNames[it.muscleGroupId] },
                    movement = slot.targetMovementType,
                )
            } else {
                null
            }
            SlotRow(
                slot = slot,
                exerciseName = slot.exerciseId?.let { names[it] } ?: "Choose exercise",
                targetLabel = targetLabel,
                draftSets = setsText,
                draftRepMin = minText,
                draftRepMax = maxText,
                draftRestSeconds = restText,
                draftTargetEffort = effortText,
                isDraftValid = valid,
                hasUnsavedChanges = dirty,
            )
        }
        val anyDirty = mappedSlots.any { it.hasUnsavedChanges }
        val allDirtyValid = mappedSlots.all { !it.hasUnsavedChanges || it.isDraftValid }
        DayDetailUiState(
            day = day,
            slots = mappedSlots,
            hasUnsavedChanges = anyDirty,
            canSave = anyDirty && allDirtyValid,
            savedTick = tick,
            effortScale = effortScalePref,
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

    /** Groups the slot with the next one in day order (extends a chain when
     * invoked on a group's last slot). */
    fun groupSlotWithNext(slotId: String) {
        viewModelScope.launch { routineRepository.groupSlotWithNext(dayId, slotId) }
    }

    /** Pulls a slot out of its superset group (dissolving a stranded one). */
    fun ungroupSlot(slotId: String) {
        viewModelScope.launch { routineRepository.ungroupSlot(dayId, slotId) }
    }

    fun onSlotFieldChange(slotId: String, field: SlotField, value: String) {
        val existing = drafts.value[slotId] ?: run {
            val row = uiState.value.slots.firstOrNull { it.slot.id == slotId } ?: return
            SlotDraft(
                sets = row.slot.sets.toString(),
                repMin = row.slot.repRangeMin.toString(),
                repMax = row.slot.repRangeMax.toString(),
                restSeconds = row.slot.restSeconds.toString(),
                targetEffort = row.draftTargetEffort,
            )
        }
        val updated = when (field) {
            SlotField.SETS -> existing.copy(sets = value)
            SlotField.REP_MIN -> existing.copy(repMin = value)
            SlotField.REP_MAX -> existing.copy(repMax = value)
            SlotField.REST -> existing.copy(restSeconds = value)
            SlotField.TARGET_EFFORT -> existing.copy(targetEffort = value)
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
                        // Draft speaks the user's scale; convert to RPE canon.
                        targetRpe = EffortScales.fromScaleText(d.targetEffort, state.effortScale),
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
