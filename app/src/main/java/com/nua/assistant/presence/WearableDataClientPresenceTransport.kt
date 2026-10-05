package com.nua.assistant.presence

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PRESENCE_PATH = "/nua/presence"
internal const val PRESENCE_KEY_DEVICE_ID = "deviceId"
internal const val PRESENCE_KEY_DEVICE_TYPE = "deviceType"
internal const val PRESENCE_KEY_LAST_ACTIVE_AT = "lastActiveAt"

/**
 * The real transport: publishes one [PresenceSnapshot] as a `DataItem` via Google's
 * documented Wearable Data Layer API (`Wearable.getDataClient`), the same mechanism
 * `wear/src/main/java/com/nua/assistant/wear/NuaTileService.kt` reads back. See
 * `docs/PRESENCE_MESH_RFC.md` for what this can and can't verify in this environment.
 *
 * Fire-and-forget, matching this codebase's existing convention for a Play Services
 * `Task`-returning call it doesn't need to await the result of
 * ([com.nua.assistant.geofencing.GeofenceManager]'s `addGeofences`/`removeGeofences`
 * calls do the same) — a failed publish simply means presence doesn't update this time,
 * never a crash or a blocked caller.
 */
@Singleton
class WearableDataClientPresenceTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) : PresenceTransport {
    override suspend fun publish(snapshot: PresenceSnapshot) {
        val request = PutDataMapRequest.create(PRESENCE_PATH).apply {
            dataMap.putString(PRESENCE_KEY_DEVICE_ID, snapshot.deviceId)
            dataMap.putString(PRESENCE_KEY_DEVICE_TYPE, snapshot.deviceType.name)
            dataMap.putLong(PRESENCE_KEY_LAST_ACTIVE_AT, snapshot.lastActiveAt)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
