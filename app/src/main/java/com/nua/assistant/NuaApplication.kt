package com.nua.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NuaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // WorkManager's default androidx.startup initializer runs too early for Hilt's
        // field injection into Application to have completed (see the disabled
        // WorkManagerInitializer in AndroidManifest.xml) — initialize it here instead,
        // once workerFactory is actually injected, so MorningBriefingWorker gets its
        // dependencies.
        WorkManager.initialize(this, workManagerConfiguration)
    }
}
