package com.nua.assistant.telemetry

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelProviderTier
import com.nua.assistant.ai.mesh.RoutingEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceResourceSnapshot(
    val batteryPercent: Int?,
    val charging: Boolean,
    val powerSave: Boolean,
    val thermalStatus: Int?,
    val lowMemory: Boolean,
    val networkClass: String,
) {
    fun routingEnvironment(): RoutingEnvironment = RoutingEnvironment(
        batteryPercent = batteryPercent,
        charging = charging,
        powerSave = powerSave,
        thermalStatus = thermalStatus,
        networkClass = networkClass,
    )
}

@Singleton
class DeviceResourceSnapshotProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun snapshot(): DeviceResourceSnapshot {
        val battery = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)

        val memory = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
        val caps = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val networkClass = when {
            caps == null -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }

        return DeviceResourceSnapshot(
            batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 },
            charging = battery?.isCharging ?: false,
            powerSave = power?.isPowerSaveMode ?: false,
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus else null,
            lowMemory = memory.lowMemory,
            networkClass = networkClass,
        )
    }
}

data class InferenceTelemetryEvent(
    val task: InferenceTaskType,
    val tier: ModelProviderTier,
    val succeeded: Boolean,
    val latencyMillis: Long,
    val validationAccepted: Boolean,
    val batteryPercent: Int?,
    val charging: Boolean,
    val powerSave: Boolean,
    val thermalStatus: Int?,
    val networkClass: String,
)

/**
 * Content-free inference telemetry. It intentionally has no prompt/response field.
 * The first slice keeps only an in-memory ring so routing can inspect current-session
 * behavior without creating another long-lived personal-data store.
 */
@Singleton
class InferenceTelemetryRecorder @Inject constructor() {
    private val events = ArrayDeque<InferenceTelemetryEvent>()

    @Synchronized
    fun record(event: InferenceTelemetryEvent) {
        if (events.size >= 256) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun recent(): List<InferenceTelemetryEvent> = events.toList()
}
