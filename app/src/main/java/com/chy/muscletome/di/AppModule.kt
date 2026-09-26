package com.chy.muscletome.di

import com.chy.muscletome.domain.selection.VarietyEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Scope for writes that must outlive a screen (e.g. note autosave flushes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideVarietyEngine(): VarietyEngine = VarietyEngine()

    /**
     * Note autosaves must survive ViewModel teardown — leaving the workout
     * screen is now the *normal* way out (session stays open), so the final
     * debounced flush has to run past the ViewModel's cancelled scope.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
