# Workouts

A fitness tracking app with a Go REST API backend and a native Android client built with Kotlin and Jetpack Compose.

## Features

- **Program management** — create and organize workout programs with custom exercise selections
- **Active workout tracking** — log sets, reps, and weight in real time with a built-in rest timer
- **Progressive overload** — per-exercise settings for working weight, target reps/sets, auto-increment amounts, and deload percentages
- **Progress analytics** — view exercise history, weight progression charts, and body weight trends
- **Workout history** — browse and delete past sessions
- **Offline-first** — workouts are cached locally with Room and synced to the API when available
- **Theming** — Material Design 3 with multiple color presets, persisted across sessions

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Go 1.22, Chi v5, pgx/v5 |
| Database | PostgreSQL 16 |
| Android | Kotlin, Jetpack Compose, Room, Retrofit |
| Infrastructure | Docker, Docker Compose, Caddy (HTTPS), DuckDNS |

## Project Structure

```
Workouts/
├── api/                  # Go REST API
│   ├── main.go           # Server setup, routing, migrations
│   ├── handlers.go       # HTTP handlers
│   ├── store.go          # Database queries
│   ├── models.go         # Shared data structures
│   ├── seed.go           # Exercise catalog seeding
│   ├── Dockerfile
│   └── migrations/       # SQL migration files
├── android/              # Kotlin Android app
│   └── app/src/main/java/com/workouts/app/
│       ├── data/         # Room DB, Retrofit API, Repository
│       └── ui/           # Compose screens and ViewModel
└── docker-compose.yml
```

## Getting Started

### Backend

**Prerequisites:** Docker and Docker Compose

For local development, start just the API and database:

```bash
docker compose up api db
```

This starts the Go API on port `9001` and PostgreSQL on port `5432`. The database is initialized and seeded with a built-in exercise catalog automatically.

The full stack (`docker compose up`, requires a `.env` — see `.env.example`) adds two services for internet-facing deployment:

- **caddy** — HTTPS reverse proxy with automatic Let's Encrypt certificates, listening on host ports `8081` (HTTP) and `8443` (HTTPS). The router must forward external `80 → 8081` and `443 → 8443`.
- **duckdns** — keeps the DuckDNS subdomain pointed at the network's public IP.

To run without Docker:

```bash
cd api
DATABASE_URL=postgres://user:password@localhost:5432/workouts go run .
```

### Android

**Prerequisites:** Android Studio, Android SDK (API 26+)

1. Open the `android/` directory in Android Studio.
2. The server URL is compiled in as `ApiService.BASE_URL` — point it at your API host (e.g. `http://10.0.2.2:8080` for the emulator) when developing.
3. Build and run on a device or emulator (API 26+).

## Device Identity

All data on the server is scoped to a user, identified by a device token. The Android app generates a random UUID on first launch, stores it in SharedPreferences, and sends it on every request in the `X-Device-Token` header. The API creates a user automatically the first time it sees a new token, so no sign-up is needed — each fresh install gets its own empty account. Requests without a token are rejected with `401`.

The token is shown under **Settings → Device ID** in the app (tap to copy).

To attach a device to an existing user (e.g. claiming data that predates user accounts, or moving to a new phone), insert its token manually:

```sql
INSERT INTO device_tokens (token, user_id) VALUES ('<device id from app settings>', <user id>);
```

Data created before migration `006_users` is assigned to a single legacy user (the first row in `users`).

### Google account linking (optional)

A user can link a Google account from **Settings → Google Account**. The app obtains an ID token via Credential Manager and posts it to `/api/auth/google`; the API verifies it against `GOOGLE_CLIENT_ID` and stores the Google identity on the user. Linking the same Google account from a new device re-points that device's token at the existing user and merges any data — this is the device-loss recovery path. No account is required to use the app.

## API Reference

All endpoints are prefixed with `/api` and require the `X-Device-Token` header.

| Method | Path | Description |
|---|---|---|
| `GET/POST/DELETE` | `/api/auth/google` | Get, create, or remove the Google account link |
| `GET` | `/api/exercises` | List exercise catalog |
| `GET/POST` | `/api/programs` | List or create programs |
| `GET/PUT/DELETE` | `/api/programs/{id}` | Get, rename, or delete a program |
| `PUT` | `/api/programs/reorder` | Reorder programs |
| `POST/DELETE` | `/api/programs/{id}/exercises` | Add or remove exercises from a program |
| `GET/PUT` | `/api/programs/{programID}/exercises/{exerciseID}/settings` | Get or update exercise settings |
| `GET/POST` | `/api/workouts` | List or submit a workout |
| `POST` | `/api/workouts/import` | Batch import workouts |
| `GET/DELETE` | `/api/workouts/{id}` | Get or delete a workout |
| `PATCH` | `/api/workouts/{id}/bodyweight` | Update body weight for a workout |
| `DELETE` | `/api/workouts/all` | Delete all workout history |
| `GET` | `/api/progress/exercises` | Exercises with full history |
| `GET` | `/api/progress/exercise/{exerciseID}` | Progress for a specific exercise |
| `GET` | `/api/progress/bodyweight` | Body weight history |

## Configuration

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `postgres://...localhost...` | PostgreSQL connection string |
| `PORT` | `8080` | API listen port (compose sets `9001`) |
| `DUCKDNS_TOKEN` | — | DuckDNS account token (compose `.env`, duckdns service only) |
| `GOOGLE_CLIENT_ID` | — | OAuth web client ID for Google account linking; linking is disabled when unset |

The Android app's server URL is a compile-time constant (`ApiService.BASE_URL`).
