package com.nua.assistant.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface RemoteEndpointResolver {
    fun approved(id: String): RemoteEndpointTrust?
}

@Singleton
class RemoteEndpointRegistry @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : RemoteEndpointResolver {
    private val prefs = context.getSharedPreferences("nua_remote_endpoints", Context.MODE_PRIVATE)

    fun stage(manifest: RemoteEndpointManifest) {
        prefs.edit().putString("manifest:${manifest.id}", json.encodeToString(manifest)).apply()
    }

    fun approve(id: String, nowMillis: Long = System.currentTimeMillis()): RemoteEndpointTrust? {
        val manifest = staged(id) ?: return null
        val trust = RemoteEndpointTrust(manifest, remoteEndpointFingerprint(manifest), nowMillis)
        prefs.edit().putString("trust:$id", json.encodeToString(trust)).apply()
        return trust
    }

    fun revoke(id: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val current = trust(id) ?: return false
        prefs.edit().putString("trust:$id", json.encodeToString(current.copy(revokedAtMillis = nowMillis))).apply()
        return true
    }

    fun staged(id: String): RemoteEndpointManifest? =
        prefs.getString("manifest:$id", null)?.let {
            runCatching { json.decodeFromString<RemoteEndpointManifest>(it) }.getOrNull()
        }

    fun trust(id: String): RemoteEndpointTrust? =
        prefs.getString("trust:$id", null)?.let {
            runCatching { json.decodeFromString<RemoteEndpointTrust>(it) }.getOrNull()
        }

    override fun approved(id: String): RemoteEndpointTrust? {
        val trust = trust(id) ?: return null
        val current = staged(id) ?: return null
        if (trust.isRevoked) return null
        if (remoteEndpointFingerprint(current) != trust.approvedFingerprint) return null
        return trust.copy(manifest = current)
    }
}
