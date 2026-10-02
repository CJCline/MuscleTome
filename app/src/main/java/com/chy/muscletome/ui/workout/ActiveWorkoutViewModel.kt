package com.chy.muscletome.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chy.muscletome.data.local.dao.ExerciseSetPoint
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.UserEntity
import com.chy.muscletome.data.repository.CatalogRepository
import com.chy.muscletome.data.repository.RoutineRepository
import com.chy.muscletome.data.repository.UserRepository
import com.chy.muscletome.data.repository.WorkoutRepository
import com.chy.muscletome.data.timer.RestTimerManager
import com.chy.muscletome.di.ApplicationScope
import com.chy.muscletome.domain.model.DefaultRepPreference
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.session.EffortScales
import com.chy.muscletome.domain.session.PersonalRecords
import com.chy.muscletome.domain.session.PrCandidate
import com.chy.muscletome.domain.session.SupersetFlow
import com.chy.muscletome.domain.session.SupersetFollowUp
import com.chy.muscletome.domain.session.SupersetMember
import com.chy.muscletome.domain.session.WeightUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveSlot(
    val result: SessionSlotResultEntity,
    val slot: RoutineSlotEntity?,
    val exercise: ExerciseEntity?,
    val sets: List<SetLogEntity>,
) {
    /** Ad-hoc rows carry their own plan; slot-backed rows use the slot's. */
    val plannedSets: Int get() = result.plannedSets ?: slot?.sets ?: 0
    val isComplete: Boolean get() = plannedSets <= 0 || sets.size >= plannedSets
}

/** Time span options for the exercise progress chart. */
enum class ProgressSpan(val days: Int?) {
    LAST_30(30),
    LAST_60(60),
    LAST_90(90),
    ALL_TIME(null);

    val label: String
        get() = when (this) {
            LAST_30 -> "30d"
            LAST_60 -> "60d"
            LAST_90 -> "90d"
            ALL_TIME -> "All"
        }
}

/** One past session's worth of history for the on-screen exercise. */
data class ExerciseSessionSummary(
    val sessionStartEpochMs: Long,
    val setCount: Int,
    val topWeight: Double,
    val bestE1rm: Double,
    val volume: Double,
)

/** One point on the progress chart: best e1RM in a session. */
data class ProgressPoint(
    val sessionStartEpochMs: Long,
    val bestE1rm: Double,
)

/** wger reference info for the on-screen exercise, shown in the info sheet. */
data class ExerciseInfo(
    val exercise: ExerciseEntity? = null,
    val equipment: List<EquipmentEntity> = emptyList(),
    val secondaryMuscles: List<MuscleGroupEntity> = emptyList(),
    val primaryMuscleName: String = "",
)

