package com.nua.assistant.presence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nua_presence_prefs"
private const val KEY_DEVICE_ID = "device_id"

/**
 * Owns this phone's stable per-install [deviceId] (generated once, persisted, never a
 * hardware identifier — the device-identity primitive the prior Presence Mesh
 * investigation named as missing) and publishes this device's own activity through
 * [PresenceTransport]. See `docs/PRESENCE_MESH_RFC.md`.
 */
@Singleton
class PresenceRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val transport: PresenceTransport,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    /** Publishes "this phone is active right now" — called once per app open
     *  ([com.nua.assistant.ui.NuaViewModel]'s init), a real event, not a periodic
     *  heartbeat (see the RFC's §4 for why that's a separate, not-yet-attempted slice). */
    suspend fun publishSelf(now: Long = System.currentTimeMillis()) {
        transport.publish(PresenceSnapshot(deviceId = deviceId, deviceType = DeviceType.PHONE, lastActiveAt = now))
    }
}
