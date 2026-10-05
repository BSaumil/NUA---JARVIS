package com.nua.assistant.automation

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers only what's deterministic without a running [android.accessibilityservice.AccessibilityService]
 * instance (which needs a real device/emulator, or Robolectric's AccessibilityService
 * shadow -- not attempted here, named honestly rather than claiming coverage this test
 * doesn't actually have): the disconnected-service safety net. A fresh test process never
 * calls `onServiceConnected`, so [NuaAccessibilityService.performSystemNavigation]'s
 * `instance` is always null here -- exactly the real "user hasn't enabled Tier 2" case.
 *
 * Runs under Robolectric (not a plain JVM test) because [NuaAccessibilityService.reportFailure]
 * calls [android.util.Log.w] on the real `android.util.Log` class, which throws
 * `"...not mocked"` outside an Android runtime -- the exact class of problem directive
 * item 13 (Robolectric) exists to let a test like this one actually run.
 * `@Config(application = Application::class)` keeps this to a bare Application, same
 * reasoning as `DecisionDaoRobolectricTest`'s: this test needs an Android runtime for
 * `Log`, not NUA's whole Hilt/WorkManager-initializing `NuaApplication`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
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