data class ActiveWorkoutUiState(
    val slots: List<ActiveSlot> = emptyList(),
    val currentIndex: Int = 0,
    val weight: String = "0",
    val reps: String = "8",
    val rpe: String = "",
    val restSecondsLeft: Int = 0,
    val totalRestSeconds: Int = 0,
    val finished: Boolean = false,
    val catalogExercises: List<ExerciseEntity> = emptyList(),
    val swapQuery: String = "",
    /** Search results for the ad-hoc superset partner picker. */
    val supersetExercises: List<ExerciseEntity> = emptyList(),
    val supersetQuery: String = "",
    val lastSessions: List<ExerciseSessionSummary> = emptyList(),
    val progressPoints: List<ProgressPoint> = emptyList(),
    val progressSpan: ProgressSpan = ProgressSpan.LAST_30,
    /** Shared exercise cues (persist on the exercise, cross-session). */
    val note: String = "",
    /** Remark about the on-screen exercise in THIS session only. */
    val sessionNote: String = "",
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** Which scale the effort chips speak (user preference). */
    val effortScale: EffortScale = EffortScale.RPE,
    /** Default rep count preference (MINIMUM or MAXIMUM). */
    val defaultRepPreference: DefaultRepPreference = DefaultRepPreference.MINIMUM,
    /** Configured weight step size from user preference. */
    val configuredWeightStep: Double? = null,
    /** Ids of this exercise's logged sets (this session included) that were
     *  all-time PRs when logged. */
    val prSetIds: Set<String> = emptySet(),
) {
    val current: ActiveSlot? get() = slots.getOrNull(currentIndex)

    /** Members of the current exercise's superset group, in session order. */
    val currentGroup: List<ActiveSlot>
        get() = current?.result?.supersetGroupId?.let { groupId ->
            slots.filter { it.result.supersetGroupId == groupId }
        }.orEmpty()
    val currentSetNumber: Int get() = (current?.sets?.size ?: 0) + 1
    val plannedSets: Int get() = current?.plannedSets ?: 0

    /** Target RPE from the routine slot, expressed on the active scale. */
    val targetEffortLabel: String?
        get() = current?.slot?.targetRpe?.let { EffortScales.label(it, effortScale) }

    /** The set being entered, expressed on the active scale ("" = none). */
    val effortEntryLabel: String
        get() = rpe.toFloatOrNull()?.let { EffortScales.label(it, effortScale) } ?: ""

    /**
     * For a superset member this means the *whole group* is done — the
     * "Next exercise" button must not appear mid-circuit while the partner
     * still owes sets.
     */
    val isCurrentComplete: Boolean
        get() {
            val c = current ?: return true
            val members = if (c.result.supersetGroupId != null) currentGroup else listOf(c)
            return members.all { it.isComplete }
        }
    val isLastExercise: Boolean get() =
        slots.isEmpty() || currentIndex == slots.lastIndex

    /** The slot immediately following the current exercise in routine sequence. */
    val nextSlot: ActiveSlot? get() = slots.getOrNull(currentIndex + 1)

    /** Stepper increment driven by user preference or fallback default. */
    val weightStep: Double get() = configuredWeightStep ?: if (weightUnit == WeightUnit.LB) 5.0 else 2.5
    /** Long-press micro increment (half plate). */
    val weightLongStep: Double get() = weightStep / 2.0
    val weightUnitSuffix: String get() = if (weightUnit == WeightUnit.LB) " lb" else " kg"

    /** The unit a unit-toggle would switch to (kg ↔ lb). */
    val otherUnit: WeightUnit get() = WeightUnits.other(weightUnit)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineDao: RoutineDao,
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val restTimerManager: RestTimerManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val slotsByDay = MutableStateFlow<List<RoutineSlotEntity>>(emptyList())

    // Navigation source of truth: the result id, not the index. Ad-hoc
    // superset partners splice a new row into the ordered list, which would
    // silently shift what "the current index" points at — an id survives it.
    private val currentResultId = MutableStateFlow<String?>(null)
    private val progressSpan = MutableStateFlow(ProgressSpan.LAST_30)
    private val weight = MutableStateFlow("0")
    private val reps = MutableStateFlow("8")
    private val rpe = MutableStateFlow("")
    private val restSecondsLeft = MutableStateFlow(0)
    private val totalRestSeconds = MutableStateFlow(0)
    private val finished = MutableStateFlow(false)
    private val swapQuery = MutableStateFlow("")
    private val supersetQuery = MutableStateFlow("")
    private val note = MutableStateFlow("")
    private val sessionNote = MutableStateFlow("")
    private var restJob: Job? = null
    private var loadedDefaultsForResultId: String? = null

    // Which exercise the note draft belongs to; notes reload whenever the
    // on-screen exercise changes (reroll/swap), not just per slot result.
    private var noteLoadedForExerciseId: String? = null
    private var noteSaveJob: Job? = null

    // Session note belongs to the slot result (this exercise, this workout).
    private var sessionNoteLoadedForResultId: String? = null
    private var sessionNoteSaveJob: Job? = null

    private val currentUser: Flow<UserEntity?> = userRepository.observeUser()

    init {
        viewModelScope.launch {
            workoutRepository.observeSession(sessionId).collect { session ->
                val dayId = session?.routineDayId ?: return@collect
                slotsByDay.value = routineDao.getSlots(dayId)
            }
        }
        // The rest countdown outlives the process — pick it back up here.
        val remaining = restTimerManager.remainingSeconds()
        if (remaining > 0) runCountdownUI(remaining)
    }

    // ID of the exercise shown on screen; follows rerolls and swaps because those
    // rewrite resolvedExerciseId on the slot result, and the slot result flow
    // re-emits whenever that changes.
    private val currentExerciseId: Flow<String?> = combine(
        workoutRepository.observeSlotResults(sessionId),
        currentResultId,
    ) { results, resultId ->
        val ordered = results.sortedBy { it.sortOrder }
        val result = if (resultId == null) ordered.firstOrNull()
        else ordered.find { it.id == resultId } ?: ordered.firstOrNull()
        result?.resolvedExerciseId
    }

    // All logged sets for the on-screen exercise, reactively.
    private val currentExerciseSets: Flow<List<ExerciseSetPoint>> =
        currentExerciseId.flatMapLatest { exerciseId ->
            if (exerciseId == null) flowOf(emptyList())
            else workoutRepository.observeExerciseSetPoints(exerciseId)
        }

    // wger reference info (description, muscles, equipment) for the on-screen
    // exercise; follows rerolls and swaps just like currentExerciseId above.
    val exerciseInfo: StateFlow<ExerciseInfo> = currentExerciseId
        .flatMapLatest { exerciseId ->
            if (exerciseId == null) {
                flowOf(ExerciseInfo())
            } else {
                combine(
                    catalogRepository.observeExercise(exerciseId),
                    catalogRepository.observeEquipmentForExercise(exerciseId),
                    catalogRepository.observeSecondaryMusclesForExercise(exerciseId),
                    catalogRepository.observeMuscleGroups(),
                ) { exercise, equipment, secondaryMuscles, muscleGroups ->
                    ExerciseInfo(
                        exercise = exercise,
                        equipment = equipment,
                        secondaryMuscles = secondaryMuscles,
                        primaryMuscleName = exercise?.primaryMuscleGroupId
                            ?.let { id -> muscleGroups.find { it.id == id }?.name }
                            .orEmpty(),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseInfo())

    val uiState = combine(
        workoutRepository.observeSlotResults(sessionId),
        workoutRepository.observeSets(sessionId),
        slotsByDay,
        catalogRepository.observeExercises(),
        currentResultId,
        weight,
        reps,
        rpe,
        restSecondsLeft,
        totalRestSeconds,
        finished,
        swapQuery,
        currentExerciseSets,
        progressSpan,
        note,
        currentUser,
        sessionNote,
        supersetQuery,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val results = values[0] as List<SessionSlotResultEntity>
        @Suppress("UNCHECKED_CAST")
        val sets = values[1] as List<SetLogEntity>
        @Suppress("UNCHECKED_CAST")
        val routineSlots = values[2] as List<RoutineSlotEntity>
        @Suppress("UNCHECKED_CAST")
        val exercises = values[3] as List<ExerciseEntity>
        val resultId = values[4] as String?
        val weightText = values[5] as String
        val repsText = values[6] as String
        val rpeText = values[7] as String
        val rest = values[8] as Int
        val totalRest = values[9] as Int
        val isFinished = values[10] as Boolean
        val swapFilter = values[11] as String
        @Suppress("UNCHECKED_CAST")
        val exerciseSetPoints = values[12] as List<ExerciseSetPoint>
        val span = values[13] as ProgressSpan
        val noteText = values[14] as String
        @Suppress("UNCHECKED_CAST")
        val user = values[15] as UserEntity?
        val sessionNoteText = values[16] as String
        val supersetFilter = values[17] as String

        val exerciseMap = exercises.associateBy { it.id }
        val slotMap = routineSlots.associateBy { it.id }
        val setsByResult = sets.groupBy { it.sessionSlotResultId }
        // Sort by the session's own snapshot order: ad-hoc rows have no
        // routine slot, and routine edits mid-session must not reshuffle a
        // running workout.
        val orderedResults = results.sortedBy { it.sortOrder }
        val orderedSlots = orderedResults.map { result ->
            ActiveSlot(
                result = result,
                slot = slotMap[result.routineSlotId],
                exercise = exerciseMap[result.resolvedExerciseId],
                sets = (setsByResult[result.id] ?: emptyList()).sortedBy { it.setNumber },
            )
        }
        // Result-id navigation: the index is derived, so an ad-hoc insert
        // can't desync what "current" points at.
        val resolvedIndex = if (resultId == null) 0 else
            orderedSlots.indexOfFirst { it.result.id == resultId }.let { if (it < 0) 0 else it }
        ActiveWorkoutUiState(
            slots = orderedSlots,
            currentIndex = resolvedIndex,
            weight = weightText,
            reps = repsText,
            rpe = rpeText,
            restSecondsLeft = rest,
            totalRestSeconds = totalRest,
            finished = isFinished,
            catalogExercises = exercises.filter {
                it.name.contains(swapFilter, ignoreCase = true)
            },
            swapQuery = swapFilter,
            supersetExercises = exercises.filter {
                it.name.contains(supersetFilter, ignoreCase = true)
            },
            supersetQuery = supersetFilter,
            lastSessions = buildLastSessions(exerciseSetPoints, limit = 3),
            progressPoints = buildProgressPoints(exerciseSetPoints, span),
            progressSpan = span,
            note = noteText,
            sessionNote = sessionNoteText,
            weightUnit = user?.weightUnit ?: WeightUnit.KG,
            effortScale = user?.effortScale ?: EffortScale.RPE,
            defaultRepPreference = user?.defaultRepPreference ?: DefaultRepPreference.MINIMUM,
            configuredWeightStep = user?.weightStep?.value,
            prSetIds = buildPrSetIds(exerciseSetPoints),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    /**
     * PR ids for the on-screen exercise: its full set history (this session
     * included) fed through the shared rule — a set tags as PR when its e1RM
     * strictly beat everything logged before it.
     */
    private fun buildPrSetIds(points: List<ExerciseSetPoint>): Set<String> =
        PersonalRecords.prSetIds(
            points.map { PrCandidate(it.id, it.completedAtEpochMs, it.weight, it.reps) },
        )

    private fun buildLastSessions(
        points: List<ExerciseSetPoint>,
        limit: Int,
    ): List<ExerciseSessionSummary> {
        return points
            .filter { it.sessionId != sessionId }
            .groupBy { it.sessionId }
            .map { (_, sessionPoints) ->
                ExerciseSessionSummary(
                    sessionStartEpochMs = sessionPoints.first().sessionStartEpochMs,
                    setCount = sessionPoints.size,
                    topWeight = sessionPoints.maxOf { it.weight },
                    bestE1rm = sessionPoints.maxOf { p -> PersonalRecords.epley1Rm(p.weight, p.reps) },
                    volume = sessionPoints.sumOf { it.weight * it.reps },
                )
            }
            .sortedByDescending { it.sessionStartEpochMs }
            .take(limit)
    }

    private fun buildProgressPoints(
        points: List<ExerciseSetPoint>,
        span: ProgressSpan,
    ): List<ProgressPoint> {
        val cutoff = span.days?.let { days ->
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        }
        return points
            .filter { it.sessionId != sessionId && (cutoff == null || it.completedAtEpochMs >= cutoff) }
            .groupBy { it.sessionId }
            .map { (_, sessionPoints) ->
                ProgressPoint(
                    sessionStartEpochMs = sessionPoints.first().sessionStartEpochMs,
                    bestE1rm = sessionPoints.maxOf { p -> PersonalRecords.epley1Rm(p.weight, p.reps) },
                )
            }
            .sortedBy { it.sessionStartEpochMs }
    }

    fun onProgressSpanChange(span: ProgressSpan) {
        progressSpan.value = span
    }

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val current = state.current ?: return@collect
                if (loadedDefaultsForResultId != current.result.id) {
                    loadedDefaultsForResultId = current.result.id
                    val last = workoutRepository.lastSetForExercise(current.result.resolvedExerciseId)
                    if (last != null) {
                        weight.value = last.weight.toString()
                        reps.value = last.reps.toString()
                    } else {
                        reps.value = calculateDefaultReps(current.slot, state.defaultRepPreference).toString()
                    }
                }
                // The note belongs to the exercise itself so it persists
                // between sessions; load it whenever the exercise changes.
                val exerciseId = current.result.resolvedExerciseId
                if (noteLoadedForExerciseId != exerciseId) {
                    noteLoadedForExerciseId = exerciseId
                    note.value = current.exercise?.notes.orEmpty()
                }
                // Session notes belong to this exercise in THIS workout.
                if (sessionNoteLoadedForResultId != current.result.id) {
                    sessionNoteLoadedForResultId = current.result.id
                    sessionNote.value = current.result.sessionNote
                }
            }
        }
    }

    fun onWeightChange(value: String) { weight.value = value }
    fun onRepsChange(value: String) { reps.value = value }
    fun onRpeChange(value: String) { rpe.value = value }
    fun onSwapQueryChange(value: String) { swapQuery.value = value }

    /**
     * Unit toggle (kg ↔ lb) from the workout sheet: the preference switch
     * converts the whole log atomically (see UserRepository.setWeightUnit),
     * so the in-flight draft must be converted to match or the next logSet
     * would write the old unit under the new one. Kept as raw text and
     * re-formatted so an unreadable field passes through untouched.
     */
    fun toggleUnit() {
        val state = uiState.value
        val next = state.otherUnit
        val parsed = state.weight.toDoubleOrNull()
        if (parsed != null) {
            weight.value = WeightUnits.displayText(
                WeightUnits.convert(parsed, state.weightUnit, next),
            )
        }
        viewModelScope.launch { userRepository.setWeightUnit(next) }
    }

    fun onSupersetQueryChange(value: String) { supersetQuery.value = value }

    fun onNoteChange(value: String) {
        note.value = value
        scheduleNoteSave()
    }

    fun onSessionNoteChange(value: String) {
        sessionNote.value = value
        scheduleSessionNoteSave()
    }

    // Autosave (both notes): debounce writes so keystrokes don't hammer Room.
    // Target id AND text are captured up front — by the time the debounce fires
    // the user may already have moved to another exercise whose note has been
    // loaded into the field. Saves run on the application scope so they still
    // land when the ViewModel is torn down mid-debounce (Back now leaves the
    // session open and is the normal exit).
    private fun scheduleNoteSave() {
        val exerciseId = uiState.value.current?.result?.resolvedExerciseId ?: return
        val text = note.value
        noteSaveJob?.cancel()
        noteSaveJob = applicationScope.launch {
            delay(600)
            catalogRepository.updateExerciseNotes(exerciseId, text)
        }
    }

    private fun flushNoteSave() {
        val pending = noteSaveJob ?: return
        val exerciseId = uiState.value.current?.result?.resolvedExerciseId ?: return
        val text = note.value
        pending.cancel()
        noteSaveJob = null
        applicationScope.launch { catalogRepository.updateExerciseNotes(exerciseId, text) }
    }

    private fun scheduleSessionNoteSave() {
        val resultId = uiState.value.current?.result?.id ?: return
        val text = sessionNote.value
        sessionNoteSaveJob?.cancel()
        sessionNoteSaveJob = applicationScope.launch {
            delay(600)
            workoutRepository.updateSessionNote(resultId, text)
        }
    }

    private fun flushSessionNoteSave() {
        val pending = sessionNoteSaveJob ?: return
        val resultId = uiState.value.current?.result?.id ?: return
        val text = sessionNote.value
        pending.cancel()
        sessionNoteSaveJob = null
        applicationScope.launch { workoutRepository.updateSessionNote(resultId, text) }
    }

    fun bumpWeight(delta: Double) {
        val current = weight.value.toDoubleOrNull() ?: 0.0
        weight.value = WeightUnits.displayText((current + delta).coerceAtLeast(0.0))
    }

    fun bumpReps(delta: Int) {
        val current = reps.value.toIntOrNull() ?: 0
        reps.value = (current + delta).coerceAtLeast(0).toString()
    }

    fun logSet() {
        val state = uiState.value
        val current = state.current ?: return
        val weightValue = state.weight.toDoubleOrNull() ?: return
        val repsValue = state.reps.toIntOrNull() ?: return
        val rpeValue = state.rpe.toFloatOrNull()
        val restSeconds = current.slot?.restSeconds ?: 0
        viewModelScope.launch {
            workoutRepository.logSet(
                sessionSlotResultId = current.result.id,
                setNumber = state.currentSetNumber,
                weight = weightValue,
                reps = repsValue,
                rpe = rpeValue,
                restSecondsActual = restSeconds,
            )
            // Superset groups navigate round-robin: mid-round hops move
            // straight to the next member (no rest); a wrapped round rests
            // first, using the just-completed member's rest seconds.
            val members = state.currentGroup.ifEmpty { listOf(current) }
                .map { slot ->
                    SupersetMember(
                        resultId = slot.result.id,
                        plannedSets = slot.plannedSets,
                        // The set just logged counts as completed for the current
                        // member; partners keep their logged counts.
                        completedSets = slot.sets.size + if (slot.result.id == current.result.id) 1 else 0,
                        restSeconds = slot.slot?.restSeconds ?: 0,
                    )
                }
            when (val followUp = SupersetFlow.followUpAfterSet(
                members = members,
                currentResultId = current.result.id,
                currentRestSeconds = restSeconds,
            )) {
                is SupersetFollowUp.Advance -> {
                    // Group complete handled above via Stay; here the group may
                    // still owe sets, so land the move and (for wraps) the rest.
                    if (followUp.restSeconds > 0) startRest(followUp.restSeconds)
                    moveToResult(followUp.targetResultId)
                }
                is SupersetFollowUp.Stay -> startRest(followUp.restSeconds)
            }
        }
    }

    /**
     * Navigates directly to any exercise in the workout routine by its result id.
     *
     * Progress behavior when navigating away:
     * - Logged sets: All sets already logged for the current exercise are saved in Room DB
     *   and remain attached to its [ActiveSlot]. They are preserved when switching exercises
     *   and will be displayed when returning to this exercise later.
     * - Skipped exercises: Leaving an exercise before completing all planned sets does NOT
     *   mark it complete or remove it from the session. It remains available in [ActiveWorkoutUiState.slots].
     * - Notes: Pending exercise cues and session notes are immediately flushed to storage.
     * - Timer: Any active rest countdown remains running when switching exercises.
     */
    fun selectExercise(resultId: String) {
        if (currentResultId.value == resultId) return
        moveToResult(resultId)
    }

    /** Navigates to a result id, flushing drafts that belong to the old one. */
    private fun moveToResult(resultId: String) {
        if (currentResultId.value == resultId) return
        flushNoteSave()
        flushSessionNoteSave()
        currentResultId.value = resultId
    }

    /** Undoes the most recent set of the on-screen exercise. */
    fun undoLastSet() {
        val current = uiState.value.current ?: return
        val last = current.sets.lastOrNull() ?: return
        viewModelScope.launch { workoutRepository.deleteSet(last) }
    }

    /** Corrects a mis-logged set in place (tap-to-edit). */
    fun updateSet(set: SetLogEntity, weight: Double, reps: Int, rpe: Float?) {
        viewModelScope.launch {
            workoutRepository.updateSet(set.copy(weight = weight, reps = reps, rpe = rpe))
        }
    }

    fun deleteSet(set: SetLogEntity) {
        viewModelScope.launch { workoutRepository.deleteSet(set) }
    }

    fun nextExercise() {
        val state = uiState.value
        if (state.isLastExercise) return
        // "Next" from a group's last incomplete member: for grouped slots,
        // skip every remaining member of the group — the user is explicitly
        // moving on from the whole circuit. Note that if the group is
        // mid-round, only the completed members were skipped by the flow.
        val from = state.current ?: return
        val next = state.slots.drop(state.currentIndex + 1).firstOrNull { slot ->
            slot.result.supersetGroupId == null ||
                slot.result.supersetGroupId != from.result.supersetGroupId
        } ?: return
        moveToResult(next.result.id)
    }

    fun skipRest() {
        restJob?.cancel()
        restSecondsLeft.value = 0
        totalRestSeconds.value = 0
        restTimerManager.cancel()
    }

    /** Adds [seconds] to the countdown; restarts the timer from the new total. */
    fun addRest(seconds: Int) {
        val current = restSecondsLeft.value
        if (current <= 0) return
        restTimerManager.addSeconds(seconds)
        totalRestSeconds.update { it + seconds }
        runCountdownUI(current + seconds)
    }

    fun finishWorkout() {
        flushNoteSave()
        flushSessionNoteSave()
        restJob?.cancel()
        restSecondsLeft.value = 0
        totalRestSeconds.value = 0
        restTimerManager.cancel()
        viewModelScope.launch {
            workoutRepository.finishSession(sessionId)
            finished.value = true
        }
    }

    fun reasonLabel(): String {
        val current = uiState.value.current ?: return ""
        return when (current.result.selectionReason) {
            SelectionReason.FIXED -> "Pinned in your routine"
            SelectionReason.AI_ROTATED -> "Picked for this target slot"
            SelectionReason.USER_REROLL -> "Rerolled"
            SelectionReason.USER_OVERRIDE -> "You chose this"
        }
    }

    fun canReroll(): Boolean {
        val current = uiState.value.current ?: return false
        return current.slot?.type == SlotType.TARGET && current.sets.isEmpty()
    }

    fun reroll() {
        val state = uiState.value
        val current = state.current ?: return
        if (!canReroll()) return
        flushNoteSave()
        viewModelScope.launch {
            workoutRepository.rerollSlot(current.result, state.currentIndex)
        }
    }

    /** True when the current exercise can become an ad-hoc superset anchor. */
    fun canSuperset(): Boolean {
        val current = uiState.value.current ?: return false
        // One pair per anchor (v1): already-grouped results can't re-pair.
        if (current.result.supersetGroupId != null) return false
        // An exercise with no sets left would never visit its partner.
        return !current.isComplete
    }

    /**
     * Creates the ad-hoc superset: inserts the library exercise right after
     * the anchor and jumps straight to it (canceling any running rest — the
     * whole point of a superset is going right to the partner).
     */
    fun addSupersetPartner(exerciseId: String, oneShot: Boolean) {
        val current = uiState.value.current ?: return
        if (!canSuperset()) return
        flushNoteSave()
        flushSessionNoteSave()
        restJob?.cancel()
        restSecondsLeft.value = 0
        totalRestSeconds.value = 0
        restTimerManager.cancel()
        val plannedSets = if (oneShot) 1 else {
            (current.plannedSets - current.sets.size).coerceAtLeast(1)
        }
        viewModelScope.launch {
            val partnerId = workoutRepository.addSupersetPartner(
                sessionId = sessionId,
                anchor = current.result,
                exerciseId = exerciseId,
                plannedSets = plannedSets,
            )
            // The partner lands right after the anchor; the result flow
            // re-emits with the new row, and id-navigation lands on it.
            moveToResult(partnerId)
        }
    }

    fun overrideWith(exerciseId: String) {
        val current = uiState.value.current ?: return
        if (current.sets.isNotEmpty()) return
        flushNoteSave()
        viewModelScope.launch {
            workoutRepository.overrideSlot(current.result, exerciseId)
        }
    }

    /** Reorders the workout session slots in place; current selection stays intact. */
    fun reorderSlots(orderedIds: List<String>) {
        viewModelScope.launch {
            workoutRepository.reorderSlotResults(sessionId, orderedIds)
        }
    }

    private fun startRest(seconds: Int) {
        restJob?.cancel()
        if (seconds <= 0) {
            restSecondsLeft.value = 0
            totalRestSeconds.value = 0
            return
        }
        totalRestSeconds.value = seconds
        // The notification + alarm survive screen-off and process death; the
        // coroutine only drives the on-screen ring.
        restTimerManager.startRest(seconds, uiState.value.current?.exercise?.name.orEmpty())
        runCountdownUI(seconds)
    }

    /** Drives the on-screen ring only — no alarm/notification side effects. */
    private fun runCountdownUI(seconds: Int) {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            restSecondsLeft.value = seconds
            if (totalRestSeconds.value <= 0) {
                totalRestSeconds.value = seconds
            }
            while (restSecondsLeft.value > 0) {
                delay(1_000)
                restSecondsLeft.update { (it - 1).coerceAtLeast(0) }
            }
            totalRestSeconds.value = 0
        }
    }

    companion object {
        fun calculateDefaultReps(
            slot: RoutineSlotEntity?,
            preference: DefaultRepPreference,
        ): Int {
            if (slot == null) {
                return when (preference) {
                    DefaultRepPreference.MINIMUM -> RoutineRepository.DEFAULT_REP_MIN
                    DefaultRepPreference.MAXIMUM -> RoutineRepository.DEFAULT_REP_MAX
                }
            }
            val min = slot.repRangeMin
            val max = slot.repRangeMax

            return when (preference) {
                DefaultRepPreference.MINIMUM -> {
                    if (min > 0) min
                    else if (max > 0) max
                    else RoutineRepository.DEFAULT_REP_MIN
                }
                DefaultRepPreference.MAXIMUM -> {
                    if (max > 0) max
                    else if (min > 0) min
                    else RoutineRepository.DEFAULT_REP_MAX
                }
            }
        }
    }
}
