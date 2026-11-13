package com.nua.assistant.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepResearchPolicyTest {
    @Test fun `claim evidence mapping preserves supporting and contradicting evidence`() {
        val evidence = listOf(
            EvidenceRecord("e1", "S1", "Revenue grew", "grew 10%", EvidenceStance.SUPPORTS, 0.9),
            EvidenceRecord("e2", "S2", "Revenue grew", "revised lower", EvidenceStance.CONTRADICTS, 0.8),
        )
        val (claims, contradictions) = buildClaims(evidence)
        assertEquals(1, claims.size)
        assertEquals(listOf("e1"), claims.single().supportingEvidenceIds)
        assertEquals(listOf("e2"), claims.single().contradictingEvidenceIds)
        assertTrue(contradictions.isNotEmpty())
    }

    @Test fun `neutral evidence is retained without creating false contradiction`() {
        val evidence = listOf(
            EvidenceRecord("e1", "S1", "A fact", "context", EvidenceStance.NEUTRAL, 0.5),
        )
        val (claims, contradictions) = buildClaims(evidence)
        assertEquals(1, claims.size)
        assertTrue(claims.single().supportingEvidenceIds.isEmpty())
        assertTrue(contradictions.isEmpty())
    }
}
