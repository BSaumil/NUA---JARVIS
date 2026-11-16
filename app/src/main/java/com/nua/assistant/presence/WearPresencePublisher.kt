package com.nua.assistant.presence

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nua.assistant.presence.protocol.NuaPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val WEAR_PRESENCE_PATH = "/nua/presence/v1"
internal const val WEAR_KEY_DEVICE_ID = "deviceId"
internal const val WEAR_KEY_PLATFORM = "platform"
internal const val WEAR_KEY_APP_VERSION = "appVersion"
internal const val WEAR_KEY_KEY_ALGORITHM = "keyAlgorithm"
internal const val WEAR_KEY_LAST_VERIFIED_AT = "lastVerifiedAtMillis"

/**
 * Publishes a narrow read-only slice of the canonical Presence v1 identity to Wear OS.
 *
 * The Wear Data Layer is transport only: it never grants trust, pairing scopes or action
 * authority. The watch can display that the phone is present; privileged work still uses
 * the signed Presence/Local API protocol.
 */
@Singleton
class WearPresencePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidPresenceIdentity: AndroidPresenceIdentity,
) {
    fun publishPhoneActive(nowMillis: Long = System.currentTimeMillis()) {
        val identity = androidPresenceIdentity.identity()
        val request = PutDataMapRequest.create(WEAR_PRESENCE_PATH).apply {
            dataMap.putString(WEAR_KEY_DEVICE_ID, identity.deviceId)
            dataMap.putString(WEAR_KEY_PLATFORM, identity.platform.name)
            dataMap.putString(WEAR_KEY_APP_VERSION, identity.appVersion)
            dataMap.putString(WEAR_KEY_KEY_ALGORITHM, identity.keyAlgorithm)
            dataMap.putLong(WEAR_KEY_LAST_VERIFIED_AT, nowMillis)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
