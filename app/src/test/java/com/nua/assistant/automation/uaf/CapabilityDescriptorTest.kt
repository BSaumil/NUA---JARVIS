package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.trust.autonomyTierFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [capabilityDescriptorFor]'s classification `when` has no `else` branch, the same
 * exhaustiveness guarantee [autonomyTierFor] already relies on — this test exercises every
 * current [NuaActionType] explicitly so a future value that somehow compiled with a
 * silently-wrong classification would still be caught here, not just by the compiler.
 */
class CapabilityDescriptorTest {

    @Test
    fun `every action type produces a descriptor whose risk tier matches the existing sandbox mapping`() {
        for (action in NuaActionType.entries) {
            val descriptor = capabilityDescriptorFor(action, SkillManifest())
            assertEquals("descriptor riskTier must never diverge from autonomyTierFor for $action", autonomyTierFor(action), descriptor.riskTier)
        }
    }

    @Test
    fun `every action type has a non-blank purpose string`() {
        for (action in NuaActionType.entries) {
            assertTrue("purpose for $action must not be blank", capabilityDescriptorFor(action, SkillManifest()).purpose.isNotBlank())
        }
    }

    @Test
    fun `descriptor parameters and permissions are read verbatim from the skill's own manifest`() {
        val manifest = SkillManifest(
            parameters = listOf(com.nua.assistant.automation.SkillParameter("to", required = true)),
            requiredPermissions = listOf("android.permission.SEND_SMS"),
            timeoutMillis = 12_345L,
        )
        val descriptor = capabilityDescriptorFor(NuaActionType.SMS_SEND, manifest)
        assertEquals(listOf("to"), descriptor.parameters)
        assertEquals(listOf("android.permission.SEND_SMS"), descriptor.permissions)
        assertEquals(12_345L, descriptor.timeoutMillis)
    }

    @Test
    fun `sensitive irreversible actions require confirmation before execute`() {
        assertEquals(ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE, capabilityDescriptorFor(NuaActionType.SMS_SEND, SkillManifest()).confirmation)
        assertEquals(ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE, capabilityDescriptorFor(NuaActionType.REPLY_TO_NOTIFICATION, SkillManifest()).confirmation)
        assertEquals(Reversibility.IRREVERSIBLE, capabilityDescriptorFor(NuaActionType.SMS_SEND, SkillManifest()).reversibility)
    }

    @Test
    fun `read-only actions never require confirmation and have no side effect`() {
        val descriptor = capabilityDescriptorFor(NuaActionType.GET_WEATHER, SkillManifest())
        assertEquals(ConfirmationPolicy.NONE_REQUIRED, descriptor.confirmation)
        assertEquals(SideEffectClass.NONE, descriptor.sideEffect)
    }

    @Test
    fun `REPLY_TO_NOTIFICATION names NOTIFICATION_REMOTE_INPUT as its fallback adapter, no other action does`() {
        assertEquals(listOf(ExecutionAdapterType.NOTIFICATION_REMOTE_INPUT), capabilityDescriptorFor(NuaActionType.REPLY_TO_NOTIFICATION, SkillManifest()).fallbackAdapters)
        for (action in NuaActionType.entries.filter { it != NuaActionType.REPLY_TO_NOTIFICATION }) {
            assertTrue("$action must not carry the REPLY_TO_NOTIFICATION-only fallback", capabilityDescriptorFor(action, SkillManifest()).fallbackAdapters.isEmpty())
        }
    }

    @Test
    fun `every descriptor defaults to the local-native adapter as preferred`() {
        for (action in NuaActionType.entries) {
            assertEquals(ExecutionAdapterType.LOCAL_NATIVE, capabilityDescriptorFor(action, SkillManifest()).preferredAdapter)
        }
    }
}
