package com.nua.assistant.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the three keyless public-API connectors (weather/news/finance) added
 * alongside the OAuth- and token-based ones -- these need no external credential
 * provisioning at all, so [SafeHttpConnector] must accept them with no bearer token
 * and no OAuth activation step. */
class ConnectorCatalogTest {

    private val keylessConnectors = listOf(ConnectorCatalog.weather, ConnectorCatalog.news, ConnectorCatalog.finance)

    @Test
    fun `all lists every catalog connector including the new keyless ones`() {
        val all = ConnectorCatalog.all()

        assertEquals(8, all.size)
        assertTrue(all.containsAll(keylessConnectors))
    }

    @Test
    fun `each keyless connector declares authType NONE`() {
        keylessConnectors.forEach { manifest ->
            assertEquals("${manifest.id} should need no credential", ConnectorAuthType.NONE, manifest.authType)
        }
    }

    @Test
    fun `each keyless connector is read-only -- no write capability, no UAF remote endpoint`() {
        keylessConnectors.forEach { manifest ->
            assertTrue("${manifest.id} declares a write capability it has no governed endpoint for", manifest.writeCapabilities.isEmpty())
            assertEquals(null, manifest.remoteEndpointIdForWrites)
        }
    }

    @Test
    fun `each keyless connector passes manifest validation`() {
        keylessConnectors.forEach { manifest ->
            val validation = validateConnectorManifest(manifest)
            assertTrue("${manifest.id} failed validation: ${validation.reason}", validation.allowed)
        }
    }

    @Test
    fun `each keyless connector's allowed host is an exact DNS name, not a URL`() {
        keylessConnectors.forEach { manifest ->
            manifest.allowedHosts.forEach { host ->
                assertTrue("$host must not contain a scheme, port, or path", '/' !in host && ':' !in host)
            }
        }
    }

    @Test
    fun `a capability not declared by a keyless connector cannot be proposed as a write`() {
        val result = writeProposal(ConnectorCatalog.weather, "weather.forecast", "{}", "{}", "idem-1")

        assertTrue(result is ConnectorResult.Refused)
    }
}
