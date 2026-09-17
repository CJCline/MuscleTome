package com.chy.regimen.di

import android.content.Context
import androidx.room.Room
import com.chy.regimen.data.local.RegimenDatabase
import com.chy.regimen.data.local.dao.CatalogDao
import com.chy.regimen.data.local.dao.RoutineDao
import com.chy.regimen.data.local.dao.UserDao
import com.chy.regimen.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RegimenDatabase {
        return Room.databaseBuilder(
            context,
            RegimenDatabase::class.java,
            "regimen.db",
        )
            // Safe only while you have no real workout history.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides fun provideUserDao(db: RegimenDatabase): UserDao = db.userDao()
    @Provides fun provideCatalogDao(db: RegimenDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideRoutineDao(db: RegimenDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: RegimenDatabase): WorkoutDao = db.workoutDao()
}