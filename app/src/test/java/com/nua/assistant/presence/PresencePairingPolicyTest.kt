package com.nua.assistant.presence

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresencePairingPolicyTest {
    @Test fun `plain HTTP is localhost only`() {
        assertTrue(validatePresenceEndpoint("http://127.0.0.1:8765", null).allowed)
        assertFalse(validatePresenceEndpoint("http://192.168.1.2:8765", null).allowed)
    }

    @Test fun `remote HTTPS requires an owner-approved pin`() {
        assertFalse(validatePresenceEndpoint("https://192.168.1.2:8765", null).allowed)
        assertTrue(validatePresenceEndpoint("https://companion.local:8765", "sha256/abc").allowed)
    }

    @Test fun `canonical JSON removes object insertion-order ambiguity for signed requests`() {
        val a = Json.parseToJsonElement("""{"b":2,"a":{"y":2,"x":1}}""")
        val b = Json.parseToJsonElement("""{"a":{"x":1,"y":2},"b":2}""")
        assertTrue(canonicalJson(a) == canonicalJson(b))
    }
}
