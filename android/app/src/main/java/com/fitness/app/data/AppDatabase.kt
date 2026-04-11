package com.fitness.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Dao
interface ActiveWorkoutDao {
    @Query("SELECT * FROM active_workout WHERE id = 1")
    suspend fun getActiveWorkout(): ActiveWorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: ActiveWorkoutEntity)

    @Query("UPDATE active_workout SET bodyWeight = :weight WHERE id = 1")
    suspend fun updateBodyWeight(weight: Double?)

    @Query("UPDATE active_workout SET stoppedAt = :stoppedAt WHERE id = 1")
    suspend fun updateStoppedAt(stoppedAt: Long?)

    @Query("DELETE FROM active_workout")
    suspend fun clearWorkout()

    @Query("SELECT * FROM active_exercises ORDER BY sortOrder")
    suspend fun getExercises(): List<ActiveExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ActiveExerciseEntity>)

    @Query("DELETE FROM active_exercises")
    suspend fun clearExercises()

    @Query("SELECT * FROM active_sets WHERE isWarmup = 0 ORDER BY exerciseId, setOrder")
    suspend fun getWorkingSets(): List<ActiveSetEntity>

    @Query("SELECT * FROM active_sets WHERE isWarmup = 1 ORDER BY exerciseId, setOrder")
    suspend fun getWarmupSets(): List<ActiveSetEntity>

    @Query("SELECT * FROM active_sets ORDER BY exerciseId, setOrder")
    suspend fun getAllSets(): List<ActiveSetEntity>

    @Insert
    suspend fun insertSet(set: ActiveSetEntity): Long

    @Query("UPDATE active_sets SET reps = :reps WHERE id = :id")
    suspend fun updateSetReps(id: Long, reps: Int)

    @Query("DELETE FROM active_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM active_sets")
    suspend fun clearSets()

    @Transaction
    suspend fun clearAll() {
        clearWorkout()
        clearExercises()
        clearSets()
    }
}

@Database(
    entities = [ActiveWorkoutEntity::class, ActiveSetEntity::class, ActiveExerciseEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activeWorkoutDao(): ActiveWorkoutDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "fitness.db"
            ).fallbackToDestructiveMigration(true).build()
        }
    }
}
