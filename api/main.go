package main

import (
	"context"
	"database/sql"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	_ "github.com/jackc/pgx/v5/stdlib"
)

func main() {
	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://workouts:workouts@localhost:5432/workouts?sslmode=disable"
	}
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	db, err := sql.Open("pgx", dbURL)
	if err != nil {
		log.Fatal("failed to open db:", err)
	}
	defer db.Close()

	for i := 0; i < 30; i++ {
		if err := db.Ping(); err == nil {
			break
		}
		log.Println("waiting for database...")
		time.Sleep(time.Second)
	}
	if err := db.Ping(); err != nil {
		log.Fatal("database not reachable:", err)
	}

	store := &Store{db: db}
	ctx := context.Background()

	if err := store.Migrate(ctx); err != nil {
		log.Fatal("migration failed:", err)
	}
	log.Println("migrations applied")

	if err := store.SeedExercises(ctx); err != nil {
		log.Fatal("seed failed:", err)
	}
	log.Println("exercises seeded")

	h := &Handler{store: store}

	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"*"},
		AllowCredentials: false,
	}))

	r.Route("/api", func(r chi.Router) {
		r.Use(h.requireUser)

		r.Get("/exercises", h.ListExercises)

		r.Get("/programs", h.ListPrograms)
		r.Post("/programs", h.CreateProgram)
		r.Put("/programs/reorder", h.ReorderPrograms)
		r.Get("/programs/{id}", h.GetProgram)
		r.Get("/programs/{id}/last-workout", h.GetLastWorkoutDate)
		r.Put("/programs/{id}", h.RenameProgram)
		r.Delete("/programs/{id}", h.DeleteProgram)

		r.Post("/programs/{id}/exercises", h.AddProgramExercise)
		r.Delete("/programs/{programID}/exercises/{exerciseID}", h.RemoveProgramExercise)

		r.Get("/programs/{programID}/exercises/{exerciseID}/settings", h.GetExerciseSettings)
		r.Put("/programs/{programID}/exercises/{exerciseID}/settings", h.UpsertExerciseSettings)

		r.Get("/progress/exercises", h.GetExercisesWithHistory)
		r.Get("/progress/bodyweight", h.GetBodyWeightProgress)
		r.Get("/progress/exercise/{exerciseID}", h.GetExerciseProgress)

		r.Post("/workouts", h.SubmitWorkout)
		r.Post("/workouts/import", h.ImportWorkouts)
		r.Get("/workouts", h.ListWorkouts)
		r.Get("/workouts/{id}", h.GetWorkout)
		r.Patch("/workouts/{id}/bodyweight", h.UpdateWorkoutBodyWeight)
		r.Delete("/workouts/all", h.DeleteAllWorkouts)
		r.Delete("/workouts/{id}", h.DeleteWorkout)
	})

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})

	log.Printf("server starting on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, r))
}
