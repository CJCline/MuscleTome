package com.chy.muscletome.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RoutineRepository,
) : ViewModel() {

    val routineId: String = checkNotNull(savedStateHandle["routineId"])

    val routine: StateFlow<RoutineEntity?> = repository.observeRoutine(routineId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val days: StateFlow<List<RoutineDayEntity>> = repository.observeDays(routineId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun addDay(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addDay(routineId, name) }
    }

    fun deleteDay(id: String) {
        viewModelScope.launch { repository.deleteDay(id) }
    }
}
