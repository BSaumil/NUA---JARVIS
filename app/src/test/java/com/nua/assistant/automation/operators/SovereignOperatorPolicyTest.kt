package com.nua.assistant.automation.operators

import com.nua.assistant.ai.NuaActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun manifest(
    allowedActions: Set<String> = setOf(NuaActionType.GET_WEATHER.name),
    maxRuns: Int = 4,
    cooldown: Long = 15,
    schedule: OperatorSchedule = OperatorSchedule(
        type = OperatorScheduleType.INTERVAL,
        intervalMinutes = 30,
    ),
) = SovereignOperatorManifest(
    id = "weather-watch",
    name = "Weather watch",
    recipeId = 1,
    allowedActions = allowedActions,
    schedule = schedule,
    maxRunsPerDay = maxRuns,
    cooldownMinutes = cooldown,
)

class SovereignOperatorPolicyTest {
    @Test fun `valid manifest is accepted`() {
        assertTrue(validateOperator(manifest()).valid)
    }

    @Test fun `unknown action capability is rejected`() {
        assertFalse(validateOperator(manifest(allowedActions = setOf("SHELL_EXEC"))).valid)
    }

    @Test fun `interval below WorkManager minimum is rejected`() {
        assertFalse(
            validateOperator(
                manifest(schedule = OperatorSchedule(OperatorScheduleType.INTERVAL, intervalMinutes = 5)),
            ).valid,
        )
    }

    @Test fun `recipe capability envelope is deny by default`() {
        val m = manifest()
        assertTrue(isActionEnvelopeSatisfied(m, setOf(NuaActionType.GET_WEATHER)))
        assertFalse(isActionEnvelopeSatisfied(m, setOf(NuaActionType.GET_WEATHER, NuaActionType.SMS_SEND)))
    }

    @Test fun `cooldown refuses duplicate OS retry`() {
        val now = 2_000_000_000_000L
        val state = OperatorRuntimeState(
            epochDayUtc = now / 86_400_000L,
            runsToday = 1,
            lastRunAt = now - 60_000L,
        )
        assertFalse(operatorGate(manifest(cooldown = 15), state, now).allowed)
    }

    @Test fun `daily cap refuses extra autonomous runs`() {
        val now = 2_000_000_000_000L
        val state = OperatorRuntimeState(
            epochDayUtc = now / 86_400_000L,
            runsToday = 4,
            lastRunAt = now - 60L * 60_000L,
        )
        assertFalse(operatorGate(manifest(maxRuns = 4), state, now).allowed)
    }

    @Test fun `new UTC day resets daily cap`() {
        val now = 2_000_000_000_000L
        val state = OperatorRuntimeState(
            epochDayUtc = now / 86_400_000L - 1,
            runsToday = 4,
            lastRunAt = now - 60L * 60_000L,
        )
        assertTrue(operatorGate(manifest(maxRuns = 4), state, now).allowed)
    }
}
