package com.nua.assistant.security.guardian

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.IdempotencyPolicy
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.Reversibility
import com.nua.assistant.automation.uaf.SideEffectClass
import com.nua.assistant.memory.AutonomyContractEntity
import com.nua.assistant.trust.AutonomyTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_000_000L

private fun contract(
    actionType: String = NuaActionType.SMS_SEND.name,
    riskCeiling: AutonomyTier? = null,
    maxPerWindow: Int? = null,
    expiresAt: Long = NOW + 1000,
    active: Boolean = true,
) = AutonomyContractEntity(actionType = actionType, riskCeiling = riskCeiling, maxPerWindow = maxPerWindow, expiresAt = expiresAt, active = active)

class RuntimeSafetySentinelTest {

    @Test
    fun `an unremarkable contract raises no anomaly`() {
        assertTrue(auditContracts(listOf(contract()), NOW).isEmpty())
    }

    @Test
    fun `an expired contract still marked active is flagged as stale`() {
        val anomalies = auditContracts(listOf(contract(expiresAt = NOW - 1, active = true)), NOW)
        assertEquals(1, anomalies.size)
        assertTrue(anomalies[0].description.contains("stale"))
    }

    @Test
    fun `an expired contract already marked inactive raises no stale-row anomaly`() {
        assertTrue(auditContracts(listOf(contract(expiresAt = NOW - 1, active = false)), NOW).isEmpty())
    }

    @Test
    fun `a risk ceiling below the action type's fixed tier is flagged as permanently void`() {
        // SMS_SEND is fixed at T3 -- a ceiling of T1 can never permit it.
        val anomalies = auditContracts(listOf(contract(riskCeiling = AutonomyTier.T1)), NOW)
        assertEquals(1, anomalies.size)
        assertTrue(anomalies[0].description.contains("never permit"))
    }

    @Test
    fun `a risk ceiling at or above the fixed tier raises no anomaly`() {
        assertTrue(auditContracts(listOf(contract(riskCeiling = AutonomyTier.T3)), NOW).isEmpty())
    }

    @Test
    fun `a zero or negative frequency cap is flagged as permanently void`() {
        assertEquals(1, auditContracts(listOf(contract(maxPerWindow = 0)), NOW).size)
        assertEquals(1, auditContracts(listOf(contract(maxPerWindow = -5)), NOW).size)
    }

    @Test
    fun `a positive frequency cap raises no anomaly`() {
        assertTrue(auditContracts(listOf(contract(maxPerWindow = 3)), NOW).isEmpty())
    }

    @Test
    fun `an actionType naming no real NuaActionType is flagged as orphaned, and skips the other checks`() {
        val anomalies = auditContracts(listOf(contract(actionType = "NOT_A_REAL_ACTION", riskCeiling = AutonomyTier.T0)), NOW)
        assertEquals(1, anomalies.size)
        assertTrue(anomalies[0].description.contains("orphaned"))
    }

    @Test
    fun `multiple contracts are each audited independently`() {
        val anomalies = auditContracts(
            listOf(contract(actionType = "SMS_SEND", riskCeiling = AutonomyTier.T1), contract(actionType = "OPEN_APP")),
            NOW,
        )
        assertEquals(1, anomalies.size)
        assertEquals("SMS_SEND", anomalies[0].subject)
    }

    private fun descriptor(action: NuaActionType) = CapabilityDescriptor(
        action = action, purpose = "p", parameters = emptyList(), riskTier = AutonomyTier.T1, permissions = emptyList(),
        sideEffect = SideEffectClass.NONE, reversibility = Reversibility.REVERSIBLE, idempotency = IdempotencyPolicy.SAFE_TO_REPEAT,
        confirmation = ConfirmationPolicy.NONE_REQUIRED, timeoutMillis = 1000, preferredAdapter = ExecutionAdapterType.LOCAL_NATIVE,
    )

    private fun step(action: NuaActionType, id: String = "step-0") =
        PlanStep(id = id, action = action, authorizationProof = AuthorizationProof.NotRequired, failurePolicy = FailurePolicy.SKIP)

    @Test
    fun `auditRecipeSteps flags a step whose capability is no longer registered`() {
        val anomalies = auditRecipeSteps(listOf(step(NuaActionType.SMS_SEND)), descriptorFor = { null })
        assertEquals(1, anomalies.size)
        assertEquals("step-0", anomalies[0].subject)
    }

    @Test
    fun `auditRecipeSteps raises no anomaly when every step's capability is still registered`() {
        val anomalies = auditRecipeSteps(listOf(step(NuaActionType.GET_WEATHER)), descriptorFor = { descriptor(it) })
        assertTrue(anomalies.isEmpty())
    }
}
