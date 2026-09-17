package com.chy.regimen

import android.app.Application
import com.chy.regimen.data.local.seed.AppSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RegimenApp : Application() {

    @Inject lateinit var seeder: AppSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            seeder.seedIfEmpty()
        }
    }
}