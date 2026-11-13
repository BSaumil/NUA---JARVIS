package com.nua.assistant.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorPolicyTest {
    @Test fun `write-capable manifest requires governed endpoint`() {
        val bad = ConnectorManifest(
            id = "sample",
            displayName = "Sample",
            version = "1",
            authType = ConnectorAuthType.NONE,
            allowedHosts = setOf("example.com"),
            readCapabilities = emptySet(),
            writeCapabilities = setOf("sample.write"),
        )
        assertFalse(validateConnectorManifest(bad).allowed)
    }

    @Test fun `write proposal becomes UAF REMOTE_TOOL rather than direct connector mutation`() {
        val result = writeProposal(
            ConnectorCatalog.slack,
            "messages.send",
            """{"channel":"ops","text":"hello"}""",
            """{"capsule":"placeholder"}""",
            "idem-1",
        )
        assertTrue(result is ConnectorResult.RequiresUaf)
        result as ConnectorResult.RequiresUaf
        assertEquals("REMOTE_TOOL", result.actionName)
        assertEquals("slack", result.parameters["endpointId"])
        assertEquals("messages.send", result.parameters["tool"])
    }

    @Test fun `undeclared write cannot be proposed`() {
        assertTrue(
            writeProposal(ConnectorCatalog.slack, "admin.delete", "{}", "{}", "i")
                is ConnectorResult.Refused,
        )
    }
}
