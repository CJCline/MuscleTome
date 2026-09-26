package com.chy.muscletome.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.onboarding.OnboardingManager
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val unit: WeightUnit = WeightUnit.KG,
    val equipment: List<EquipmentEntity> = emptyList(),
    /** Defaults to "all available" until the user actually changes something. */
    val selectedEquipmentIds: Set<String> = emptySet(),
    val busy: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val onboardingManager: OnboardingManager,
) : ViewModel() {

    private val unit = MutableStateFlow(WeightUnit.KG)
    private val selectedEquipmentIds = MutableStateFlow<Set<String>>(emptySet())
    private val busy = MutableStateFlow(false)

    /** True once the user has deliberately changed the equipment selection. */
    private val equipmentTouched = MutableStateFlow(false)

    /** True once first-run setup has been completed on this install. */
    val completed: StateFlow<Boolean> = onboardingManager.completed

    init {
        viewModelScope.launch {
            // The seeder inserts equipment asynchronously on first launch;
            // pre-select everything once it lands (if the user hasn't touched).
            val equipment = catalogRepository.observeEquipment().first { it.isNotEmpty() }
            if (!equipmentTouched.value) {
                selectedEquipmentIds.value = equipment.map { it.id }.toSet()
            }
        }
    }

    val uiState = combine(
        catalogRepository.observeEquipment(),
        selectedEquipmentIds,
        unit,
        busy,
    ) { equipment, selectedIds, selectedUnit, isBusy ->
        OnboardingUiState(
            unit = selectedUnit,
            equipment = equipment,
            selectedEquipmentIds = selectedIds,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun setUnit(value: WeightUnit) {
        unit.value = value
    }

    fun toggleEquipment(id: String) {
        equipmentTouched.value = true
        selectedEquipmentIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    /**
     * Persists the choices (units, equipment) and marks onboarding done —
     * starter programs are added from Home's templates banner instead.
     * [onDone] fires once the writes are on disk.
     */
    fun complete(onDone: () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                userRepository.setWeightUnit(unit.value)
                if (equipmentTouched.value) {
                    userRepository.setAvailableEquipment(selectedEquipmentIds.value)
                }
                // Flag only flips after all preceding writes succeeded.
                onboardingManager.markCompleted()
            } finally {
                busy.value = false
                onDone()
            }
        }
    }
}
