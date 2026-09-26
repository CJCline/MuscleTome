package com.chy.muscletome.di

import android.content.Context
import androidx.room.Room
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.MuscleTomeMigrations
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.SelectionHistoryDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
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
    fun provideDatabase(@ApplicationContext context: Context): MuscleTomeDatabase {
        return Room.databaseBuilder(
            context,
            MuscleTomeDatabase::class.java,
            "muscletome.db",
        )
            // Migrations are mandatory: no destructive fallback. A schema
            // change without a matching migration must crash loudly rather
            // than silently wipe workout history. See MuscleTomeMigrations.
            .addMigrations(*MuscleTomeMigrations.ALL)
            .build()
    }

    @Provides fun provideUserDao(db: MuscleTomeDatabase): UserDao = db.userDao()
    @Provides fun provideCatalogDao(db: MuscleTomeDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideRoutineDao(db: MuscleTomeDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: MuscleTomeDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideSelectionHistoryDao(db: MuscleTomeDatabase): SelectionHistoryDao = db.selectionHistoryDao()
}
