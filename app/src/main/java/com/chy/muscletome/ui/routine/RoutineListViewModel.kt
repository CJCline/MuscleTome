package com.chy.muscletome.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.domain.template.RoutineTemplate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** Routines plus the currently active one, for the row's Active badge. */
    val routines = combine(
        repository.observeRoutines(),
        userRepository.observeUser(),
    ) { routines, user -> routines to user?.activeRoutineId }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
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

    /** Pins Home's "Up next" (and the day preview) to this program. */
    fun setActiveRoutine(id: String) {
        viewModelScope.launch { userRepository.setActiveRoutine(id) }
    }

    /** Emits the template name after a starter program is added. */
    private val _routineAdded = MutableSharedFlow<String>()
    val routineAdded: SharedFlow<String> = _routineAdded.asSharedFlow()

    /** Guard so a double-tap can't add the same template twice. */
    private val applyingTemplate = MutableStateFlow(false)

    /**
     * Adds a starter program and makes it the active program — you just
     * picked it, so it becomes "Up next" immediately.
     */
    fun addRoutineFromTemplate(template: RoutineTemplate) {
        if (applyingTemplate.value) return
        viewModelScope.launch {
            applyingTemplate.value = true
            try {
                val routineId = repository.applyTemplate(template)
                userRepository.setActiveRoutine(routineId)
                _routineAdded.emit(template.name)
            } finally {
                applyingTemplate.value = false
            }
        }
    }
}
