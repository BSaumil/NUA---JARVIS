package com.nua.assistant.presence.companion

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.NuaPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

private const val PREFS_NAME = "nua_companion_client_identity"
private const val KEY_PRIVATE_SEED = "private_seed_base64"
private const val KEY_DEVICE_ID = "device_id"

/**
 * What [CompanionPairingClient] and [CompanionApiClient] need from a device identity --
 * split out from [CompanionIdentity] so their pairing/signing/authorization logic can be
 * unit-tested against a plain in-memory fake, without an Android [Context] or Keystore.
 */
interface CompanionSigningIdentity {
    val identity: DeviceIdentity
    fun sign(message: ByteArray): String
}

/**
 * This device's persistent Ed25519 keypair and [DeviceIdentity], used to pair with and
 * sign requests to a companion node (services/companion). The private key never leaves
 * this class; only the public key is ever placed on the wire.
 *
 * Signing is deterministic (Ed25519, no per-signature randomness), so the same digest
 * always yields the same signature -- this is exploited directly by
 * CompanionSecurityTest's golden-vector cases to prove this implementation is
 * byte-for-byte interoperable with the companion's own Python implementation.
 */
@Singleton
class CompanionIdentity @Inject constructor(
    @ApplicationContext context: Context,
    appVersionName: String = "0.1.0",
) : CompanionSigningIdentity {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val privateKey: Ed25519PrivateKeyParameters by lazy { loadOrCreatePrivateKey() }

    override val identity: DeviceIdentity by lazy {
        DeviceIdentity(
            deviceId = loadOrCreateDeviceId(),
            publicKey = publicKeyBase64(),
            ownerLabel = "NUA Android client",
            platform = NuaPlatform.ANDROID_PHONE,
            appVersion = appVersionName,
        )
    }

    fun publicKeyBase64(): String = Base64.getEncoder().encodeToString(privateKey.generatePublicKey().encoded)

    /** Signs [message] (typically a [CompanionSecurity.digestRequest] result) and returns
     * the signature as base64, matching companion's `signatureBase64` fields. */
    override fun sign(message: ByteArray): String {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        return Base64.getEncoder().encodeToString(signer.generateSignature())
    }

    private fun loadOrCreatePrivateKey(): Ed25519PrivateKeyParameters {
        val existing = prefs.getString(KEY_PRIVATE_SEED, null)
        if (existing != null) {
            return Ed25519PrivateKeyParameters(Base64.getDecoder().decode(existing), 0)
        }
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)
        prefs.edit().putString(KEY_PRIVATE_SEED, Base64.getEncoder().encodeToString(seed)).apply()
        return Ed25519PrivateKeyParameters(seed, 0)
    }

    private fun loadOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val deviceId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }
}
