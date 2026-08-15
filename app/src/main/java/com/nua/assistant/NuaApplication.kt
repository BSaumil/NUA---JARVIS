package com.nua.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nua.assistant.documents.DocumentExpiryWorker
import com.nua.assistant.dreams.DreamSynthesisWorker
import com.nua.assistant.goals.GoalReviewWorker
import com.nua.assistant.memory.MemoryConsolidationWorker
import com.nua.assistant.vision.VisionMonitorWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val MEMORY_CONSOLIDATION_WORK_NAME = "nua_memory_consolidation"
private const val GOAL_REVIEW_WORK_NAME = "nua_goal_review"
private const val DREAM_SYNTHESIS_WORK_NAME = "nua_dream_synthesis"
private const val VISION_MONITOR_WORK_NAME = "nua_vision_monitor"
private const val DOCUMENT_EXPIRY_WORK_NAME = "nua_document_expiry"

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

        val consolidationRequest = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MEMORY_CONSOLIDATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            consolidationRequest,
        )

        val goalReviewRequest = PeriodicWorkRequestBuilder<GoalReviewWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GOAL_REVIEW_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            goalReviewRequest,
        )

        val dreamSynthesisRequest = PeriodicWorkRequestBuilder<DreamSynthesisWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DREAM_SYNTHESIS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dreamSynthesisRequest,
        )

        val visionMonitorRequest = PeriodicWorkRequestBuilder<VisionMonitorWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            VISION_MONITOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            visionMonitorRequest,
        )

        val documentExpiryRequest = PeriodicWorkRequestBuilder<DocumentExpiryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DOCUMENT_EXPIRY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            documentExpiryRequest,
        )
    }
}
