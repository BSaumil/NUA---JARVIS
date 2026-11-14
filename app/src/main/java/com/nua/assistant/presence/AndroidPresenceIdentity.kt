package com.nua.assistant.presence

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nua.assistant.BuildConfig
import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.NuaPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_ALIAS = "nua_presence_identity_p256"

@Singleton
class AndroidPresenceIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("nua_presence_identity", Context.MODE_PRIVATE)

    private fun ensureKey() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        generator.generateKeyPair()
    }

    fun identity(ownerLabel: String = android.os.Build.MODEL): DeviceIdentity {
        ensureKey()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val publicKey = store.getCertificate(KEY_ALIAS).publicKey.encoded
        val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        val platform = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
            NuaPlatform.ANDROID_TABLET
        } else {
            NuaPlatform.ANDROID_PHONE
        }
        return DeviceIdentity(
            deviceId = deviceId,
            publicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP),
            ownerLabel = ownerLabel,
            platform = platform,
            appVersion = BuildConfig.VERSION_NAME,
            keyAlgorithm = "P256_SHA256",
            publicKeyEncoding = "X509",
        )
    }

    fun sign(message: ByteArray): String {
        ensureKey()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(message)
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
