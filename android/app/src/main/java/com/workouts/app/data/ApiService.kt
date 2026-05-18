package com.workouts.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("api/exercises")
    suspend fun getExercises(
        @Query("category") category: String? = null,
        @Query("muscle_group") muscleGroup: String? = null
    ): List<Exercise>

    @GET("api/programs")
    suspend fun getPrograms(): List<Program>

    @POST("api/programs")
    suspend fun createProgram(@Body request: CreateProgramRequest): Program

    @PUT("api/programs/reorder")
    suspend fun reorderPrograms(@Body request: ReorderProgramsRequest): Unit

    @GET("api/programs/{id}")
    suspend fun getProgram(@Path("id") id: Long): ProgramDetail

    @GET("api/programs/{id}/last-workout")
    suspend fun getLastWorkoutDate(@Path("id") id: Long): LastWorkoutResponse

    @PUT("api/programs/{id}")
    suspend fun renameProgram(@Path("id") id: Long, @Body request: CreateProgramRequest): Program

    @DELETE("api/programs/{id}")
    suspend fun deleteProgram(@Path("id") id: Long)

    @POST("api/programs/{id}/exercises")
    suspend fun addExercise(
        @Path("id") programId: Long,
        @Body request: AddExerciseRequest
    ): ProgramExerciseItem

    @DELETE("api/programs/{programID}/exercises/{exerciseID}")
    suspend fun removeExercise(
        @Path("programID") programId: Long,
        @Path("exerciseID") exerciseId: Long
    )

    @GET("api/programs/{programID}/exercises/{exerciseID}/settings")
    suspend fun getExerciseSettings(
        @Path("programID") programId: Long,
        @Path("exerciseID") exerciseId: Long
    ): ExerciseSettings

    @PUT("api/programs/{programID}/exercises/{exerciseID}/settings")
    suspend fun upsertExerciseSettings(
        @Path("programID") programId: Long,
        @Path("exerciseID") exerciseId: Long,
        @Body request: UpsertExerciseSettingsRequest
    ): ExerciseSettings

    @GET("api/progress/exercises")
    suspend fun getExercisesWithHistory(): List<Exercise>

    @GET("api/progress/bodyweight")
    suspend fun getBodyWeightProgress(): List<ProgressPoint>

    @GET("api/progress/exercise/{exerciseID}")
    suspend fun getExerciseProgress(@Path("exerciseID") exerciseId: Long): List<ProgressPoint>

    @POST("api/workouts")
    suspend fun submitWorkout(@Body request: SubmitWorkoutRequest): WorkoutDetailResponse

    @POST("api/workouts/import")
    suspend fun importWorkouts(@Body request: ImportWorkoutRequest): ImportResponse

    @DELETE("api/workouts/all")
    suspend fun deleteAllWorkouts()

    @GET("api/workouts")
    suspend fun getWorkouts(): List<WorkoutSummary>

    @GET("api/workouts/{id}")
    suspend fun getWorkout(@Path("id") id: Long): WorkoutDetailResponse

    @PATCH("api/workouts/{id}/bodyweight")
    suspend fun updateWorkoutBodyWeight(@Path("id") id: Long, @Body request: UpdateBodyWeightRequest)

    @DELETE("api/workouts/{id}")
    suspend fun deleteWorkout(@Path("id") id: Long)

    companion object {
        const val BASE_URL = "http://192.168.1.100:8080/"

        fun create(baseUrl: String = BASE_URL): ApiService {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
