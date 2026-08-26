package com.nua.assistant.trust

import com.nua.assistant.memory.ActionOutcomeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun outcome(succeeded: Boolean, wasRejection: Boolean = false) =
    ActionOutcomeEntity(actionType = "X", tier = AutonomyTier.T1, summary = "s", succeeded = succeeded, wasRejection = wasRejection)

/**
 * The Trust Engine's headline number had no test at all despite being pure — flagged in
 * an architecture review alongside the other untested security-adjacent files.
 */
class TrustScoreEngineTest {

    @Test
    fun `no history at all scores perfect trust`() {
        assertEquals(100, TrustScoreEngine.score(emptyList()))
    }

    @Test
    fun `all successes scores perfect trust`() {
        assertEquals(100, TrustScoreEngine.score(List(5) { outcome(succeeded = true) }))
    }

    @Test
    fun `all failures scores zero`() {
        assertEquals(0, TrustScoreEngine.score(List(5) { outcome(succeeded = false) }))
    }

    @Test
    fun `a rejection counts for half a failure, not a full one`() {
        // The discount only shows up against a mixed history — an all-bad batch scores 0
        // either way, since the weight is proportional in both the numerator and the
        // denominator. Three successes plus one bad outcome isolates the difference.
        val threeGoodOneRejection = List(3) { outcome(true) } + outcome(succeeded = false, wasRejection = true)
        val threeGoodOneFailure = List(3) { outcome(true) } + outcome(succeeded = false, wasRejection = false)
        val rejectionScore = TrustScoreEngine.score(threeGoodOneRejection)
        val failureScore = TrustScoreEngine.score(threeGoodOneFailure)
        assertTrue("a rejection must hurt the score less than an equivalent failure: " +
            "rejection=$rejectionScore failure=$failureScore", rejectionScore > failureScore)
        assertEquals(75, failureScore)
        assertEquals(86, rejectionScore)
    }

    @Test
    fun `mixed history lands between the extremes`() {
        val score = TrustScoreEngine.score(listOf(outcome(true), outcome(true), outcome(true), outcome(false)))
        assertEquals(75, score)
    }

    @Test
    fun `score is always clamped to 0 to 100`() {
        val score = TrustScoreEngine.score(List(100) { outcome(succeeded = false) })
        assertEquals(0, score)
        assertEquals(0, score.coerceIn(0, 100))
    }
}
