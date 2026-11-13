package com.nua.assistant.remote

import com.nua.assistant.security.egress.DataCategory
import com.nua.assistant.security.egress.DisclosureLevel
import com.nua.assistant.security.egress.PrivacyCapsule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun manifest(
    baseUrl: String = "https://tools.example.test",
    protocol: RemoteProtocol = RemoteProtocol.MCP,
    allowed: Set<String> = setOf("weather.lookup"),
) = RemoteEndpointManifest(
    id = "example",
    displayName = "Example",
    protocol = protocol,
    baseUrl = baseUrl,
    allowedCapabilities = allowed,
    tlsPinSha256 = if (baseUrl.startsWith("https://")) "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" else null,
)

private fun trust(manifest: RemoteEndpointManifest): RemoteEndpointTrust =
    RemoteEndpointTrust(manifest, remoteEndpointFingerprint(manifest), approvedAtMillis = 1)

private fun capsule() = PrivacyCapsule(
    purpose = "remote test",
    recipientProvider = "example",
    permittedCategories = setOf(DataCategory.STANDARD_FACTS),
    disclosureLevel = DisclosureLevel.RAW,
    networkEgressPermitted = true,
)

class RemoteGatewayPolicyTest {
    @Test fun `approved capability with capsule is allowed`() {
        assertTrue(validateRemoteInvocation(trust(manifest()), RemoteProtocol.MCP, "weather.lookup", """{"city":"Melbourne"}""", capsule()).allowed)
    }

    @Test fun `undeclared capability is refused`() {
        assertFalse(validateRemoteInvocation(trust(manifest()), RemoteProtocol.MCP, "shell.exec", "{}", capsule()).allowed)
    }

    @Test fun `identity drift after approval is refused`() {
        val approved = trust(manifest())
        val changed = approved.copy(manifest = approved.manifest.copy(baseUrl = "https://other.example.test"))
        assertFalse(validateRemoteInvocation(changed, RemoteProtocol.MCP, "weather.lookup", "{}", capsule()).allowed)
    }

    @Test fun `network egress without capsule is refused`() {
        assertFalse(validateRemoteInvocation(trust(manifest()), RemoteProtocol.MCP, "weather.lookup", "{}", null).allowed)
    }

    @Test fun `credential shaped payload is refused`() {
        assertFalse(validateRemoteInvocation(trust(manifest()), RemoteProtocol.MCP, "weather.lookup", """{"apiKey":"secret-value"}""", capsule()).allowed)
    }

    @Test fun `plain HTTP is localhost only`() {
        val remote = manifest(baseUrl = "http://192.168.1.10:8765")
        assertFalse(validateRemoteInvocation(trust(remote), RemoteProtocol.MCP, "weather.lookup", "{}", capsule()).allowed)

        val local = manifest(baseUrl = "http://localhost:8765")
        assertTrue(validateRemoteInvocation(trust(local), RemoteProtocol.MCP, "weather.lookup", "{}", capsule()).allowed)
    }
}
