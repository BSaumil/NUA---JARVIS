package com.nua.assistant.security.egress

import com.nua.assistant.memory.MemoryPrivacyLevel
import com.nua.assistant.memory.UserFactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fact(key: String, privacyLevel: MemoryPrivacyLevel) =
    UserFactEntity(key = key, value = "v", category = "c", privacyLevel = privacyLevel)

class DataEgressGatewayTest {

    private val standardFact = fact("standard", MemoryPrivacyLevel.STANDARD)
    private val sensitiveFact = fact("sensitive", MemoryPrivacyLevel.SENSITIVE)

    @Test
    fun `a null capsule -- the strictest policy -- lets Standard facts through but blocks Sensitive ones`() {
        val (permitted, decision) = DataEgressGateway.filterFacts(capsule = null, facts = listOf(standardFact, sensitiveFact))
        assertEquals(listOf(standardFact), permitted)
        assertEquals(1, decision.itemCountRequested)
        assertEquals(0, decision.itemCountPermitted)
        assertFalse(decision.fullyPermitted)
    }

    @Test
    fun `a capsule authorizing SENSITIVE_FACTS at RAW disclosure lets Sensitive facts through`() {
        val capsule = PrivacyCapsule(
            purpose = "test", recipientProvider = "anthropic-claude",
            permittedCategories = setOf(DataCategory.SENSITIVE_FACTS),
            disclosureLevel = DisclosureLevel.RAW, networkEgressPermitted = true,
        )
        val (permitted, decision) = DataEgressGateway.filterFacts(capsule, listOf(standardFact, sensitiveFact))
        assertEquals(setOf(standardFact, sensitiveFact), permitted.toSet())
        assertTrue(decision.fullyPermitted)
    }

    @Test
    fun `a capsule requesting REDACTED disclosure for facts still refuses Sensitive facts -- no per-fact redaction exists yet`() {
        val capsule = PrivacyCapsule(
            purpose = "test", recipientProvider = "anthropic-claude",
            permittedCategories = setOf(DataCategory.SENSITIVE_FACTS),
            disclosureLevel = DisclosureLevel.REDACTED, networkEgressPermitted = true,
        )
        val (permitted, _) = DataEgressGateway.filterFacts(capsule, listOf(sensitiveFact))
        assertTrue("a disclosure level this codebase can't actually honor must refuse, not silently upgrade to RAW", permitted.isEmpty())
    }

    @Test
    fun `a capsule naming a different category does not authorize Sensitive facts`() {
        val capsule = PrivacyCapsule(
            purpose = "test", recipientProvider = "anthropic-claude",
            permittedCategories = setOf(DataCategory.DOCUMENT_CONTENT),
            disclosureLevel = DisclosureLevel.RAW, networkEgressPermitted = true,
        )
        val (permitted, _) = DataEgressGateway.filterFacts(capsule, listOf(sensitiveFact))
        assertTrue(permitted.isEmpty())
    }

    @Test
    fun `an expired capsule refuses Sensitive facts even though it names the right category`() {
        val capsule = PrivacyCapsule(
            purpose = "test", recipientProvider = "anthropic-claude",
            permittedCategories = setOf(DataCategory.SENSITIVE_FACTS),
            disclosureLevel = DisclosureLevel.RAW, networkEgressPermitted = true,
            expiresAtMillis = 1L,
        )
        val (permitted, _) = DataEgressGateway.filterFacts(capsule, listOf(sensitiveFact), nowMillis = 100L)
        assertTrue(permitted.isEmpty())
    }

    @Test
    fun `an empty fact list produces a decision with zero requested and zero permitted`() {
        val (permitted, decision) = DataEgressGateway.filterFacts(capsule = null, facts = emptyList())
        assertTrue(permitted.isEmpty())
        assertEquals(0, decision.itemCountRequested)
        assertTrue(decision.fullyPermitted)
    }
}
