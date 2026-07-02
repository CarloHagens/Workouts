package com.workouts.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

class FitnessRepository(
    private val api: ApiService,
    private val db: AppDatabase
) {
    private val dao = db.activeWorkoutDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Google account link
    suspend fun getGoogleLink(): GoogleLinkStatus = api.getGoogleLink()
    suspend fun linkGoogle(idToken: String): GoogleLinkStatus = api.linkGoogle(LinkGoogleRequest(idToken))
    suspend fun unlinkGoogle() = api.unlinkGoogle()

    // Exercises
    suspend fun getExercises(category: String? = null, muscleGroup: String? = null): List<Exercise> {
        return api.getExercises(category, muscleGroup)
    }

    // Programs
    suspend fun getPrograms(): List<Program> = api.getPrograms()
    suspend fun createProgram(name: String): Program = api.createProgram(CreateProgramRequest(name))
    suspend fun getProgram(id: Long): ProgramDetail = api.getProgram(id)
    suspend fun renameProgram(id: Long, name: String) = api.renameProgram(id, CreateProgramRequest(name))
    suspend fun reorderPrograms(ids: List<Long>) = api.reorderPrograms(ReorderProgramsRequest(ids))
    suspend fun getLastWorkoutDate(programId: Long) = api.getLastWorkoutDate(programId)
    suspend fun deleteProgram(id: Long) = api.deleteProgram(id)

    suspend fun addExercise(programId: Long, exerciseId: Long) =
        api.addExercise(programId, AddExerciseRequest(exerciseId))

    suspend fun removeExercise(programId: Long, exerciseId: Long) =
        api.removeExercise(programId, exerciseId)

    // Exercise settings
    suspend fun upsertExerciseSettings(
        programId: Long,
        exerciseId: Long,
        request: UpsertExerciseSettingsRequest
    ): ExerciseSettings = api.upsertExerciseSettings(programId, exerciseId, request)

    // Active workout (local persistence)
    suspend fun startWorkout(programId: Long, programName: String, exercises: List<ProgramExerciseItem>) {
        dao.clearAll()
        dao.insertWorkout(
            ActiveWorkoutEntity(
                programId = programId,
                programName = programName,
                startedAt = System.currentTimeMillis()
            )
        )
        dao.insertExercises(exercises.map {
            ActiveExerciseEntity(
                exerciseId = it.exercise.id,
                name = it.exercise.name,
                category = it.exercise.category,
                muscleGroup = it.exercise.muscle_group,
                sortOrder = it.sort_order,
                workingWeight = it.settings?.working_weight ?: 0.0,
                targetReps = it.settings?.target_reps ?: 10,
                targetSets = it.settings?.target_sets ?: 3,
                incrementAmount = it.settings?.increment_amount ?: 2.5,
                deloadPercent = it.settings?.deload_percent ?: 10.0,
                successThreshold = it.settings?.success_threshold ?: 3,
                failureThreshold = it.settings?.failure_threshold ?: 2
            )
        })
    }

    suspend fun getActiveWorkout(): ActiveWorkoutEntity? = dao.getActiveWorkout()
    suspend fun getActiveExercises(): List<ActiveExerciseEntity> = dao.getExercises()
    suspend fun getWorkingSets(): List<ActiveSetEntity> = dao.getWorkingSets()
    suspend fun getCompletedWarmups(): List<ActiveSetEntity> = dao.getWarmupSets()

    suspend fun updateBodyWeight(weight: Double?) = dao.updateBodyWeight(weight)
    suspend fun updateStoppedAt(stoppedAt: Long?) = dao.updateStoppedAt(stoppedAt)

    suspend fun logSet(exerciseId: Long, exerciseName: String, setOrder: Int, reps: Int, weight: Double, isWarmup: Boolean = false): Long {
        return dao.insertSet(
            ActiveSetEntity(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                setOrder = setOrder,
                reps = reps,
                weight = weight,
                isWarmup = isWarmup
            )
        )
    }

    suspend fun decrementSetReps(id: Long) {
        val sets = dao.getAllSets()
        val set = sets.find { it.id == id } ?: return
        val newReps = set.reps - 1
        if (newReps > 0) {
            dao.updateSetReps(id, newReps)
        } else {
            dao.deleteSet(id)
        }
    }

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun finishWorkout(stoppedAt: Long? = null): WorkoutDetailResponse {
        val workout = dao.getActiveWorkout() ?: throw IllegalStateException("No active workout")
        val exercises = dao.getExercises()
        val exerciseOrder = exercises.associate { it.exerciseId to it.sortOrder }
        val workingSets = dao.getWorkingSets().sortedWith(
            compareBy<ActiveSetEntity> { exerciseOrder[it.exerciseId] ?: 0 }.thenBy { it.setOrder }
        )
        val finishTime = stoppedAt ?: System.currentTimeMillis()
        val durationSeconds = ((finishTime - workout.startedAt) / 1000).toInt()

        val request = SubmitWorkoutRequest(
            program_id = workout.programId,
            started_at = dateFormat.format(Date(workout.startedAt)),
            finished_at = dateFormat.format(Date(finishTime)),
            duration_seconds = durationSeconds,
            body_weight = workout.bodyWeight,
            sets = workingSets.map { set ->
                WorkoutSetInput(
                    exercise_id = set.exerciseId,
                    set_order = set.setOrder,
                    reps = set.reps,
                    weight = set.weight
                )
            }
        )

        val result = api.submitWorkout(request)
        dao.clearAll()
        return result
    }

    suspend fun cancelWorkout() = dao.clearAll()

    // Workout history
    suspend fun getWorkouts(): List<WorkoutSummary> = api.getWorkouts()
    suspend fun getWorkout(id: Long): WorkoutDetailResponse = api.getWorkout(id)
    suspend fun getExercisesWithHistory() = api.getExercisesWithHistory()
    suspend fun getBodyWeightProgress() = api.getBodyWeightProgress()
    suspend fun getExerciseProgress(exerciseId: Long) = api.getExerciseProgress(exerciseId)
    suspend fun updateWorkoutBodyWeight(id: Long, weight: Double) = api.updateWorkoutBodyWeight(id, UpdateBodyWeightRequest(weight))
    suspend fun deleteWorkout(id: Long) = api.deleteWorkout(id)
    suspend fun deleteAllWorkouts() = api.deleteAllWorkouts()
    suspend fun importWorkouts(request: ImportWorkoutRequest): ImportResponse = api.importWorkouts(request)

    companion object {
        private val floorExercises = setOf(
            "deadlift", "barbell row", "t-bar row", "romanian deadlift"
        )

        fun isFloorExercise(name: String): Boolean {
            val lower = name.lowercase()
            return floorExercises.any { lower.contains(it) }
        }

        fun generateWarmupSets(workingWeight: Double, targetReps: Int = 10, exerciseName: String = ""): List<WarmupSet> {
            val barWeight = 20.0
            val isFloor = isFloorExercise(exerciseName)
            if (workingWeight <= barWeight) return emptyList()

            val sets = mutableListOf<WarmupSet>()
            val reps5 = minOf(5, targetReps)
            val reps3 = minOf(3, targetReps)

            // Determine reps based on how close the warmup weight is to working weight
            // Reps will be assigned after all sets are built

            if (isFloor) {
                // Floor exercises: always start at 60kg, 20kg jumps
                var w = 60.0
                while (w <= workingWeight - 10) {
                    sets.add(WarmupSet(w, reps5))
                    w += 20.0
                }
                // If no warmups, add one at workingWeight - 10
                if (sets.isEmpty()) {
                    val single = roundToPlate(workingWeight - 10.0)
                    if (single >= 60.0) sets.add(WarmupSet(single, reps5))
                }
                // If gap >= 20, add a closer one
                if (sets.isNotEmpty() && workingWeight - sets.last().weight >= 20) {
                    val extra = roundToPlate(workingWeight - 10.0)
                    if (extra > sets.last().weight) sets.add(WarmupSet(extra, reps5))
                }
            } else {
                // Rack exercises: 2x empty bar
                sets.add(WarmupSet(barWeight, minOf(5, targetReps)))
                sets.add(WarmupSet(barWeight, minOf(5, targetReps)))

                // Build intermediates with 20kg jumps, 10kg near the top
                val intermediates = mutableListOf<Double>()
                val startW = if (workingWeight < 55) barWeight + 10.0 else barWeight + 20.0
                var w = startW
                while (w <= workingWeight - 10) {
                    intermediates.add(roundToPlate(w))
                    val next20 = w + 20.0
                    w += if (next20 > workingWeight - 10) 10.0 else 20.0
                }

                intermediates.forEach { weight ->
                    sets.add(WarmupSet(weight, reps5))
                }
            }

            // Remove any warmup too close (< 10kg gap)
            while (sets.isNotEmpty() && workingWeight - sets.last().weight < 10) {
                sets.removeAt(sets.lastIndex)
            }

            // Apply rep tapering from the end
            // Pattern: all sets get 5 reps, then taper the last N sets
            // For working weight > 150: taper last 4 sets: ...5, 3, 2, 1, 1
            // For working weight > 100: taper last 2 sets: ...5, 3, 3 (or just last = 3)
            // For working weight <= 100: just last set = 3
            val n = sets.size
            if (n >= 2) {
                val barSets = if (!isFloor) 2 else 0  // don't taper bar sets
                val taperStart = barSets  // first index eligible for tapering

                if (workingWeight > 150) {
                    // Taper last 4 warmup sets: 3, 2, 1, 1
                    val taperReps = listOf(3, 2, 1, 1)
                    for (i in taperReps.indices) {
                        val idx = n - taperReps.size + i
                        if (idx >= taperStart) {
                            sets[idx] = WarmupSet(sets[idx].weight, minOf(taperReps[i], targetReps))
                        }
                    }
                } else {
                    // Last warmup set gets 3 reps
                    sets[n - 1] = WarmupSet(sets[n - 1].weight, minOf(3, targetReps))
                }
            }

            return sets
        }

        /** Round to nearest 10kg for warm-up sets (no small plates), minimum bar weight */
        fun roundToPlate(weight: Double, minWeight: Double = 20.0): Double {
            val rounded = (weight / 10.0).roundToInt() * 10.0
            return max(rounded, minWeight)
        }
    }
}
