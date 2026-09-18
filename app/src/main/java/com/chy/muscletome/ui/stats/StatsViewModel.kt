package com.chy.muscletome.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.repository.StatsRepository
import com.chy.muscletome.data.repository.StatsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
) : ViewModel() {
    val uiState = statsRepository.observeStats().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsSnapshot(),
    )
}