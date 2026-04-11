package main

import "time"

type Exercise struct {
	ID          int64  `json:"id"`
	Name        string `json:"name"`
	Category    string `json:"category"`
	MuscleGroup string `json:"muscle_group"`
}

type Program struct {
	ID            int64     `json:"id"`
	Name          string    `json:"name"`
	SortOrder     int       `json:"sort_order"`
	ExerciseCount int       `json:"exercise_count"`
	CreatedAt     time.Time `json:"created_at"`
}

type ReorderProgramsRequest struct {
	IDs []int64 `json:"ids"`
}

type ProgramDetail struct {
	ID        int64             `json:"id"`
	Name      string            `json:"name"`
	CreatedAt time.Time         `json:"created_at"`
	Exercises []ProgramExercise `json:"exercises"`
}

type ProgramExercise struct {
	ID         int64             `json:"id"`
	ProgramID  int64             `json:"program_id"`
	ExerciseID int64             `json:"exercise_id"`
	SortOrder  int               `json:"sort_order"`
	Exercise   Exercise          `json:"exercise"`
	Settings   *ExerciseSettings `json:"settings"`
}

type ExerciseSettings struct {
	ID                   int64   `json:"id"`
	ProgramID            int64   `json:"program_id"`
	ExerciseID           int64   `json:"exercise_id"`
	WorkingWeight        float64 `json:"working_weight"`
	TargetReps           int     `json:"target_reps"`
	TargetSets           int     `json:"target_sets"`
	IncrementAmount      float64 `json:"increment_amount"`
	DeloadPercent        float64 `json:"deload_percent"`
	SuccessThreshold     int     `json:"success_threshold"`
	FailureThreshold     int     `json:"failure_threshold"`
	ConsecutiveSuccesses int     `json:"consecutive_successes"`
	ConsecutiveFailures  int     `json:"consecutive_failures"`
}

type Workout struct {
	ID              int64     `json:"id"`
	ProgramID       int64     `json:"program_id"`
	ProgramName     string    `json:"program_name"`
	StartedAt       time.Time `json:"started_at"`
	FinishedAt      time.Time `json:"finished_at"`
	DurationSeconds int       `json:"duration_seconds"`
	BodyWeight      *float64  `json:"body_weight"`
	CreatedAt       time.Time `json:"created_at"`
}

type WorkoutDetail struct {
	Workout
	Sets []WorkoutSet `json:"sets"`
}

type WorkoutSet struct {
	ID           int64   `json:"id"`
	WorkoutID    int64   `json:"workout_id"`
	ExerciseID   int64   `json:"exercise_id"`
	ExerciseName string  `json:"exercise_name"`
	SetOrder     int     `json:"set_order"`
	Reps         int     `json:"reps"`
	Weight       float64 `json:"weight"`
}

// Request types

type CreateProgramRequest struct {
	Name string `json:"name"`
}

type AddExerciseRequest struct {
	ExerciseID int64 `json:"exercise_id"`
	SortOrder  int   `json:"sort_order"`
}

type SubmitWorkoutRequest struct {
	ProgramID       int64             `json:"program_id"`
	StartedAt       time.Time         `json:"started_at"`
	FinishedAt      time.Time         `json:"finished_at"`
	DurationSeconds int               `json:"duration_seconds"`
	BodyWeight      *float64          `json:"body_weight"`
	Sets            []WorkoutSetInput `json:"sets"`
}

type WorkoutSetInput struct {
	ExerciseID int64   `json:"exercise_id"`
	SetOrder   int     `json:"set_order"`
	Reps       int     `json:"reps"`
	Weight     float64 `json:"weight"`
}

type ImportWorkoutRequest struct {
	Workouts []ImportWorkout `json:"workouts"`
}

type ImportWorkout struct {
	ProgramName     string             `json:"program_name"`
	StartedAt       time.Time          `json:"started_at"`
	FinishedAt      time.Time          `json:"finished_at"`
	DurationSeconds int                `json:"duration_seconds"`
	BodyWeight      *float64           `json:"body_weight"`
	Exercises       []ImportExercise   `json:"exercises"`
}

type ImportExercise struct {
	ExerciseName string           `json:"exercise_name"`
	Sets         []WorkoutSetInput `json:"sets"`
}

type ProgressPoint struct {
	Date   string  `json:"date"`
	Weight float64 `json:"weight"`
}

type LastWorkoutResponse struct {
	ProgramID int64      `json:"program_id"`
	LastDate  *time.Time `json:"last_date"`
}

type UpdateBodyWeightRequest struct {
	BodyWeight float64 `json:"body_weight"`
}

type UpsertExerciseSettingsRequest struct {
	WorkingWeight    float64 `json:"working_weight"`
	TargetReps       int     `json:"target_reps"`
	TargetSets       int     `json:"target_sets"`
	IncrementAmount  float64 `json:"increment_amount"`
	DeloadPercent    float64 `json:"deload_percent"`
	SuccessThreshold int     `json:"success_threshold"`
	FailureThreshold int     `json:"failure_threshold"`
}
