package com.nua.assistant.automation.operators

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal const val OPERATOR_ID_INPUT = "operator_id"
private const val UNIQUE_PREFIX = "nua-sovereign-operator:"

@Singleton
class SovereignOperatorScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SovereignOperatorStore,
) {
    fun apply(manifest: SovereignOperatorManifest) {
        val validation = validateOperator(manifest)
        require(validation.valid) { validation.errors.joinToString() }
        store.save(manifest)
        if (!manifest.enabled) {
            cancel(manifest.id)
            return
        }

        val repeatMinutes = when (manifest.schedule.type) {
            OperatorScheduleType.INTERVAL -> requireNotNull(manifest.schedule.intervalMinutes)
            OperatorScheduleType.DAILY -> 24 * 60L
        }
        val initialDelay = when (manifest.schedule.type) {
            OperatorScheduleType.INTERVAL -> 0L
            OperatorScheduleType.DAILY -> millisUntilNext(
                requireNotNull(manifest.schedule.hour),
                requireNotNull(manifest.schedule.minute),
            )
        }

        val request = PeriodicWorkRequestBuilder<SovereignOperatorWorker>(repeatMinutes, TimeUnit.MINUTES)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(OPERATOR_ID_INPUT, manifest.id).build())
            .addTag(UNIQUE_PREFIX + manifest.id)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PREFIX + manifest.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PREFIX + id)
    }

    fun delete(id: String) {
        cancel(id)
        store.delete(id)
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
