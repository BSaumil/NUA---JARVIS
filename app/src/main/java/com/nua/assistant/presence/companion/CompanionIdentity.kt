package com.nua.assistant.presence.companion

import com.nua.assistant.presence.AndroidPresenceIdentity
import com.nua.assistant.presence.protocol.DeviceIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compatibility interface used by companion inference/files clients.
 *
 * There is exactly one Android device identity: [AndroidPresenceIdentity], backed by the
 * Android Keystore P256 key. Companion calls adapt that same identity instead of creating
 * a second Ed25519 private key or a second device id.
 */
interface CompanionSigningIdentity {
    val identity: DeviceIdentity
    fun sign(message: ByteArray): String
}

@Singleton
class CompanionIdentity @Inject constructor(
    private val canonicalIdentity: AndroidPresenceIdentity,
) : CompanionSigningIdentity {
    override val identity: DeviceIdentity
        get() = canonicalIdentity.identity("NUA Android client")

    override fun sign(message: ByteArray): String = canonicalIdentity.sign(message)
}
