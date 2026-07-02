package com.workouts.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// --- API models ---

data class Exercise(
    val id: Long,
    val name: String,
    val category: String,
    val muscle_group: String
)

data class Program(
    val id: Long,
    val name: String,
    val sort_order: Int = 0,
    val exercise_count: Int = 0,
    val created_at: String
)

data class ProgramDetail(
    val id: Long,
    val name: String,
    val created_at: String,
    val exercises: List<ProgramExerciseItem>
)

data class ProgramExerciseItem(
    val id: Long,
    val program_id: Long,
    val exercise_id: Long,
    val sort_order: Int,
    val exercise: Exercise,
    val settings: ExerciseSettings?
)

data class ExerciseSettings(
    val id: Long,
    val program_id: Long,
    val exercise_id: Long,
    val working_weight: Double,
    val target_reps: Int,
    val target_sets: Int,
    val increment_amount: Double,
    val deload_percent: Double,
    val success_threshold: Int,
    val failure_threshold: Int,
    val consecutive_successes: Int,
    val consecutive_failures: Int
)

data class CreateProgramRequest(val name: String)

data class ReorderProgramsRequest(val ids: List<Long>)

data class AddExerciseRequest(
    val exercise_id: Long,
    val sort_order: Int = 0
)

data class ProgressPoint(val date: String, val weight: Double)

data class LastWorkoutResponse(val program_id: Long, val last_date: String?)

data class UpdateBodyWeightRequest(val body_weight: Double)

data class ImportWorkoutRequest(val workouts: List<ImportWorkout>)

data class ImportWorkout(
    val program_name: String,
    val started_at: String,
    val finished_at: String,
    val duration_seconds: Int,
    val body_weight: Double?,
    val exercises: List<ImportExercise>
)

data class ImportExercise(
    val exercise_name: String,
    val sets: List<WorkoutSetInput>
)

data class ImportResponse(val imported: Int)

data class GoogleLinkStatus(
    val linked: Boolean,
    val email: String?,
    val restored: Boolean?
)

data class LinkGoogleRequest(val id_token: String)

data class UpsertExerciseSettingsRequest(
    val working_weight: Double,
    val target_reps: Int,
    val target_sets: Int,
    val increment_amount: Double,
    val deload_percent: Double,
    val success_threshold: Int,
    val failure_threshold: Int
)

data class SubmitWorkoutRequest(
    val program_id: Long,
    val started_at: String,
    val finished_at: String,
    val duration_seconds: Int,
    val body_weight: Double?,
    val sets: List<WorkoutSetInput>
)

data class WorkoutSetInput(
    val exercise_id: Long,
    val set_order: Int,
    val reps: Int,
    val weight: Double
)

data class WorkoutSummary(
    val id: Long,
    val program_id: Long,
    val program_name: String,
    val started_at: String,
    val finished_at: String,
    val duration_seconds: Int,
    val body_weight: Double?,
    val created_at: String
)

data class WorkoutDetailResponse(
    val id: Long,
    val program_id: Long,
    val program_name: String,
    val started_at: String,
    val finished_at: String,
    val duration_seconds: Int,
    val body_weight: Double?,
    val sets: List<WorkoutSetDetail>
)

data class WorkoutSetDetail(
    val id: Long,
    val workout_id: Long,
    val exercise_id: Long,
    val exercise_name: String,
    val set_order: Int,
    val reps: Int,
    val weight: Double
)

// --- Room entities (local persistence for active workout) ---

@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(
    @PrimaryKey val id: Int = 1,
    val programId: Long,
    val programName: String,
    val startedAt: Long,
    val bodyWeight: Double? = null,
    val stoppedAt: Long? = null
)

@Entity(tableName = "active_sets")
data class ActiveSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val setOrder: Int,
    val reps: Int,
    val weight: Double,
    val isWarmup: Boolean = false
)

@Entity(tableName = "active_exercises")
data class ActiveExerciseEntity(
    @PrimaryKey val exerciseId: Long,
    val name: String,
    val category: String,
    val muscleGroup: String,
    val sortOrder: Int,
    val workingWeight: Double = 0.0,
    val targetReps: Int = 10,
    val targetSets: Int = 3,
    val incrementAmount: Double = 2.5,
    val deloadPercent: Double = 10.0,
    val successThreshold: Int = 3,
    val failureThreshold: Int = 2
)

// --- Local warm-up model (not persisted) ---

data class WarmupSet(
    val weight: Double,
    val reps: Int
)
