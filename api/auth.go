package main

import (
	"context"
	"encoding/json"
	"net/http"

	"google.golang.org/api/idtoken"
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

// Google account linking. A linked Google identity lets a user reclaim their
// data from a new device: signing in on an unknown device re-points that
// device's token at the existing user.

type googleLinkRequest struct {
	IDToken string `json:"id_token"`
}

type googleLinkResponse struct {
	Linked   bool   `json:"linked"`
	Email    string `json:"email,omitempty"`
	Restored bool   `json:"restored,omitempty"`
}

func (h *Handler) GoogleStatus(w http.ResponseWriter, r *http.Request) {
	sub, email, err := h.store.GetGoogleLink(r.Context(), userID(r))
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, googleLinkResponse{Linked: sub != "", Email: email})
}

func (h *Handler) LinkGoogle(w http.ResponseWriter, r *http.Request) {
	if h.googleClientID == "" {
		writeError(w, http.StatusServiceUnavailable, "google sign-in is not configured on this server")
		return
	}
	var req googleLinkRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.IDToken == "" {
		writeError(w, http.StatusBadRequest, "id_token is required")
		return
	}

	payload, err := idtoken.Validate(r.Context(), req.IDToken, h.googleClientID)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "invalid google token")
		return
	}
	if payload.Issuer != "https://accounts.google.com" && payload.Issuer != "accounts.google.com" {
		writeError(w, http.StatusUnauthorized, "invalid google token")
		return
	}
	email, _ := payload.Claims["email"].(string)

	restored, err := h.store.LinkGoogle(r.Context(), userID(r), payload.Subject, email)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, googleLinkResponse{Linked: true, Email: email, Restored: restored})
}

func (h *Handler) UnlinkGoogle(w http.ResponseWriter, r *http.Request) {
	if err := h.store.UnlinkGoogle(r.Context(), userID(r)); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
