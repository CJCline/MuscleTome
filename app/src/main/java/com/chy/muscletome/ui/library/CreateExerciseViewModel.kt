package com.chy.muscletome.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.CreateExerciseResult
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class CreateExerciseUiState(
    val name: String = "",
    val category: String = "CHEST",
    val muscleGroup: String = "PECTORALIS",
    val parentExerciseId: String? = null,
    val parentExercises: List<ExerciseEntity> = emptyList(),
    val nameTaken: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CreateExerciseViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val name = MutableStateFlow("")
    private val category = MutableStateFlow("CHEST")
    private val muscleGroup = MutableStateFlow("PECTORALIS")
    private val parentExerciseId = MutableStateFlow<String?>(null)
    private val saved = MutableStateFlow(false)

    private val nameTaken = name.debounce(250.milliseconds).flatMapLatest { value ->
        if (value.isBlank()) flowOf(false) else repository.observeNameTaken(value)
    }

    val uiState = combine(
        name,
        category,
        muscleGroup,
        parentExerciseId,
        repository.observeParentExercises(),
        nameTaken,
        saved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val currentName = values[0] as String
        val currentCategory = values[1] as String
        val currentMuscle = values[2] as String
        val parentId = values[3] as String?
        @Suppress("UNCHECKED_CAST")
        val parents = values[4] as List<ExerciseEntity>
        val taken = values[5] as Boolean
        val isSaved = values[6] as Boolean

        CreateExerciseUiState(
            name = currentName,
            category = currentCategory,
            muscleGroup = currentMuscle,
            parentExerciseId = parentId,
            parentExercises = parents,
            nameTaken = taken,
            canSave = currentName.isNotBlank() && !taken,
            saved = isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateExerciseUiState())

    fun onNameChange(value: String) { name.value = value }
    fun onCategoryChange(value: String) { category.value = value.uppercase(Locale.US) }
    fun onMuscleGroupChange(value: String) { muscleGroup.value = value.uppercase(Locale.US) }
    fun onParentExerciseSelected(id: String?) { parentExerciseId.value = id }

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val result = repository.createCustomExercise(
                name = state.name.uppercase(Locale.US).trim(),
                description = "",
                primaryMuscleGroupId = state.category.lowercase(Locale.US),
                movementType = if (state.parentExerciseId == null) MovementType.COMPOUND else MovementType.ISOLATION,
                movementPattern = MovementPattern.OTHER,
                difficulty = Difficulty.INTERMEDIATE,
                equipmentIds = emptyList(),
                secondaryMuscleGroupIds = emptyList(),
                category = state.category.uppercase(Locale.US).trim(),
                muscleGroup = state.muscleGroup.uppercase(Locale.US).trim(),
                parentExerciseId = state.parentExerciseId,
            )
            if (result == CreateExerciseResult.SUCCESS) {
                saved.value = true
            }
        }
    }
}
