package com.workouts.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workouts.app.BreakTimerService
import com.workouts.app.WorkoutsApp
import com.workouts.app.data.ActiveExerciseEntity
import com.workouts.app.data.ActiveSetEntity
import com.workouts.app.data.ActiveWorkoutEntity
import com.workouts.app.data.Exercise
import com.workouts.app.data.ExerciseSettings
import com.workouts.app.data.FitnessRepository
import com.workouts.app.data.GoogleLinkStatus
import com.workouts.app.data.Program
import com.workouts.app.data.ProgramDetail
import com.workouts.app.data.UpsertExerciseSettingsRequest
import com.workouts.app.data.WarmupSet
import com.workouts.app.data.WorkoutSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkoutsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WorkoutsApp
    private val repo get() = app.repository
    private val prefs = application.getSharedPreferences("workouts_prefs", Context.MODE_PRIVATE)

    // Programs
    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs

    private val _currentProgram = MutableStateFlow<ProgramDetail?>(null)
    val currentProgram: StateFlow<ProgramDetail?> = _currentProgram

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises

    // Active workout
    private val _activeWorkout = MutableStateFlow<ActiveWorkoutEntity?>(null)
    val activeWorkout: StateFlow<ActiveWorkoutEntity?> = _activeWorkout

    private val _activeExercises = MutableStateFlow<List<ActiveExerciseEntity>>(emptyList())
    val activeExercises: StateFlow<List<ActiveExerciseEntity>> = _activeExercises

    private val _activeSets = MutableStateFlow<List<ActiveSetEntity>>(emptyList())
    val activeSets: StateFlow<List<ActiveSetEntity>> = _activeSets

    private val _completedWarmups = MutableStateFlow<List<ActiveSetEntity>>(emptyList())
    val completedWarmups: StateFlow<List<ActiveSetEntity>> = _completedWarmups

    private val _bodyWeight = MutableStateFlow<Double?>(null)
    val bodyWeight: StateFlow<Double?> = _bodyWeight

    // Deload suggestion
    private val _showDeloadSuggestion = MutableStateFlow(false)
    val showDeloadSuggestion: StateFlow<Boolean> = _showDeloadSuggestion

    // Workout timer
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds
    private var timerJob: Job? = null
    private var workoutStoppedAt: Long? = null

    // Break timer — state comes from the foreground service
    val breakElapsed: StateFlow<Int> = BreakTimerService.elapsedSeconds
    val breakTarget: StateFlow<Int> = BreakTimerService.targetDuration
    val breakRunning: StateFlow<Boolean> = BreakTimerService.isRunning

    private val _breakDuration = MutableStateFlow(prefs.getInt("break_duration", 90))
    val breakDuration: StateFlow<Int> = _breakDuration

    private val _failBreakDuration = MutableStateFlow(prefs.getInt("fail_break_duration", 300))
    val failBreakDuration: StateFlow<Int> = _failBreakDuration

    // Workout history
    private val _workoutHistory = MutableStateFlow<List<WorkoutSummary>>(emptyList())
    val workoutHistory: StateFlow<List<WorkoutSummary>> = _workoutHistory

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Google account link
    private val _googleLink = MutableStateFlow<GoogleLinkStatus?>(null)
    val googleLink: StateFlow<GoogleLinkStatus?> = _googleLink

    init {
        viewModelScope.launch {
            val existing = repo.getActiveWorkout()
            if (existing != null) {
                _activeWorkout.value = existing
                _activeExercises.value = repo.getActiveExercises()
                _activeSets.value = repo.getWorkingSets()
                _completedWarmups.value = repo.getCompletedWarmups()
                _bodyWeight.value = existing.bodyWeight
                if (existing.stoppedAt != null) {
                    // Timer was stopped — show frozen time
                    workoutStoppedAt = existing.stoppedAt
                    _elapsedSeconds.value = (existing.stoppedAt - existing.startedAt) / 1000
                } else {
                    startTimer(existing.startedAt)
                }
            }
        }
    }

    fun clearError() { _error.value = null }

    // --- Programs ---

    fun loadPrograms() {
        viewModelScope.launch {
            _isLoading.value = true
            try { _programs.value = repo.getPrograms() }
            catch (e: Exception) { _error.value = "Failed to load programs: ${e.message}" }
            _isLoading.value = false
        }
    }

    fun moveProgram(from: Int, to: Int) {
        val list = _programs.value.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        _programs.value = list
    }

    fun saveReorder() {
        viewModelScope.launch {
            try { repo.reorderPrograms(_programs.value.map { it.id }) }
            catch (e: Exception) { _error.value = "Failed to reorder: ${e.message}" }
        }
    }

    fun createProgram(name: String) {
        viewModelScope.launch {
            try { repo.createProgram(name); loadPrograms() }
            catch (e: Exception) { _error.value = "Failed to create program: ${e.message}" }
        }
    }

    fun loadProgram(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try { _currentProgram.value = repo.getProgram(id) }
            catch (e: Exception) { _error.value = "Failed to load program: ${e.message}" }
            _isLoading.value = false
        }
    }

    fun renameProgram(id: Long, name: String) {
        viewModelScope.launch {
            try { repo.renameProgram(id, name); loadProgram(id) }
            catch (e: Exception) { _error.value = "Failed to rename program: ${e.message}" }
        }
    }

    fun deleteProgram(id: Long) {
        viewModelScope.launch {
            try { repo.deleteProgram(id); loadPrograms() }
            catch (e: Exception) { _error.value = "Failed to delete program: ${e.message}" }
        }
    }

    // --- Exercises ---

    fun loadExercises() {
        viewModelScope.launch {
            try { _exercises.value = repo.getExercises() }
            catch (e: Exception) { _error.value = "Failed to load exercises: ${e.message}" }
        }
    }

    fun addExerciseToProgram(programId: Long, exerciseId: Long) {
        viewModelScope.launch {
            try { repo.addExercise(programId, exerciseId); loadProgram(programId) }
            catch (e: Exception) { _error.value = "Failed to add exercise: ${e.message}" }
        }
    }

    fun removeExerciseFromProgram(programId: Long, exerciseId: Long) {
        viewModelScope.launch {
            try { repo.removeExercise(programId, exerciseId); loadProgram(programId) }
            catch (e: Exception) { _error.value = "Failed to remove exercise: ${e.message}" }
        }
    }

    // --- Exercise Settings ---

    fun saveExerciseSettings(programId: Long, exerciseId: Long, request: UpsertExerciseSettingsRequest) {
        viewModelScope.launch {
            try {
                repo.upsertExerciseSettings(programId, exerciseId, request)
                val exercises = _activeExercises.value.toMutableList()
                val idx = exercises.indexOfFirst { it.exerciseId == exerciseId }
                if (idx >= 0) {
                    exercises[idx] = exercises[idx].copy(
                        workingWeight = request.working_weight,
                        targetReps = request.target_reps,
                        targetSets = request.target_sets,
                        incrementAmount = request.increment_amount,
                        deloadPercent = request.deload_percent,
                        successThreshold = request.success_threshold,
                        failureThreshold = request.failure_threshold
                    )
                    _activeExercises.value = exercises

                    // If target sets reduced, delete excess logged sets
                    val currentSets = _activeSets.value.filter { it.exerciseId == exerciseId }
                    if (currentSets.size > request.target_sets) {
                        val toDelete = currentSets.drop(request.target_sets)
                        toDelete.forEach { repo.deleteSet(it.id) }
                        _activeSets.value = repo.getWorkingSets()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to save settings: ${e.message}"
            }
        }
    }

    // --- Active Workout ---

    fun loadProgramAndStart(programId: Long) {
        viewModelScope.launch {
            try {
                val program = repo.getProgram(programId)
                if (program.exercises.isEmpty()) {
                    _error.value = "Add exercises to this program first"
                    return@launch
                }
                startWorkout(program)
            } catch (e: Exception) {
                _error.value = "Failed to start workout: ${e.message}"
            }
        }
    }

    fun startWorkout(program: ProgramDetail) {
        viewModelScope.launch {
            // Check if deload should be suggested (> 7 days since last workout for this program)
            try {
                val lastWorkout = repo.getLastWorkoutDate(program.id)
                if (lastWorkout.last_date != null) {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    val lastDate = fmt.parse(lastWorkout.last_date.take(19))
                    if (lastDate != null) {
                        val daysSince = (System.currentTimeMillis() - lastDate.time) / (1000 * 60 * 60 * 24)
                        _showDeloadSuggestion.value = daysSince >= 7
                    }
                }
            } catch (_: Exception) {}

            repo.startWorkout(program.id, program.name, program.exercises)
            val workout = repo.getActiveWorkout()!!
            _activeWorkout.value = workout
            _activeExercises.value = repo.getActiveExercises()
            _activeSets.value = emptyList()
            _completedWarmups.value = emptyList()
            _bodyWeight.value = null
            lastExerciseId = -1
            lastNextSetNumber = 0
            lastLoggedSetId = -1
            workoutStoppedAt = null
            startTimer(workout.startedAt)
        }
    }

    fun applyDeload() {
        viewModelScope.launch {
            val programId = _activeWorkout.value?.programId ?: return@launch
            val exercises = _activeExercises.value.toMutableList()
            for (i in exercises.indices) {
                val ex = exercises[i]
                if (ex.workingWeight > 0 && ex.deloadPercent > 0) {
                    val newWeight = com.workouts.app.data.FitnessRepository.roundToPlate(
                        ex.workingWeight * (1 - ex.deloadPercent / 100)
                    )
                    exercises[i] = ex.copy(workingWeight = newWeight)
                    // Save to API
                    try {
                        repo.upsertExerciseSettings(programId, ex.exerciseId,
                            com.workouts.app.data.UpsertExerciseSettingsRequest(
                                working_weight = newWeight,
                                target_reps = ex.targetReps,
                                target_sets = ex.targetSets,
                                increment_amount = ex.incrementAmount,
                                deload_percent = ex.deloadPercent,
                                success_threshold = ex.successThreshold,
                                failure_threshold = ex.failureThreshold
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
            _activeExercises.value = exercises
            _showDeloadSuggestion.value = false
        }
    }

    fun dismissDeloadSuggestion() {
        _showDeloadSuggestion.value = false
    }

    fun setBodyWeight(weight: Double?) {
        _bodyWeight.value = weight
        viewModelScope.launch {
            repo.updateBodyWeight(weight)
        }
    }

    private fun startTimer(startedAt: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _elapsedSeconds.value = (System.currentTimeMillis() - startedAt) / 1000
                delay(1000)
            }
        }
    }

    fun logWarmupSet(exerciseId: Long, exerciseName: String, reps: Int, weight: Double) {
        viewModelScope.launch {
            val currentWarmups = _completedWarmups.value.filter { it.exerciseId == exerciseId }
            val nextOrder = (currentWarmups.maxOfOrNull { it.setOrder } ?: 0) + 1
            repo.logSet(exerciseId, exerciseName, nextOrder, reps, weight, isWarmup = true)
            _completedWarmups.value = repo.getCompletedWarmups()
        }
    }

    fun undoWarmupSet(setId: Long) {
        viewModelScope.launch {
            repo.deleteSet(setId)
            _completedWarmups.value = repo.getCompletedWarmups()
        }
    }

    private var lastLoggedSetId: Long = -1

    private fun isLastSetOfExercise(exerciseId: Long): Boolean {
        val exercise = _activeExercises.value.find { it.exerciseId == exerciseId } ?: return false
        val count = _activeSets.value.count { it.exerciseId == exerciseId }
        return count >= exercise.targetSets
    }

    private fun areAllExercisesComplete(): Boolean {
        return _activeExercises.value.all { ex ->
            val count = _activeSets.value.count { it.exerciseId == ex.exerciseId }
            count >= ex.targetSets
        }
    }

    fun logWorkingSet(exerciseId: Long, exerciseName: String, reps: Int, weight: Double) {
        viewModelScope.launch {
            val exercise = _activeExercises.value.find { it.exerciseId == exerciseId }
            val currentSets = _activeSets.value.filter { it.exerciseId == exerciseId }
            val nextOrder = (currentSets.maxOfOrNull { it.setOrder } ?: 0) + 1
            val id = repo.logSet(exerciseId, exerciseName, nextOrder, reps, weight, isWarmup = false)
            lastLoggedSetId = id
            _activeSets.value = repo.getWorkingSets()

            // Update notification context for THIS exercise
            val updatedCount = _activeSets.value.count { it.exerciseId == exerciseId }
            lastExerciseId = exerciseId
            lastExerciseName = exerciseName
            lastSetWeight = weight
            lastSetReps = reps
            lastNextSetNumber = updatedCount + 1
            lastTotalSets = exercise?.targetSets ?: 0

            if (isLastSetOfExercise(exerciseId)) {
                skipBreakTimer()
                if (areAllExercisesComplete()) {
                    timerJob?.cancel()
                    workoutStoppedAt = System.currentTimeMillis()
                    viewModelScope.launch { repo.updateStoppedAt(workoutStoppedAt) }
                }
            } else {
                startBreakTimer(_breakDuration.value)
            }
        }
    }

    fun decrementSetReps(setId: Long) {
        viewModelScope.launch {
            val set = _activeSets.value.find { it.id == setId }
            val exercise = set?.let { s -> _activeExercises.value.find { it.exerciseId == s.exerciseId } }

            repo.decrementSetReps(setId)
            _activeSets.value = repo.getWorkingSets()

            if (set != null && exercise != null) {
                val newReps = set.reps - 1

                // Update notification set count (set may have been deleted if reps hit 0)
                val updatedCount = _activeSets.value.count { it.exerciseId == set.exerciseId }
                lastExerciseId = set.exerciseId
                lastExerciseName = set.exerciseName
                lastNextSetNumber = updatedCount + 1
                lastTotalSets = exercise.targetSets

                // Never start rest timer on the last set
                if (!isLastSetOfExercise(set.exerciseId)) {
                    if (setId == lastLoggedSetId && newReps < exercise.targetReps && set.reps >= exercise.targetReps) {
                        startBreakTimer(_failBreakDuration.value)
                    }
                }
            }
        }
    }

    fun deleteSet(id: Long) {
        viewModelScope.launch {
            repo.deleteSet(id)
            _activeSets.value = repo.getWorkingSets()
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.finishWorkout(workoutStoppedAt)
                _activeWorkout.value = null
                _activeExercises.value = emptyList()
                _activeSets.value = emptyList()
                _completedWarmups.value = emptyList()
                timerJob?.cancel()
                _elapsedSeconds.value = 0
                stopBreakTimerService()
                loadWorkoutHistory()
            } catch (e: Exception) {
                _error.value = "Failed to submit workout: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun cancelWorkout() {
        viewModelScope.launch {
            repo.cancelWorkout()
            _activeWorkout.value = null
            _activeExercises.value = emptyList()
            _activeSets.value = emptyList()
            _completedWarmups.value = emptyList()
            timerJob?.cancel()
            _elapsedSeconds.value = 0
            stopBreakTimerService()
        }
    }

    // --- Break Timer (delegated to foreground service) ---

    private val _lastExerciseId = MutableStateFlow(-1L)
    val lastLoggedExerciseId: StateFlow<Long> = _lastExerciseId
    private var lastExerciseId: Long
        get() = _lastExerciseId.value
        set(value) { _lastExerciseId.value = value }
    private var lastExerciseName: String = ""
    private var lastSetWeight: Double = 0.0
    private var lastSetReps: Int = 0
    private var lastNextSetNumber: Int = 0
    private var lastTotalSets: Int = 0

    /** Called when switching from warm-up to working sets for an exercise */
    fun startWarmupToWorkingBreak(exerciseId: Long, exerciseName: String, weight: Double, targetReps: Int, targetSets: Int) {
        lastExerciseId = exerciseId
        lastExerciseName = exerciseName
        lastSetWeight = weight
        lastSetReps = targetReps
        lastNextSetNumber = 1
        lastTotalSets = targetSets
        startBreakTimer(_breakDuration.value)
    }

    fun startBreakTimer(duration: Int = _breakDuration.value) {
        val context = getApplication<Application>()
        val intent = Intent(context, BreakTimerService::class.java).apply {
            action = BreakTimerService.ACTION_START
            putExtra(BreakTimerService.EXTRA_DURATION, duration)
            putExtra(BreakTimerService.EXTRA_EXERCISE, lastExerciseName)
            putExtra(BreakTimerService.EXTRA_NEXT_SET, lastNextSetNumber)
            putExtra(BreakTimerService.EXTRA_TOTAL_SETS, lastTotalSets)
            putExtra(BreakTimerService.EXTRA_WEIGHT, lastSetWeight)
            putExtra(BreakTimerService.EXTRA_REPS, lastSetReps)
        }
        context.startForegroundService(intent)
    }

    /** Called from notification pass/fail actions */
    fun handleNotificationAction(action: String) {
        if (lastExerciseId > 0 && lastExerciseName.isNotEmpty()) {
            val exercise = _activeExercises.value.find { it.exerciseId == lastExerciseId }
            val targetReps = exercise?.targetReps ?: lastSetReps
            if (action == BreakTimerService.ACTION_PASS) {
                logWorkingSet(lastExerciseId, lastExerciseName, targetReps, lastSetWeight)
            } else if (action == BreakTimerService.ACTION_FAIL) {
                viewModelScope.launch {
                    val currentSets = _activeSets.value.filter { it.exerciseId == lastExerciseId }
                    val nextOrder = (currentSets.maxOfOrNull { it.setOrder } ?: 0) + 1
                    val id = repo.logSet(lastExerciseId, lastExerciseName, nextOrder, targetReps - 1, lastSetWeight, isWarmup = false)
                    lastLoggedSetId = id
                    _activeSets.value = repo.getWorkingSets()
                    lastExerciseId = lastExerciseId
                    lastExerciseName = lastExerciseName
                    lastSetWeight = lastSetWeight
                    lastSetReps = targetReps
                    val updatedCount = _activeSets.value.count { it.exerciseId == lastExerciseId }
                    lastNextSetNumber = updatedCount + 1
                    lastTotalSets = exercise?.targetSets ?: 0
                    if (isLastSetOfExercise(lastExerciseId)) {
                        skipBreakTimer()
                        if (areAllExercisesComplete()) {
                            timerJob?.cancel()
                            workoutStoppedAt = System.currentTimeMillis()
                            viewModelScope.launch { repo.updateStoppedAt(workoutStoppedAt) }
                        }
                    } else {
                        startBreakTimer(_failBreakDuration.value)
                    }
                }
            }
        }
    }

    fun skipBreakTimer() {
        val context = getApplication<Application>()
        val intent = Intent(context, BreakTimerService::class.java).apply {
            action = BreakTimerService.ACTION_SKIP
        }
        context.startService(intent)
    }

    private fun stopBreakTimerService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, BreakTimerService::class.java))
    }

    fun setBreakDuration(seconds: Int) {
        _breakDuration.value = seconds
        prefs.edit().putInt("break_duration", seconds).apply()
    }

    fun setFailBreakDuration(seconds: Int) {
        _failBreakDuration.value = seconds
        prefs.edit().putInt("fail_break_duration", seconds).apply()
    }

    // --- Warm-up Generation ---

    fun getWarmupSets(workingWeight: Double, targetReps: Int = 10, exerciseName: String = ""): List<WarmupSet> {
        return FitnessRepository.generateWarmupSets(workingWeight, targetReps, exerciseName)
    }

    // --- History ---

    fun loadWorkoutHistory() {
        viewModelScope.launch {
            try { _workoutHistory.value = repo.getWorkouts() }
            catch (e: Exception) { _error.value = "Failed to load history: ${e.message}" }
        }
    }

    fun deleteAllWorkouts() {
        viewModelScope.launch {
            try { repo.deleteAllWorkouts(); _workoutHistory.value = emptyList() }
            catch (e: Exception) { _error.value = "Failed to delete history: ${e.message}" }
        }
    }

    fun importWorkouts(request: com.workouts.app.data.ImportWorkoutRequest, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val result = repo.importWorkouts(request)
                loadWorkoutHistory()
                onDone(result.imported)
            } catch (e: Exception) {
                _error.value = "Import failed: ${e.message}"
                onDone(0)
            }
        }
    }

    fun updateWorkoutBodyWeight(workoutId: Long, weight: Double) {
        viewModelScope.launch {
            try { repo.updateWorkoutBodyWeight(workoutId, weight); loadWorkoutHistory() }
            catch (e: Exception) { _error.value = "Failed to update body weight: ${e.message}" }
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            try { repo.deleteWorkout(id); loadWorkoutHistory() }
            catch (e: Exception) { _error.value = "Failed to delete workout: ${e.message}" }
        }
    }

    fun loadWorkoutDetail(id: Long, onLoaded: (com.workouts.app.data.WorkoutDetailResponse) -> Unit) {
        viewModelScope.launch {
            try { onLoaded(repo.getWorkout(id)) }
            catch (e: Exception) { _error.value = "Failed to load workout: ${e.message}" }
        }
    }

    // --- Settings ---

    // --- Progress ---

    suspend fun getExercisesWithHistorySync(): List<com.workouts.app.data.Exercise> = repo.getExercisesWithHistory()
    suspend fun getBodyWeightProgressSync(): List<com.workouts.app.data.ProgressPoint> = repo.getBodyWeightProgress()
    suspend fun getExerciseProgressSync(exerciseId: Long): List<com.workouts.app.data.ProgressPoint> = repo.getExerciseProgress(exerciseId)

    // --- Google account link ---

    fun loadGoogleLink() {
        viewModelScope.launch {
            _googleLink.value = try { repo.getGoogleLink() } catch (e: Exception) { null }
        }
    }

    fun linkGoogle(idToken: String, onResult: (GoogleLinkStatus?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val status = repo.linkGoogle(idToken)
                _googleLink.value = status
                if (status.restored == true) {
                    // The device now points at a different user; reload everything
                    loadPrograms()
                    loadWorkoutHistory()
                }
                onResult(status, null)
            } catch (e: Exception) {
                onResult(null, e.message)
            }
        }
    }

    fun unlinkGoogle(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.unlinkGoogle()
                _googleLink.value = GoogleLinkStatus(linked = false, email = null, restored = null)
                onResult(null)
            } catch (e: Exception) {
                onResult(e.message)
            }
        }
    }
}
