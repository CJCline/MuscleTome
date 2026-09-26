package com.chy.muscletome.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val repository: RoutineRepository,
) : ViewModel() {

    val routines = repository.observeRoutines().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun createRoutine(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createRoutine(name) }
    }

    fun deleteRoutine(id: String) {
        viewModelScope.launch { repository.deleteRoutine(id) }
    }

    /** Copies the routine (days, slots, targets) under a "(copy)" name. */
    fun duplicateRoutine(id: String) {
        viewModelScope.launch { repository.duplicateRoutine(id) }
    }
}
