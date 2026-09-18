package com.chy.regimen.ui.routine

import com.chy.regimen.data.repository.WorkoutRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.regimen.data.local.entity.ExerciseEntity
import com.chy.regimen.data.local.entity.RoutineDayEntity
import com.chy.regimen.data.local.entity.RoutineSlotEntity
import com.chy.regimen.data.repository.CatalogRepository
import com.chy.regimen.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class SlotRow(
    val slot: RoutineSlotEntity,
    val exerciseName: String,
)

data class DayDetailUiState(
    val day: RoutineDayEntity? = null,
    val slots: List<SlotRow> = emptyList(),
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    catalogRepository: CatalogRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val dayId: String = checkNotNull(savedStateHandle["dayId"])

    val uiState = combine(
        routineRepository.observeDay(dayId),
        routineRepository.observeSlots(dayId),
        catalogRepository.observeExercises(),
    ) { day, slots, exercises ->
        val names = exercises.associate { it.id to it.name }
        DayDetailUiState(
            day = day,
            slots = slots.map { slot ->
                SlotRow(
                    slot = slot,
                    exerciseName = slot.exerciseId?.let { names[it] } ?: "Choose exercise",
                )
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayDetailUiState(),
    )

    fun deleteSlot(id: String) {
        viewModelScope.launch { routineRepository.deleteSlot(id) }
    }

    private val _startSessionId = MutableSharedFlow<String>()
    val startSessionId: SharedFlow<String> = _startSessionId.asSharedFlow()

    fun startWorkout() {
        viewModelScope.launch {
            val sessionId = workoutRepository.startSession(dayId)
            _startSessionId.emit(sessionId)
        }
    }
}