package com.nua.assistant.security.egress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun capsule(
    categories: Set<DataCategory> = setOf(DataCategory.SENSITIVE_FACTS),
    disclosure: DisclosureLevel = DisclosureLevel.RAW,
    networkEgress: Boolean = true,
    expiresAtMillis: Long? = null,
) = PrivacyCapsule(
    purpose = "test",
    recipientProvider = "anthropic-claude",
    permittedCategories = categories,
    disclosureLevel = disclosure,
    networkEgressPermitted = networkEgress,
    expiresAtMillis = expiresAtMillis,
)

class PrivacyCapsuleTest {

    private val now = 1_000_000L

    @Test
    fun `a capsule authorizes exactly the category it names`() {
        assertTrue(capsuleAuthorizes(capsule(setOf(DataCategory.SENSITIVE_FACTS)), DataCategory.SENSITIVE_FACTS, now))
        assertFalse(capsuleAuthorizes(capsule(setOf(DataCategory.SENSITIVE_FACTS)), DataCategory.DOCUMENT_CONTENT, now))
    }

    @Test
    fun `network egress must be explicitly permitted`() {
        assertFalse(capsuleAuthorizes(capsule(networkEgress = false), DataCategory.SENSITIVE_FACTS, now))
    }

    @Test
    fun `a null expiry never expires`() {
        assertTrue(capsuleAuthorizes(capsule(expiresAtMillis = null), DataCategory.SENSITIVE_FACTS, now + 10_000_000L))
    }

    @Test
    fun `a set expiry in the past refuses authorization`() {
        assertFalse(capsuleAuthorizes(capsule(expiresAtMillis = now - 1), DataCategory.SENSITIVE_FACTS, now))
    }

    @Test
    fun `a set expiry strictly in the future still authorizes`() {
        assertTrue(capsuleAuthorizes(capsule(expiresAtMillis = now + 1), DataCategory.SENSITIVE_FACTS, now))
    }

    @Test
    fun `the expiry instant itself is already expired, not still valid`() {
        assertFalse(capsuleAuthorizes(capsule(expiresAtMillis = now), DataCategory.SENSITIVE_FACTS, now))
    }
}
