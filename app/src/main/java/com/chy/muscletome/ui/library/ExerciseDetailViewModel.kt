package com.chy.muscletome.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseHistory(
    val lastSet: SetLogEntity? = null,
    val bestWeight: Double? = null,
    val sessionCount: Int = 0,
)

data class ExerciseDetailUiState(
    val exercise: ExerciseEntity? = null,
    val equipment: List<EquipmentEntity> = emptyList(),
    val secondaryMuscles: List<MuscleGroupEntity> = emptyList(),
    val history: ExerciseHistory = ExerciseHistory(),
    val primaryMuscleName: String = "",
    val loaded: Boolean = false,
    /** Sets display in the user's unit (they're stored in it). */
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    workoutRepository: WorkoutRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])

    private val history = MutableStateFlow(ExerciseHistory())

    init {
        viewModelScope.launch {
            history.value = ExerciseHistory(
                lastSet = workoutRepository.lastSetForExercise(exerciseId),
                bestWeight = workoutRepository.bestWeightForExercise(exerciseId),
                sessionCount = workoutRepository.sessionCountForExercise(exerciseId),
            )
        }
    }

    val uiState = combine(
        combine(
            catalogRepository.observeExercise(exerciseId),
            catalogRepository.observeEquipmentForExercise(exerciseId),
            catalogRepository.observeSecondaryMusclesForExercise(exerciseId),
            catalogRepository.observeMuscleGroups(),
            history,
        ) { exercise, equipment, secondaryMuscles, muscleGroups, currentHistory ->
            ExerciseDetailUiState(
                exercise = exercise,
                equipment = equipment,
                secondaryMuscles = secondaryMuscles,
                history = currentHistory,
                primaryMuscleName = exercise?.primaryMuscleGroupId
                    ?.let { id -> muscleGroups.find { it.id == id }?.name }
                    .orEmpty(),
                loaded = true,
            )
        },
        userRepository.observeUser(),
    ) { detail, user ->
        detail.copy(weightUnit = user?.weightUnit ?: WeightUnit.KG)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseDetailUiState(),
    )
}
