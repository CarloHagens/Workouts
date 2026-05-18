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
| Infrastructure | Docker, Docker Compose |

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

```bash
docker compose up
```

This starts the Go API on port `8080` and PostgreSQL on port `5432`. The database is initialized and seeded with a built-in exercise catalog automatically.

To run without Docker:

```bash
cd api
DATABASE_URL=postgres://user:password@localhost:5432/workouts go run .
```

### Android

**Prerequisites:** Android Studio, Android SDK (API 26+)

1. Open the `android/` directory in Android Studio.
2. Go to **Settings** in the app and set the server URL to your API host (e.g. `http://10.0.2.2:8080` for the emulator).
3. Build and run on a device or emulator (API 26+).

## API Reference

All endpoints are prefixed with `/api`.

| Method | Path | Description |
|---|---|---|
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
| `PORT` | `8080` | API listen port |

The Android app stores the server URL in SharedPreferences, configurable from the Settings screen.
