package com.chy.muscletome.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import com.chy.muscletome.data.repository.ExerciseImportReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

@HiltViewModel
class ExerciseImportReviewViewModel @Inject constructor(
    private val repository: ExerciseImportReviewRepository,
) : ViewModel() {
    val pending = repository.observePending().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList<PendingExerciseImportEntity>(),
    )

    fun keepBoth(id: String) { viewModelScope.launch { repository.keepBoth(id) } }
    fun merge(id: String, exerciseId: String) {
        viewModelScope.launch { repository.mergeIntoExisting(id, exerciseId) }
    }
    fun discard(id: String) { viewModelScope.launch { repository.discardIncoming(id) } }
}
