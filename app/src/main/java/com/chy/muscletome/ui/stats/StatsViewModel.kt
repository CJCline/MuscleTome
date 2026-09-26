package com.chy.muscletome.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.repository.StatsRepository
import com.chy.muscletome.data.repository.StatsSnapshot
import com.chy.muscletome.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    val uiState = statsRepository.observeStats().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsSnapshot(),
    )

    /** Weekly set goal for a muscle; null clears it. */
    fun setWeeklySetTarget(muscleGroupId: String, target: Int?) {
        viewModelScope.launch { userRepository.setWeeklySetTarget(muscleGroupId, target) }
    }
}