package com.chy.muscletome

import android.app.Application
import com.chy.muscletome.data.local.seed.AppSeeder
import com.chy.muscletome.data.repository.BundledCatalogImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MuscleTomeApp : Application() {

    @Inject lateinit var seeder: AppSeeder
    @Inject lateinit var catalogImporter: BundledCatalogImporter

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            seeder.seedIfEmpty()
            // Bundled catalog (offline, deterministic, idempotent by asset
            // revision) — runs after the seed so review candidates against
            // seed exercises resolve in the same pass.
            catalogImporter.runIfNeeded()
        }
    }
}
