package main

import (
	"context"
	"database/sql"
	_ "embed"
	"fmt"
	"math"
	"time"
)

//go:embed migrations/001_initial.sql
var migration001 string

//go:embed migrations/002_exercise_settings.sql
var migration002 string

//go:embed migrations/003_body_weight.sql
var migration003 string

//go:embed migrations/004_deload_percent.sql
var migration004 string

//go:embed migrations/005_program_sort.sql
var migration005 string

//go:embed migrations/006_users.sql
var migration006 string

type migration struct {
	name string
	sql  string
}

var migrations = []migration{
	{"001_initial", migration001},
	{"002_exercise_settings", migration002},
	{"003_body_weight", migration003},
	{"004_deload_percent", migration004},
	{"005_program_sort", migration005},
	{"006_users", migration006},
}

type Store struct {
	db *sql.DB
}

func (s *Store) Migrate(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			name TEXT PRIMARY KEY,
			applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
		)
	`)
	if err != nil {
		return fmt.Errorf("creating migrations table: %w", err)
	}

	for _, m := range migrations {
		var exists bool
		s.db.QueryRowContext(ctx,
			"SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE name = $1)", m.name).Scan(&exists)
		if exists {
			continue
		}

		if _, err := s.db.ExecContext(ctx, m.sql); err != nil {
			return fmt.Errorf("migration %s: %w", m.name, err)
		}
		if _, err := s.db.ExecContext(ctx,
			"INSERT INTO schema_migrations (name) VALUES ($1)", m.name); err != nil {
			return fmt.Errorf("recording migration %s: %w", m.name, err)
		}
		fmt.Printf("applied migration: %s\n", m.name)
	}
	return nil
}

func (s *Store) SeedExercises(ctx context.Context) error {
	for _, e := range seedExercises {
		_, err := s.db.ExecContext(ctx,
			`INSERT INTO exercises (name, category, muscle_group) VALUES ($1, $2, $3) ON CONFLICT (name) DO NOTHING`,
			e.Name, e.Category, e.MuscleGroup)
		if err != nil {
			return fmt.Errorf("seeding %s: %w", e.Name, err)
		}
	}
	return nil
}

// Users

func (s *Store) GetOrCreateUserByToken(ctx context.Context, token string) (int64, error) {
	var uid int64
	err := s.db.QueryRowContext(ctx,
		"UPDATE device_tokens SET last_seen = NOW() WHERE token = $1 RETURNING user_id", token).Scan(&uid)
	if err == nil {
		return uid, nil
	}
	if err != sql.ErrNoRows {
		return 0, err
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()
	if err := tx.QueryRowContext(ctx, "INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&uid); err != nil {
		return 0, err
	}
	if _, err := tx.ExecContext(ctx,
		"INSERT INTO device_tokens (token, user_id, last_seen) VALUES ($1, $2, NOW())", token, uid); err != nil {
		return 0, err
	}
	return uid, tx.Commit()
}

// ownsProgram returns sql.ErrNoRows if the program doesn't exist or belongs to another user.
func ownsProgram(ctx context.Context, q interface {
	QueryRowContext(ctx context.Context, query string, args ...any) *sql.Row
}, userID, programID int64) error {
	var ok bool
	err := q.QueryRowContext(ctx,
		"SELECT EXISTS(SELECT 1 FROM programs WHERE id = $1 AND user_id = $2)", programID, userID).Scan(&ok)
	if err != nil {
		return err
	}
	if !ok {
		return sql.ErrNoRows
	}
	return nil
}

func (s *Store) ListExercises(ctx context.Context, category, muscleGroup string) ([]Exercise, error) {
	query := "SELECT id, name, category, muscle_group FROM exercises WHERE 1=1"
	args := []any{}
	n := 0
	if category != "" {
		n++
		query += fmt.Sprintf(" AND category = $%d", n)
		args = append(args, category)
	}
	if muscleGroup != "" {
		n++
		query += fmt.Sprintf(" AND muscle_group = $%d", n)
		args = append(args, muscleGroup)
	}
	query += " ORDER BY muscle_group, name"

	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var exercises []Exercise
	for rows.Next() {
		var e Exercise
		if err := rows.Scan(&e.ID, &e.Name, &e.Category, &e.MuscleGroup); err != nil {
			return nil, err
		}
		exercises = append(exercises, e)
	}
	return exercises, rows.Err()
}

func (s *Store) CreateProgram(ctx context.Context, userID int64, name string) (*Program, error) {
	var p Program
	err := s.db.QueryRowContext(ctx,
		"INSERT INTO programs (name, user_id) VALUES ($1, $2) RETURNING id, name, sort_order, created_at",
		name, userID).Scan(&p.ID, &p.Name, &p.SortOrder, &p.CreatedAt)
	p.ExerciseCount = 0
	return &p, err
}

func (s *Store) RenameProgram(ctx context.Context, userID, id int64, name string) (*Program, error) {
	var p Program
	err := s.db.QueryRowContext(ctx,
		"UPDATE programs SET name = $1 WHERE id = $2 AND user_id = $3 RETURNING id, name, sort_order, created_at",
		name, id, userID).Scan(&p.ID, &p.Name, &p.SortOrder, &p.CreatedAt)
	// Count exercises
	s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM program_exercises WHERE program_id = $1", id).Scan(&p.ExerciseCount)
	return &p, err
}

func (s *Store) ReorderPrograms(ctx context.Context, userID int64, ids []int64) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	for i, id := range ids {
		if _, err := tx.ExecContext(ctx, "UPDATE programs SET sort_order = $1 WHERE id = $2 AND user_id = $3", i, id, userID); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) ListPrograms(ctx context.Context, userID int64) ([]Program, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT p.id, p.name, p.sort_order, COUNT(pe.id), p.created_at
		 FROM programs p LEFT JOIN program_exercises pe ON pe.program_id = p.id
		 WHERE p.user_id = $1
		 GROUP BY p.id ORDER BY p.sort_order, p.created_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var programs []Program
	for rows.Next() {
		var p Program
		if err := rows.Scan(&p.ID, &p.Name, &p.SortOrder, &p.ExerciseCount, &p.CreatedAt); err != nil {
			return nil, err
		}
		programs = append(programs, p)
	}
	return programs, rows.Err()
}

func (s *Store) GetProgram(ctx context.Context, userID, id int64) (*ProgramDetail, error) {
	var pd ProgramDetail
	err := s.db.QueryRowContext(ctx,
		"SELECT id, name, created_at FROM programs WHERE id = $1 AND user_id = $2", id, userID).
		Scan(&pd.ID, &pd.Name, &pd.CreatedAt)
	if err != nil {
		return nil, err
	}

	rows, err := s.db.QueryContext(ctx,
		`SELECT pe.id, pe.program_id, pe.exercise_id, pe.sort_order,
		        e.id, e.name, e.category, e.muscle_group
		 FROM program_exercises pe
		 JOIN exercises e ON e.id = pe.exercise_id
		 WHERE pe.program_id = $1
		 ORDER BY pe.sort_order`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var pe ProgramExercise
		if err := rows.Scan(
			&pe.ID, &pe.ProgramID, &pe.ExerciseID, &pe.SortOrder,
			&pe.Exercise.ID, &pe.Exercise.Name, &pe.Exercise.Category, &pe.Exercise.MuscleGroup,
		); err != nil {
			return nil, err
		}
		pd.Exercises = append(pd.Exercises, pe)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if pd.Exercises == nil {
		pd.Exercises = []ProgramExercise{}
	}

	// Fetch exercise settings for this program
	settingsMap := make(map[int64]*ExerciseSettings)
	sRows, err := s.db.QueryContext(ctx,
		`SELECT id, program_id, exercise_id, working_weight, target_reps, target_sets,
		        increment_amount, deload_percent, success_threshold, failure_threshold,
		        consecutive_successes, consecutive_failures
		 FROM exercise_settings WHERE program_id = $1`, id)
	if err == nil {
		defer sRows.Close()
		for sRows.Next() {
			var es ExerciseSettings
			if err := sRows.Scan(&es.ID, &es.ProgramID, &es.ExerciseID, &es.WorkingWeight,
				&es.TargetReps, &es.TargetSets, &es.IncrementAmount, &es.DeloadPercent,
				&es.SuccessThreshold, &es.FailureThreshold,
				&es.ConsecutiveSuccesses, &es.ConsecutiveFailures); err == nil {
				settingsMap[es.ExerciseID] = &es
			}
		}
	}
	for i := range pd.Exercises {
		pd.Exercises[i].Settings = settingsMap[pd.Exercises[i].ExerciseID]
	}

	return &pd, nil
}

func (s *Store) DeleteProgram(ctx context.Context, userID, id int64) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM programs WHERE id = $1 AND user_id = $2", id, userID)
	return err
}

func (s *Store) AddProgramExercise(ctx context.Context, userID, programID, exerciseID int64, sortOrder int) (*ProgramExercise, error) {
	if err := ownsProgram(ctx, s.db, userID, programID); err != nil {
		return nil, err
	}
	if sortOrder == 0 {
		var maxOrder sql.NullInt64
		s.db.QueryRowContext(ctx,
			"SELECT MAX(sort_order) FROM program_exercises WHERE program_id = $1", programID).
			Scan(&maxOrder)
		if maxOrder.Valid {
			sortOrder = int(maxOrder.Int64) + 1
		} else {
			sortOrder = 1
		}
	}

	var pe ProgramExercise
	err := s.db.QueryRowContext(ctx,
		`INSERT INTO program_exercises (program_id, exercise_id, sort_order)
		 VALUES ($1, $2, $3) RETURNING id, program_id, exercise_id, sort_order`,
		programID, exerciseID, sortOrder).
		Scan(&pe.ID, &pe.ProgramID, &pe.ExerciseID, &pe.SortOrder)
	if err != nil {
		return nil, err
	}

	s.db.QueryRowContext(ctx,
		"SELECT id, name, category, muscle_group FROM exercises WHERE id = $1", exerciseID).
		Scan(&pe.Exercise.ID, &pe.Exercise.Name, &pe.Exercise.Category, &pe.Exercise.MuscleGroup)

	return &pe, nil
}

func (s *Store) RemoveProgramExercise(ctx context.Context, userID, programID, exerciseID int64) error {
	_, err := s.db.ExecContext(ctx,
		`DELETE FROM program_exercises pe USING programs p
		 WHERE p.id = pe.program_id AND pe.program_id = $1 AND pe.exercise_id = $2 AND p.user_id = $3`,
		programID, exerciseID, userID)
	return err
}

// Exercise settings

func (s *Store) UpsertExerciseSettings(ctx context.Context, userID, programID, exerciseID int64, req UpsertExerciseSettingsRequest) (*ExerciseSettings, error) {
	if err := ownsProgram(ctx, s.db, userID, programID); err != nil {
		return nil, err
	}
	var es ExerciseSettings
	err := s.db.QueryRowContext(ctx,
		`INSERT INTO exercise_settings (program_id, exercise_id, working_weight, target_reps, target_sets,
		    increment_amount, deload_percent, success_threshold, failure_threshold)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		 ON CONFLICT (program_id, exercise_id) DO UPDATE SET
		    working_weight = EXCLUDED.working_weight,
		    target_reps = EXCLUDED.target_reps,
		    target_sets = EXCLUDED.target_sets,
		    increment_amount = EXCLUDED.increment_amount,
		    deload_percent = EXCLUDED.deload_percent,
		    success_threshold = EXCLUDED.success_threshold,
		    failure_threshold = EXCLUDED.failure_threshold,
		    updated_at = NOW()
		 RETURNING id, program_id, exercise_id, working_weight, target_reps, target_sets,
		    increment_amount, deload_percent, success_threshold, failure_threshold,
		    consecutive_successes, consecutive_failures`,
		programID, exerciseID, req.WorkingWeight, req.TargetReps, req.TargetSets,
		req.IncrementAmount, req.DeloadPercent, req.SuccessThreshold, req.FailureThreshold).
		Scan(&es.ID, &es.ProgramID, &es.ExerciseID, &es.WorkingWeight,
			&es.TargetReps, &es.TargetSets, &es.IncrementAmount, &es.DeloadPercent,
			&es.SuccessThreshold, &es.FailureThreshold,
			&es.ConsecutiveSuccesses, &es.ConsecutiveFailures)
	return &es, err
}

func (s *Store) GetExerciseSettings(ctx context.Context, userID, programID, exerciseID int64) (*ExerciseSettings, error) {
	var es ExerciseSettings
	err := s.db.QueryRowContext(ctx,
		`SELECT es.id, es.program_id, es.exercise_id, es.working_weight, es.target_reps, es.target_sets,
		        es.increment_amount, es.deload_percent, es.success_threshold, es.failure_threshold,
		        es.consecutive_successes, es.consecutive_failures
		 FROM exercise_settings es
		 JOIN programs p ON p.id = es.program_id
		 WHERE es.program_id = $1 AND es.exercise_id = $2 AND p.user_id = $3`,
		programID, exerciseID, userID).
		Scan(&es.ID, &es.ProgramID, &es.ExerciseID, &es.WorkingWeight,
			&es.TargetReps, &es.TargetSets, &es.IncrementAmount, &es.DeloadPercent,
			&es.SuccessThreshold, &es.FailureThreshold,
			&es.ConsecutiveSuccesses, &es.ConsecutiveFailures)
	if err != nil {
		return nil, err
	}
	return &es, nil
}

// Workouts

func (s *Store) SubmitWorkout(ctx context.Context, userID int64, req SubmitWorkoutRequest) (*WorkoutDetail, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	if err := ownsProgram(ctx, tx, userID, req.ProgramID); err != nil {
		return nil, err
	}

	var wd WorkoutDetail
	err = tx.QueryRowContext(ctx,
		`INSERT INTO workouts (program_id, user_id, started_at, finished_at, duration_seconds, body_weight)
		 VALUES ($1, $2, $3, $4, $5, $6) RETURNING id, program_id, started_at, finished_at, duration_seconds, body_weight, created_at`,
		req.ProgramID, userID, req.StartedAt, req.FinishedAt, req.DurationSeconds, req.BodyWeight).
		Scan(&wd.ID, &wd.ProgramID, &wd.StartedAt, &wd.FinishedAt, &wd.DurationSeconds, &wd.BodyWeight, &wd.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("inserting workout: %w", err)
	}

	tx.QueryRowContext(ctx, "SELECT name FROM programs WHERE id = $1", req.ProgramID).
		Scan(&wd.ProgramName)

	// Insert sets and group by exercise for progression
	exerciseSets := make(map[int64][]WorkoutSetInput)
	for _, set := range req.Sets {
		var ws WorkoutSet
		err = tx.QueryRowContext(ctx,
			`INSERT INTO workout_sets (workout_id, exercise_id, set_order, reps, weight)
			 VALUES ($1, $2, $3, $4, $5) RETURNING id, workout_id, exercise_id, set_order, reps, weight`,
			wd.ID, set.ExerciseID, set.SetOrder, set.Reps, set.Weight).
			Scan(&ws.ID, &ws.WorkoutID, &ws.ExerciseID, &ws.SetOrder, &ws.Reps, &ws.Weight)
		if err != nil {
			return nil, fmt.Errorf("inserting set: %w", err)
		}

		tx.QueryRowContext(ctx, "SELECT name FROM exercises WHERE id = $1", set.ExerciseID).
			Scan(&ws.ExerciseName)

		wd.Sets = append(wd.Sets, ws)
		exerciseSets[set.ExerciseID] = append(exerciseSets[set.ExerciseID], set)
	}
	if wd.Sets == nil {
		wd.Sets = []WorkoutSet{}
	}

	// Compute progression for each exercise
	for exerciseID, sets := range exerciseSets {
		var es ExerciseSettings
		err := tx.QueryRowContext(ctx,
			`SELECT id, program_id, exercise_id, working_weight, target_reps, target_sets,
			        increment_amount, deload_percent, success_threshold, failure_threshold,
			        consecutive_successes, consecutive_failures
			 FROM exercise_settings WHERE program_id = $1 AND exercise_id = $2`,
			req.ProgramID, exerciseID).
			Scan(&es.ID, &es.ProgramID, &es.ExerciseID, &es.WorkingWeight,
				&es.TargetReps, &es.TargetSets, &es.IncrementAmount, &es.DeloadPercent,
				&es.SuccessThreshold, &es.FailureThreshold,
				&es.ConsecutiveSuccesses, &es.ConsecutiveFailures)
		if err != nil {
			continue // No settings configured, skip progression
		}

		// Determine success: enough sets AND all reps met target
		success := len(sets) >= es.TargetSets
		if success {
			for _, set := range sets {
				if set.Reps < es.TargetReps {
					success = false
					break
				}
			}
		}

		newSuccesses := es.ConsecutiveSuccesses
		newFailures := es.ConsecutiveFailures
		newWeight := es.WorkingWeight

		if success {
			newSuccesses++
			newFailures = 0
			if newSuccesses >= es.SuccessThreshold {
				newWeight += es.IncrementAmount
				newSuccesses = 0
			}
		} else {
			newFailures++
			newSuccesses = 0
			if newFailures >= es.FailureThreshold {
				newWeight = newWeight * (1 - es.DeloadPercent/100)
				// Round to nearest 2.5kg
				newWeight = math.Round(newWeight/2.5) * 2.5
				if newWeight < 0 {
					newWeight = 0
				}
				newFailures = 0
			}
		}

		// Update this program's settings
		tx.ExecContext(ctx,
			`UPDATE exercise_settings SET
			    consecutive_successes = $1, consecutive_failures = $2,
			    working_weight = $3, updated_at = NOW()
			 WHERE program_id = $4 AND exercise_id = $5`,
			newSuccesses, newFailures, newWeight, req.ProgramID, exerciseID)

		// Sync weight change to same exercise in the user's other programs
		if newWeight != es.WorkingWeight {
			tx.ExecContext(ctx,
				`UPDATE exercise_settings SET working_weight = $1, updated_at = NOW()
				 WHERE exercise_id = $2 AND program_id != $3
				   AND program_id IN (SELECT id FROM programs WHERE user_id = $4)`,
				newWeight, exerciseID, req.ProgramID, userID)
		}
	}

	return &wd, tx.Commit()
}

func (s *Store) ListWorkouts(ctx context.Context, userID int64) ([]Workout, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT w.id, w.program_id, p.name, w.started_at, w.finished_at, w.duration_seconds, w.body_weight, w.created_at
		 FROM workouts w
		 JOIN programs p ON p.id = w.program_id
		 WHERE w.user_id = $1
		 ORDER BY w.started_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var workouts []Workout
	for rows.Next() {
		var w Workout
		if err := rows.Scan(&w.ID, &w.ProgramID, &w.ProgramName, &w.StartedAt, &w.FinishedAt, &w.DurationSeconds, &w.BodyWeight, &w.CreatedAt); err != nil {
			return nil, err
		}
		workouts = append(workouts, w)
	}
	return workouts, rows.Err()
}

func (s *Store) GetWorkout(ctx context.Context, userID, id int64) (*WorkoutDetail, error) {
	var wd WorkoutDetail
	err := s.db.QueryRowContext(ctx,
		`SELECT w.id, w.program_id, p.name, w.started_at, w.finished_at, w.duration_seconds, w.body_weight, w.created_at
		 FROM workouts w
		 JOIN programs p ON p.id = w.program_id
		 WHERE w.id = $1 AND w.user_id = $2`, id, userID).
		Scan(&wd.ID, &wd.ProgramID, &wd.ProgramName, &wd.StartedAt, &wd.FinishedAt, &wd.DurationSeconds, &wd.BodyWeight, &wd.CreatedAt)
	if err != nil {
		return nil, err
	}

	rows, err := s.db.QueryContext(ctx,
		`SELECT ws.id, ws.workout_id, ws.exercise_id, e.name, ws.set_order, ws.reps, ws.weight
		 FROM workout_sets ws
		 JOIN exercises e ON e.id = ws.exercise_id
		 WHERE ws.workout_id = $1
		 ORDER BY ws.id`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var ws WorkoutSet
		if err := rows.Scan(&ws.ID, &ws.WorkoutID, &ws.ExerciseID, &ws.ExerciseName, &ws.SetOrder, &ws.Reps, &ws.Weight); err != nil {
			return nil, err
		}
		wd.Sets = append(wd.Sets, ws)
	}
	if wd.Sets == nil {
		wd.Sets = []WorkoutSet{}
	}
	return &wd, rows.Err()
}

func (s *Store) DeleteWorkout(ctx context.Context, userID, id int64) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM workouts WHERE id = $1 AND user_id = $2", id, userID)
	return err
}

func (s *Store) GetExerciseProgress(ctx context.Context, userID, exerciseID int64) ([]ProgressPoint, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT w.started_at::date::text, MAX(ws.weight)
		 FROM workout_sets ws
		 JOIN workouts w ON w.id = ws.workout_id
		 WHERE ws.exercise_id = $1 AND w.user_id = $2
		 GROUP BY w.started_at::date
		 ORDER BY w.started_at::date`, exerciseID, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var points []ProgressPoint
	for rows.Next() {
		var p ProgressPoint
		if err := rows.Scan(&p.Date, &p.Weight); err != nil {
			return nil, err
		}
		points = append(points, p)
	}
	return points, rows.Err()
}

