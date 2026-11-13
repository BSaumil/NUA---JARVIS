package com.nua.assistant.connectors

import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MAX_CONNECTOR_BODY = 256 * 1024

class SafeHttpConnector @Inject constructor(
    private val client: OkHttpClient,
    private val secretStore: ConnectorSecretStore,
) {
    suspend fun read(
        manifest: ConnectorManifest,
        request: ConnectorRequest,
        baseUrl: String,
    ): ConnectorResult = withContext(Dispatchers.IO) {
        val validation = validateConnectorManifest(manifest)
        if (!validation.allowed) return@withContext ConnectorResult.Refused(validation.reason ?: "invalid manifest")
        if (request.capability !in manifest.readCapabilities) {
            return@withContext ConnectorResult.Refused("read capability not declared")
        }
        if (manifest.authType == ConnectorAuthType.COMPANION_PAIRED) {
            // A companion-paired connector's requests must be Ed25519-signed against a
            // specific paired device's key (presence/companion/**); this generic HTTP path
            // has no way to produce that signature, so it must never silently attempt an
            // unsigned request. See CompanionFilesConnector for the real implementation.
            return@withContext ConnectorResult.Refused("companion-paired connectors are not served by SafeHttpConnector")
        }

        val base = runCatching { URI(baseUrl) }.getOrNull()
            ?: return@withContext ConnectorResult.Refused("invalid base URL")
        if (base.scheme != "https") return@withContext ConnectorResult.Refused("HTTPS required")
        val host = base.host?.lowercase()
            ?: return@withContext ConnectorResult.Refused("host required")
        if (host !in manifest.allowedHosts.map { it.lowercase() }.toSet()) {
            return@withContext ConnectorResult.Refused("host not allowlisted")
        }
        if (base.userInfo != null) return@withContext ConnectorResult.Refused("userinfo in URL is forbidden")
        if (base.port !in listOf(-1, 443)) return@withContext ConnectorResult.Refused("non-standard port forbidden")

        val path = request.path.trim()
        if (!path.startsWith("/") || path.contains("..")) {
            return@withContext ConnectorResult.Refused("unsafe connector path")
        }

        val query = request.query.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        val url = baseUrl.trimEnd('/') + path + if (query.isBlank()) "" else "?$query"
        val builder = Request.Builder().url(url).get().header("accept", "application/json, text/plain;q=0.8")
        if (manifest.authType == ConnectorAuthType.BEARER_TOKEN) {
            val token = secretStore.get(manifest.id)
                ?: return@withContext ConnectorResult.Refused("connector credential is not configured")
            builder.header("authorization", "Bearer $token")
        } else if (manifest.authType == ConnectorAuthType.OAUTH2_EXTERNAL) {
            return@withContext ConnectorResult.Refused("OAuth connector requires platform OAuth activation")
        }

        runCatching {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use ConnectorResult.Failed("HTTP_${response.code}")
                val contentType = response.header("content-type").orEmpty().lowercase()
                if (!contentType.startsWith("application/json") && !contentType.startsWith("text/")) {
                    return@use ConnectorResult.Refused("binary connector response is not accepted")
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val truncated = bytes.size > MAX_CONNECTOR_BODY
                val safe = bytes.copyOf(minOf(bytes.size, MAX_CONNECTOR_BODY)).toString(Charsets.UTF_8)
                ConnectorResult.ReadSuccess(manifest.id, contentType, safe, truncated)
            }
        }.getOrElse {
            ConnectorResult.Failed("TRANSPORT_FAILURE")
        }
    }
}
