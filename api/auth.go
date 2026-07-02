package main

import (
	"context"
	"net/http"
)

type contextKey int

const userIDKey contextKey = iota

const deviceTokenHeader = "X-Device-Token"

// requireUser resolves the device token header to a user, creating the user
// on first sight, and stores the user id in the request context.
func (h *Handler) requireUser(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := r.Header.Get(deviceTokenHeader)
		if len(token) < 16 || len(token) > 128 {
			writeError(w, http.StatusUnauthorized, "missing or invalid device token")
			return
		}
		uid, err := h.store.GetOrCreateUserByToken(r.Context(), token)
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		ctx := context.WithValue(r.Context(), userIDKey, uid)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func userID(r *http.Request) int64 {
	return r.Context().Value(userIDKey).(int64)
}