func (s *Store) GetExercisesWithHistory(ctx context.Context, userID int64) ([]Exercise, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT DISTINCT e.id, e.name, e.category, e.muscle_group
		 FROM exercises e
		 JOIN workout_sets ws ON ws.exercise_id = e.id
		 JOIN workouts w ON w.id = ws.workout_id
		 WHERE w.user_id = $1
		 ORDER BY e.name`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var exercises []Exercise
	for rows.Next() {
		var e Exercise
		if err := rows.Scan(&e.ID, &e.Name, &e.Category, &e.MuscleGroup); err != nil {
			return nil, err
		}
		exercises = append(exercises, e)
	}
	return exercises, rows.Err()
}

func (s *Store) GetBodyWeightProgress(ctx context.Context, userID int64) ([]ProgressPoint, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT started_at::date::text, body_weight
		 FROM workouts
		 WHERE body_weight IS NOT NULL AND user_id = $1
		 ORDER BY started_at::date`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var points []ProgressPoint
	for rows.Next() {
		var p ProgressPoint
		if err := rows.Scan(&p.Date, &p.Weight); err != nil {
			return nil, err
		}
		points = append(points, p)
	}
	return points, rows.Err()
}

func (s *Store) GetLastWorkoutDate(ctx context.Context, userID, programID int64) (*time.Time, error) {
	var lastDate time.Time
	err := s.db.QueryRowContext(ctx,
		"SELECT MAX(started_at) FROM workouts WHERE program_id = $1 AND user_id = $2", programID, userID).Scan(&lastDate)
	if err != nil {
		return nil, nil
	}
	if lastDate.IsZero() {
		return nil, nil
	}
	return &lastDate, nil
}

