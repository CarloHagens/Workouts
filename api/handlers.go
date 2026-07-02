package main

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
)

type Handler struct {
	store *Store
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func (h *Handler) ListExercises(w http.ResponseWriter, r *http.Request) {
	category := r.URL.Query().Get("category")
	muscleGroup := r.URL.Query().Get("muscle_group")

	exercises, err := h.store.ListExercises(r.Context(), category, muscleGroup)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if exercises == nil {
		exercises = []Exercise{}
	}
	writeJSON(w, http.StatusOK, exercises)
}

func (h *Handler) ListPrograms(w http.ResponseWriter, r *http.Request) {
	programs, err := h.store.ListPrograms(r.Context(), userID(r))
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if programs == nil {
		programs = []Program{}
	}
	writeJSON(w, http.StatusOK, programs)
}

func (h *Handler) CreateProgram(w http.ResponseWriter, r *http.Request) {
	var req CreateProgramRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if req.Name == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}

	program, err := h.store.CreateProgram(r.Context(), userID(r), req.Name)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, program)
}

func (h *Handler) ReorderPrograms(w http.ResponseWriter, r *http.Request) {
	var req ReorderProgramsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if err := h.store.ReorderPrograms(r.Context(), userID(r), req.IDs); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) GetProgram(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}

	program, err := h.store.GetProgram(r.Context(), userID(r), id)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "program not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, program)
}

func (h *Handler) DeleteProgram(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}

	if err := h.store.DeleteProgram(r.Context(), userID(r), id); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) RenameProgram(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}

	var req CreateProgramRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if req.Name == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}

	program, err := h.store.RenameProgram(r.Context(), userID(r), id, req.Name)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "program not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, program)
}

func (h *Handler) AddProgramExercise(w http.ResponseWriter, r *http.Request) {
	programID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid program id")
		return
	}

	var req AddExerciseRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if req.ExerciseID == 0 {
		writeError(w, http.StatusBadRequest, "exercise_id is required")
		return
	}

	pe, err := h.store.AddProgramExercise(r.Context(), userID(r), programID, req.ExerciseID, req.SortOrder)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "program not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, pe)
}

func (h *Handler) RemoveProgramExercise(w http.ResponseWriter, r *http.Request) {
	programID, err := strconv.ParseInt(chi.URLParam(r, "programID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid program id")
		return
	}
	exerciseID, err := strconv.ParseInt(chi.URLParam(r, "exerciseID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid exercise id")
		return
	}

	if err := h.store.RemoveProgramExercise(r.Context(), userID(r), programID, exerciseID); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// Exercise settings

func (h *Handler) GetExerciseSettings(w http.ResponseWriter, r *http.Request) {
	programID, err := strconv.ParseInt(chi.URLParam(r, "programID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid program id")
		return
	}
	exerciseID, err := strconv.ParseInt(chi.URLParam(r, "exerciseID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid exercise id")
		return
	}

	es, err := h.store.GetExerciseSettings(r.Context(), userID(r), programID, exerciseID)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "no settings configured")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, es)
}

func (h *Handler) UpsertExerciseSettings(w http.ResponseWriter, r *http.Request) {
	programID, err := strconv.ParseInt(chi.URLParam(r, "programID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid program id")
		return
	}
	exerciseID, err := strconv.ParseInt(chi.URLParam(r, "exerciseID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid exercise id")
		return
	}

	var req UpsertExerciseSettingsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	es, err := h.store.UpsertExerciseSettings(r.Context(), userID(r), programID, exerciseID, req)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "program not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, es)
}

// Workouts

func (h *Handler) SubmitWorkout(w http.ResponseWriter, r *http.Request) {
	var req SubmitWorkoutRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if req.ProgramID == 0 {
		writeError(w, http.StatusBadRequest, "program_id is required")
		return
	}

	workout, err := h.store.SubmitWorkout(r.Context(), userID(r), req)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "program not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, workout)
}

func (h *Handler) ListWorkouts(w http.ResponseWriter, r *http.Request) {
	workouts, err := h.store.ListWorkouts(r.Context(), userID(r))
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if workouts == nil {
		workouts = []Workout{}
	}
	writeJSON(w, http.StatusOK, workouts)
}

func (h *Handler) GetWorkout(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}

	workout, err := h.store.GetWorkout(r.Context(), userID(r), id)
	if err != nil {
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "workout not found")
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, workout)
}

func (h *Handler) GetExercisesWithHistory(w http.ResponseWriter, r *http.Request) {
	exercises, err := h.store.GetExercisesWithHistory(r.Context(), userID(r))
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if exercises == nil {
		exercises = []Exercise{}
	}
	writeJSON(w, http.StatusOK, exercises)
}

func (h *Handler) GetExerciseProgress(w http.ResponseWriter, r *http.Request) {
	exerciseID, err := strconv.ParseInt(chi.URLParam(r, "exerciseID"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid exercise id")
		return
	}
	points, err := h.store.GetExerciseProgress(r.Context(), userID(r), exerciseID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if points == nil {
		points = []ProgressPoint{}
	}
	writeJSON(w, http.StatusOK, points)
}

func (h *Handler) GetBodyWeightProgress(w http.ResponseWriter, r *http.Request) {
	points, err := h.store.GetBodyWeightProgress(r.Context(), userID(r))
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if points == nil {
		points = []ProgressPoint{}
	}
	writeJSON(w, http.StatusOK, points)
}

func (h *Handler) ImportWorkouts(w http.ResponseWriter, r *http.Request) {
	var req ImportWorkoutRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	count, err := h.store.ImportWorkouts(r.Context(), userID(r), req)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, map[string]int{"imported": count})
}

func (h *Handler) GetLastWorkoutDate(w http.ResponseWriter, r *http.Request) {
	programID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}
	lastDate, err := h.store.GetLastWorkoutDate(r.Context(), userID(r), programID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, LastWorkoutResponse{ProgramID: programID, LastDate: lastDate})
}

func (h *Handler) UpdateWorkoutBodyWeight(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}
	var req UpdateBodyWeightRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if err := h.store.UpdateWorkoutBodyWeight(r.Context(), userID(r), id, req.BodyWeight); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) DeleteAllWorkouts(w http.ResponseWriter, r *http.Request) {
	if err := h.store.DeleteAllWorkouts(r.Context(), userID(r)); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) DeleteWorkout(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid id")
		return
	}

	if err := h.store.DeleteWorkout(r.Context(), userID(r), id); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
