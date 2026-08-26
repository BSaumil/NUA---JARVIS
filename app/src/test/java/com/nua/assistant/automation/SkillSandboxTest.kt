package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.voice.NuaLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private val INTENT = ClassifiedIntent(NuaActionType.SMS_SEND, confidence = 0.9, parameters = mapOf("to" to "Alex"))

private class FakeSkill(
    override val manifest: SkillManifest,
    private val body: suspend (ClassifiedIntent) -> NuaRouteResult = { NuaRouteResult.ActionTaken("ok") },
) : NuaSkill {
    var invokedWith: ClassifiedIntent? = null
    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        invokedWith = intent
        return body(intent)
    }
}

/**
 * Exercises the real coroutine behaviour of [executeSandboxed] — timeout, cancellation,
 * and exception mapping — none of which [SkillManifestTest] covers, since that file only
 * tests the pure parameter/permission functions [executeSandboxed] calls, not the
 * orchestration and suspend machinery around them. This is the gap an architecture
 * review flagged: SkillSandbox, the single enforcement point every skill execution
 * passes through, had no test at all.
 */
class SkillSandboxTest {

    @Test
    fun `a missing required parameter falls through to chat without ever calling the skill`() = runTest {
        val skill = FakeSkill(SkillManifest(parameters = listOf(SkillParameter("to", required = true))))
        val result = executeSandboxed(skill, INTENT.copy(parameters = emptyMap()), "text alex", null, hasPermission = { true })
        assertEquals(NuaRouteResult.FallThroughToChat, result)
        assertEquals(null, skill.invokedWith)
    }

    @Test
    fun `an ungranted required permission refuses without calling the skill`() {
        runTest {
            val skill = FakeSkill(SkillManifest(requiredPermissions = listOf("android.permission.SEND_SMS")))
            val result = executeSandboxed(skill, INTENT, "text alex", null, hasPermission = { false })
            assertTrue(result is NuaRouteResult.ActionTaken)
            assertTrue((result as NuaRouteResult.ActionTaken).succeeded.not())
            assertEquals(null, skill.invokedWith)
        }
    }

    @Test
    fun `undeclared parameters never reach the skill`() {
        runTest {
            val skill = FakeSkill(SkillManifest(parameters = listOf(SkillParameter("to"))))
            val dirty = INTENT.copy(parameters = mapOf("to" to "Alex", "message" to "malicious payload"))
            executeSandboxed(skill, dirty, "text alex", null, hasPermission = { true })
            assertEquals(setOf("to"), skill.invokedWith?.parameters?.keys)
        }
    }

    @Test
    fun `a skill that exceeds its declared timeout is stopped, not left hanging`() = runTest {
        val skill = FakeSkill(SkillManifest(timeoutMillis = 100)) { delay(10_000); NuaRouteResult.ActionTaken("too late") }
        var timedOutCallback = false
        val result = executeSandboxed(
            skill, INTENT, "text alex", null, hasPermission = { true },
            onTimedOut = { timedOutCallback = true },
        )
        assertTrue(result is NuaRouteResult.ActionTaken)
        assertTrue((result as NuaRouteResult.ActionTaken).succeeded.not())
        assertTrue("the timeout callback must fire so the sandbox can log what happened", timedOutCallback)
    }

    @Test
    fun `a skill that throws degrades to a reported failure, never a crash`() = runTest {
        val skill = FakeSkill(SkillManifest()) { throw IllegalStateException("boom") }
        var threwCallback: Exception? = null
        val result = executeSandboxed(skill, INTENT, "text alex", null, hasPermission = { true }, onThrew = { threwCallback = it })
        assertTrue(result is NuaRouteResult.ActionTaken)
        assertTrue((result as NuaRouteResult.ActionTaken).message.contains("boom"))
        assertEquals("boom", threwCallback?.message)
    }

    @Test
    fun `a skill throwing without a message still degrades to a readable failure`() = runTest {
        val skill = FakeSkill(SkillManifest()) { throw IllegalStateException() }
        val result = executeSandboxed(skill, INTENT, "text alex", null, hasPermission = { true }) as NuaRouteResult.ActionTaken
        assertTrue(result.message.contains("IllegalStateException"))
    }

    @Test
    fun `genuine cancellation is never swallowed as a failure`() = runTest {
        val skill = FakeSkill(SkillManifest()) { throw CancellationException("scope cleared") }
        try {
            executeSandboxed(skill, INTENT, "text alex", null, hasPermission = { true })
            fail("CancellationException must propagate, not be converted into an ActionTaken failure")
        } catch (expected: CancellationException) {
            // expected — this is the one exception type the sandbox must not contain.
        }
    }

    @Test
    fun `a clean run reaches the skill with its declared parameters intact`() = runTest {
        val skill = FakeSkill(SkillManifest(parameters = listOf(SkillParameter("to", required = true))))
        val result = executeSandboxed(skill, INTENT, "text alex", null, hasPermission = { true })
        assertEquals(NuaRouteResult.ActionTaken("ok"), result)
        assertEquals(mapOf("to" to "Alex"), skill.invokedWith?.parameters)
    }

    @Test
    fun `preflight order is parameters before permissions`() = runTest {
        // A request missing a required parameter AND lacking a required permission must
        // fall through to chat (fixable by asking a follow-up), not report a permission
        // refusal the user can do nothing about from chat.
        val skill = FakeSkill(
            SkillManifest(parameters = listOf(SkillParameter("to", required = true)), requiredPermissions = listOf("x")),
        )
        val result = executeSandboxed(skill, INTENT.copy(parameters = emptyMap()), "text", null, hasPermission = { false })
        assertEquals(NuaRouteResult.FallThroughToChat, result)
    }
}
