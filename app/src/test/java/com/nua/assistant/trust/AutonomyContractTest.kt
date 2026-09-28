package com.nua.assistant.trust

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.AutonomyContractEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_000_000L

private fun contract(
    actionType: String = NuaActionType.SMS_SEND.name,
    recipient: String? = null,
    riskCeiling: AutonomyTier? = null,
    maxPerWindow: Int? = null,
    windowMillis: Long? = null,
    expiresAt: Long = NOW + 1000,
    active: Boolean = true,
) = AutonomyContractEntity(
    actionType = actionType,
    recipient = recipient,
    riskCeiling = riskCeiling,
    maxPerWindow = maxPerWindow,
    windowMillis = windowMillis,
    expiresAt = expiresAt,
    active = active,
)

class AutonomyContractTest {

    @Test
    fun `an unrestricted contract permits any recipient, tier, and frequency`() {
        val decision = evaluateContract(contract(), NuaActionType.SMS_SEND, recipient = "+15551234567", now = NOW, recentCommittedCountInWindow = 0)
        assertEquals(ContractDecision.Permit, decision)
    }

    @Test
    fun `a suspended contract denies regardless of every other dimension`() {
        val decision = evaluateContract(contract(active = false), NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        assertTrue(decision is ContractDecision.Deny)
    }

    @Test
    fun `an expired contract denies`() {
        val decision = evaluateContract(contract(expiresAt = NOW - 1), NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        assertTrue(decision is ContractDecision.Deny)
    }

    @Test
    fun `a contract exactly at its expiry moment is already expired -- boundary is exclusive`() {
        val decision = evaluateContract(contract(expiresAt = NOW), NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        assertTrue(decision is ContractDecision.Deny)
    }

    @Test
    fun `a scoped contract denies a different recipient`() {
        val decision = evaluateContract(contract(recipient = "+15551234567"), NuaActionType.SMS_SEND, recipient = "+19998887777", now = NOW, recentCommittedCountInWindow = 0)
        assertTrue(decision is ContractDecision.Deny)
    }

    @Test
    fun `a scoped contract permits the exact matching recipient`() {
        val decision = evaluateContract(contract(recipient = "+15551234567"), NuaActionType.SMS_SEND, recipient = "+15551234567", now = NOW, recentCommittedCountInWindow = 0)
        assertEquals(ContractDecision.Permit, decision)
    }

    @Test
    fun `a risk ceiling below the action type's fixed tier voids the contract`() {
        // SMS_SEND is fixed at T3 (see AutonomyTier.kt's autonomyTierFor) -- a ceiling of T2 must never permit it.
        val decision = evaluateContract(contract(riskCeiling = AutonomyTier.T2), NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        assertTrue(decision is ContractDecision.Deny)
    }

    @Test
    fun `a risk ceiling at or above the action type's fixed tier permits it`() {
        val decision = evaluateContract(contract(riskCeiling = AutonomyTier.T3), NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        assertEquals(ContractDecision.Permit, decision)
    }

    @Test
    fun `a frequency cap at or above the recent count denies`() {
        val c = contract(maxPerWindow = 3, windowMillis = 60_000)
        val decision = evaluateContract(c, NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 3)
        assertTrue(decision is ContractDecision.Deny)
    }

    @Test
    fun `a frequency cap under the recent count permits`() {
        val c = contract(maxPerWindow = 3, windowMillis = 60_000)
        val decision = evaluateContract(c, NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 2)
        assertEquals(ContractDecision.Permit, decision)
    }

    @Test
    fun `fewer than three recent outcomes never trips drift detection`() {
        assertEquals(false, contractShouldSuspend(listOf(ActionOutcomeState.FAILED, ActionOutcomeState.FAILED)))
    }

    @Test
    fun `two failures in the last three attempts trips drift detection`() {
        val outcomes = listOf(ActionOutcomeState.FAILED, ActionOutcomeState.FAILED, ActionOutcomeState.COMPLETED)
        assertEquals(true, contractShouldSuspend(outcomes))
    }

    @Test
    fun `one failure in the last three attempts does not trip drift detection`() {
        val outcomes = listOf(ActionOutcomeState.FAILED, ActionOutcomeState.COMPLETED, ActionOutcomeState.COMPLETED)
        assertEquals(false, contractShouldSuspend(outcomes))
    }

    @Test
    fun `a failure outside the lookback window is never counted`() {
        // Newest-first: the 2 failures are 4th and 5th -- outside the default 3-attempt lookback.
        val outcomes = listOf(
            ActionOutcomeState.COMPLETED, ActionOutcomeState.COMPLETED, ActionOutcomeState.COMPLETED,
            ActionOutcomeState.FAILED, ActionOutcomeState.FAILED,
        )
        assertEquals(false, contractShouldSuspend(outcomes))
    }
}