func (s *Store) UpdateWorkoutBodyWeight(ctx context.Context, userID, id int64, bodyWeight float64) error {
	_, err := s.db.ExecContext(ctx, "UPDATE workouts SET body_weight = $1 WHERE id = $2 AND user_id = $3", bodyWeight, id, userID)
	return err
}

func (s *Store) DeleteAllWorkouts(ctx context.Context, userID int64) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM workouts WHERE user_id = $1", userID)
	return err
}

func (s *Store) ImportWorkouts(ctx context.Context, userID int64, req ImportWorkoutRequest) (int, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()

	imported := 0
	for _, w := range req.Workouts {
		// Find or create program
		var programID int64
		err := tx.QueryRowContext(ctx,
			"SELECT id FROM programs WHERE name = $1 AND user_id = $2", w.ProgramName, userID).Scan(&programID)
		if err != nil {
			tx.QueryRowContext(ctx,
				"INSERT INTO programs (name, user_id) VALUES ($1, $2) RETURNING id", w.ProgramName, userID).Scan(&programID)
		}

		// Insert workout
		var workoutID int64
		err = tx.QueryRowContext(ctx,
			`INSERT INTO workouts (program_id, user_id, started_at, finished_at, duration_seconds, body_weight)
			 VALUES ($1, $2, $3, $4, $5, $6) RETURNING id`,
			programID, userID, w.StartedAt, w.FinishedAt, w.DurationSeconds, w.BodyWeight).Scan(&workoutID)
		if err != nil {
			return 0, fmt.Errorf("inserting workout: %w", err)
		}

		// Insert sets for each exercise
		for _, ex := range w.Exercises {
			// Look up exercise ID by name — try exact match first, then contains
			var exerciseID int64
			err := tx.QueryRowContext(ctx,
				"SELECT id FROM exercises WHERE LOWER(name) = LOWER($1)", ex.ExerciseName).Scan(&exerciseID)
			if err != nil {
				// Try partial match: prefer "Barbell X" over "Hack X" by prioritizing names ending with the search term
				err = tx.QueryRowContext(ctx,
					`SELECT id FROM exercises WHERE LOWER(name) LIKE '%' || LOWER($1) || '%'
					 ORDER BY CASE WHEN LOWER(name) LIKE 'barbell %' THEN 0 ELSE 1 END, LENGTH(name) LIMIT 1`,
					ex.ExerciseName).Scan(&exerciseID)
			}
			if err != nil {
				continue // Skip unknown exercises
			}

			for _, set := range ex.Sets {
				tx.ExecContext(ctx,
					`INSERT INTO workout_sets (workout_id, exercise_id, set_order, reps, weight)
					 VALUES ($1, $2, $3, $4, $5)`,
					workoutID, exerciseID, set.SetOrder, set.Reps, set.Weight)
			}
		}
		imported++
	}

	return imported, tx.Commit()
}
