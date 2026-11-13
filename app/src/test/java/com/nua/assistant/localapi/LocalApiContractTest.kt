package com.nua.assistant.localapi

import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.NuaPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private fun identity(scopes: Set<LocalApiScope>) = LocalApiClientIdentity(
    clientId = "desktop-1",
    device = DeviceIdentity("desktop-1", "pub", "Desk", NuaPlatform.WINDOWS, "0.1"),
    grantedScopes = scopes,
    keyFingerprint = "abc",
)

private fun meta(nonce: String = "n1", time: Long = 1_000) = LocalApiRequestMeta(
    requestId = "r1",
    clientId = "desktop-1",
    nonce = nonce,
    timestampMillis = time,
    signatureBase64 = "sig",
)

class LocalApiContractTest {
    @Test fun `scope is mandatory`() {
        assertEquals(
            LocalApiAuthDecision.REFUSE_SCOPE,
            authorizeLocalApiRequest(identity(setOf(LocalApiScope.HEALTH)), meta(), LocalApiScope.INFERENCE, emptySet(), 1_000).decision,
        )
    }

    @Test fun `replayed nonce is refused`() {
        assertEquals(
            LocalApiAuthDecision.REFUSE_REPLAY,
            authorizeLocalApiRequest(identity(setOf(LocalApiScope.INFERENCE)), meta(), LocalApiScope.INFERENCE, setOf("n1"), 1_000).decision,
        )
    }

    @Test fun `clock skew is bounded`() {
        assertEquals(
            LocalApiAuthDecision.REFUSE_CLOCK_SKEW,
            authorizeLocalApiRequest(identity(setOf(LocalApiScope.INFERENCE)), meta(time = 1_000), LocalApiScope.INFERENCE, emptySet(), 500_000).decision,
        )
    }

    @Test fun `digest changes with operation payload`() {
        val m = meta()
        assertNotEquals(
            localApiRequestDigest(m, "health", "{}"),
            localApiRequestDigest(m, "inference.start", "{}"),
        )
    }
}
