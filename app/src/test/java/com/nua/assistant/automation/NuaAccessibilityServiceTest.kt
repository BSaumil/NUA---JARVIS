package com.nua.assistant.automation

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers only what's deterministic without a running [android.accessibilityservice.AccessibilityService]
 * instance (which needs a real device/emulator, or Robolectric's AccessibilityService
 * shadow -- not attempted here, named honestly rather than claiming coverage this test
 * doesn't actually have): the disconnected-service safety net. A fresh JVM test process
 * never calls `onServiceConnected`, so [NuaAccessibilityService.performSystemNavigation]'s
 * `instance` is always null here -- exactly the real "user hasn't enabled Tier 2" case.
 */
class NuaAccessibilityServiceTest {

    @Test
    fun `performSystemNavigation fails safely when the service isn't connected, regardless of command`() {
        assertFalse(NuaAccessibilityService.performSystemNavigation(NAV_COMMAND_HOME))
        assertFalse(NuaAccessibilityService.performSystemNavigation(NAV_COMMAND_BACK))
        assertFalse(NuaAccessibilityService.performSystemNavigation(NAV_COMMAND_RECENTS))
        assertFalse(NuaAccessibilityService.performSystemNavigation("not a real command"))
    }

    @Test
    fun `an unconnected service reports why, not just a bare false`() {
        NuaAccessibilityService.performSystemNavigation(NAV_COMMAND_HOME)
        assertFalse(NuaAccessibilityService.lastFailure.value.isNullOrBlank())
    }
}
