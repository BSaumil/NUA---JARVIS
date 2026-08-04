package com.nua.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NuaAccessibilityService"

/**
 * Tier 2 skeleton: accessibility-based automation for actions with no official API
 * path. Off by default — the user must explicitly enable this under Android Settings >
 * Accessibility; NUA never prompts for it as part of normal onboarding. No concrete
 * Tier 2 action is wired up yet (Phase 3 is Tier 1 only); this exists so the
 * confirmation contract below is settled before any action is added on top of it:
 *
 * - Every action this service performs must be requested with an explicit, in-the-moment
 *   user confirmation (a granted [pendingActionConfirmed] is not reusable/schedulable).
 * - A failed action must surface as a failure via [lastFailure], never fail silently —
 *   Tier 2 breaks when target app UIs change, and pretending otherwise is worse than
 *   erroring loudly.
 */
class NuaAccessibilityService : AccessibilityService() {

    companion object {
        private val _lastFailure = MutableStateFlow<String?>(null)
        val lastFailure: StateFlow<String?> = _lastFailure.asStateFlow()

        internal fun reportFailure(reason: String) {
            Log.w(TAG, "Tier 2 action failed: $reason")
            _lastFailure.value = reason
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "NUA accessibility service connected (Tier 2, no actions wired up yet).")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No Tier 2 action currently implemented; this is intentionally inert until one
        // is added, each requiring its own explicit per-action confirmation flow.
    }

    override fun onInterrupt() {
        reportFailure("Accessibility service interrupted by the system mid-action.")
    }
}
