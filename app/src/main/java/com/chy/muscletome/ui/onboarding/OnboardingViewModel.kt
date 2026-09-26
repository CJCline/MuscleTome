package com.chy.muscletome.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.onboarding.OnboardingManager
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.template.RoutineTemplate
import com.chy.muscletome.domain.template.RoutineTemplates
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
    val selectedTemplate: RoutineTemplate? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val routineRepository: RoutineRepository,
    private val userRepository: UserRepository,
    private val onboardingManager: OnboardingManager,
) : ViewModel() {

    private val unit = MutableStateFlow(WeightUnit.KG)
    private val selectedEquipmentIds = MutableStateFlow<Set<String>>(emptySet())
    private val selectedTemplate = MutableStateFlow<RoutineTemplate?>(null)
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
        selectedTemplate,
        busy,
    ) { equipment, selectedIds, selectedUnit, template, isBusy ->
        OnboardingUiState(
            unit = selectedUnit,
            equipment = equipment,
            selectedEquipmentIds = selectedIds,
            selectedTemplate = template,
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

    fun selectTemplate(template: RoutineTemplate) {
        selectedTemplate.value = template
    }

    /**
     * Persists the choices (units, equipment, template) and marks onboarding
     * done. Selecting [RoutineTemplates.SCRATCH] creates no routine.
     * [onDone] fires once the writes are on disk (or failed — onboarding
     * simply stays pending in that case).
     */
    fun complete(onDone: () -> Unit) {
        if (busy.value) return
        val template = selectedTemplate.value
        busy.value = true
        viewModelScope.launch {
            try {
                userRepository.setWeightUnit(unit.value)
                if (equipmentTouched.value) {
                    userRepository.setAvailableEquipment(selectedEquipmentIds.value)
                }
                if (template != null && template !== RoutineTemplates.SCRATCH) {
                    val routineId = routineRepository.applyTemplate(template)
                    userRepository.setActiveRoutine(routineId)
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
