package com.nua.assistant.ui.trust

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.TrustLedgerEntity
import com.nua.assistant.trust.TrustEventType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [refreshedTrustState] and [autonomySuggestionsAfterEnabling] — the two pure
 * functions [TrustUiController.refresh]/[TrustUiController.enableAutoApprove] delegate
 * to. [TrustUiController] itself cannot be constructed in this test: [TrustRepository]
 * builds `SharedPreferences` from a real Android `Context` in its own constructor, and
 * this project has no Robolectric — so nothing holding a real `TrustRepository`
 * reference can be instantiated on the plain JVM. That's the same wall
 * `SkillSandbox`/`WorkerRetryPolicy` hit earlier this session, and the same fix: extract
 * the actual behaviour into a function that takes its data sources as lambdas instead of
 * the whole dependency, and test that directly.
 *
 * This means several of the ten behaviours an architecture review asked this extraction
 * to prove are, as stated, integration-shaped rather than unit-testable here — see the
 * final report for exactly which, and why redesigning TrustRepository to make them
 * mockable was ruled out as out of scope for this seam.
 */
class TrustUiControllerTest {

    private fun ledgerEntry(type: TrustEventType = TrustEventType.MISREAD_INTENT) =
        TrustLedgerEntity(type = type, description = "d")

    private fun preference(actionType: NuaActionType, approvedCount: Int, autoApproveEnabled: Boolean) =
        AutonomyPreferenceEntity(actionType = actionType.name, approvedCount = approvedCount, autoApproveEnabled = autoApproveEnabled)

    // -----------------------------------------------------------------------------------
    // refreshedTrustState — covers directive items 3, 4, 6 (score/ledger/suggestions
    // propagated correctly) and 9 (repeated calls don't corrupt state — each call is
    // independent, no shared mutable state between invocations).
    // -----------------------------------------------------------------------------------

    @Test
    fun `assembles score, ledger, and suggestions from their three sources`() = runTest {
        val ledger = listOf(ledgerEntry())
        val suggestions = listOf(preference(NuaActionType.SMS_SEND, approvedCount = 5, autoApproveEnabled = false))

        val state = refreshedTrustState(
            scoreSnapshot = { 87 },
            recentLedger = { ledger },
            autonomySuggestions = { suggestions },
        )

        assertEquals(87, state.trustScore)
        assertEquals(ledger, state.trustLedger)
        assertEquals(suggestions, state.autonomySuggestions)
    }

    @Test
    fun `an empty ledger and no suggestions produce an empty, not null, state`() = runTest {
        val state = refreshedTrustState(scoreSnapshot = { 100 }, recentLedger = { emptyList() }, autonomySuggestions = { emptyList() })
        assertEquals(100, state.trustScore)
        assertTrue(state.trustLedger.isEmpty())
        assertTrue(state.autonomySuggestions.isEmpty())
    }

    @Test
    fun `repeated calls are independent — the second reflects only its own sources`() = runTest {
        val first = refreshedTrustState({ 40 }, { listOf(ledgerEntry()) }, { emptyList() })
        val second = refreshedTrustState({ 90 }, { emptyList() }, { listOf(preference(NuaActionType.PLAN_TASK, 5, false)) })

        assertEquals(40, first.trustScore)
        assertEquals(1, first.trustLedger.size)
        assertTrue(first.autonomySuggestions.isEmpty())

        assertEquals(90, second.trustScore)
        assertTrue(second.trustLedger.isEmpty())
        assertEquals(1, second.autonomySuggestions.size)
    }

    @Test(expected = IllegalStateException::class)
    fun `a failing source propagates uncaught, matching NuaViewModel's other refresh functions`() = runTest {
        // None of NuaViewModel's refresh* functions (refreshUsage, refreshDiagnostics,
        // the pre-extraction refreshTrust) ever caught a repository exception — a
        // failure surfaces through the launched coroutine exactly as it always has.
        // Adding a try/catch here would be new behaviour, not preserved behaviour.
        refreshedTrustState(scoreSnapshot = { throw IllegalStateException("boom") }, recentLedger = { emptyList() }, autonomySuggestions = { emptyList() })
    }

    // -----------------------------------------------------------------------------------
    // autonomySuggestionsAfterEnabling — covers directive item 7 (enableAutoApprove
    // behaves exactly as before): the write must land before the read, or the action
    // type just enabled would incorrectly still appear in its own suggestion list.
    // -----------------------------------------------------------------------------------

    @Test
    fun `the write happens before the read`() = runTest {
        var written = false
        val result = autonomySuggestionsAfterEnabling(
            enableAutoApprove = { written = true },
            autonomySuggestions = {
                assertTrue("autonomySuggestions must be read after enableAutoApprove, not before", written)
                listOf(preference(NuaActionType.SMS_SEND, 5, true))
            },
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `the newly enabled action type is gone from the returned suggestions`() = runTest {
        // Simulates the real TrustRepository.autonomySuggestions() filter
        // (isAutonomySuggested requires !autoApproveEnabled): once enabled, the entry
        // this call just turned on is excluded from what it returns.
        var enabled = false
        val result = autonomySuggestionsAfterEnabling(
            enableAutoApprove = { enabled = true },
            autonomySuggestions = {
                listOfNotNull(
                    preference(NuaActionType.SMS_SEND, 5, autoApproveEnabled = enabled).takeIf { !enabled },
                    preference(NuaActionType.PLAN_TASK, 6, autoApproveEnabled = false),
                )
            },
        )
        assertEquals(listOf(NuaActionType.PLAN_TASK.name), result.map { it.actionType })
    }
}
