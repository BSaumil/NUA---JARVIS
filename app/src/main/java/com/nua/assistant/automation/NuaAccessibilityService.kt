package com.nua.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NuaAccessibilityService"

/** Known [NuaAccessibilityService.performSystemNavigation] commands — the first real
 *  Tier 2 action (directive item 14). Kept as plain strings, not an enum, since this is
 *  the exact same "command" parameter shape [com.nua.assistant.automation.MediaControlSkill]
 *  already uses for the identical reason: it travels through [ClassifiedIntent.parameters]
 *  (`Map<String, String>`), which isn't natively typed. */
internal const val NAV_COMMAND_HOME = "home"
internal const val NAV_COMMAND_BACK = "back"
internal const val NAV_COMMAND_RECENTS = "recents"

/**
 * Tier 2: accessibility-based automation for actions with no official API path. Off by
 * default — the user must explicitly enable this under Android Settings > Accessibility;
 * NUA never prompts for it as part of normal onboarding.
 *
 * [performSystemNavigation] is the first real Tier 2 action (directive item 14):
 * [AccessibilityService.performGlobalAction] is genuinely accessibility-exclusive — no
 * other public API lets a third-party app press the device's own home/back/recents
 * buttons without Device Admin (a much heavier, unrelated permission model NUA doesn't
 * use elsewhere). Deliberately the *simplest* real accessibility action available: every
 * one of these gestures is [com.nua.assistant.automation.uaf.Reversibility.REVERSIBLE]
 * and [com.nua.assistant.automation.uaf.IdempotencyPolicy.SAFE_TO_REPEAT] — nothing it
 * does can corrupt state or require undoing, unlike tapping an arbitrary button in a
 * third-party app's UI (a real future Tier 2 action, but one that needs its own
 * reversibility/confirmation review this slice deliberately doesn't attempt).
 *
 * - A failed action must surface as a failure via [lastFailure], never fail silently —
 *   Tier 2 breaks when target app UIs change (or, here, when the user hasn't enabled the
 *   service at all), and pretending otherwise is worse than erroring loudly.
 */
class NuaAccessibilityService : AccessibilityService() {

    companion object {
        private val _lastFailure = MutableStateFlow<String?>(null)
        val lastFailure: StateFlow<String?> = _lastFailure.asStateFlow()

        /** The live, connected instance, or null when the user hasn't enabled the
         *  service — [performSystemNavigation] is a companion function (callable from a
         *  plain [com.nua.assistant.automation.NuaSkill] with no service reference of its
         *  own) precisely so a caller never needs to null-check this directly. */
        private var instance: NuaAccessibilityService? = null

        internal fun reportFailure(reason: String) {
            Log.w(TAG, "Tier 2 action failed: $reason")
            _lastFailure.value = reason
        }

        /** Whether the user has enabled NUA's Tier 2 service under system Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, NuaAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        /**
         * Performs a device navigation gesture via [AccessibilityService.performGlobalAction].
         * Returns false (and records [lastFailure]) when the service isn't connected (the
         * user hasn't enabled Tier 2) or [command] isn't one of [NAV_COMMAND_HOME]/
         * [NAV_COMMAND_BACK]/[NAV_COMMAND_RECENTS] — never throws, matching every other
         * [com.nua.assistant.automation.NuaSkill]'s "report, don't crash" contract.
         */
        fun performSystemNavigation(command: String): Boolean {
            val service = instance
            if (service == null) {
                reportFailure("Tier 2 isn't enabled — enable NUA under Android Settings > Accessibility first.")
                return false
            }
            val globalAction = when (command) {
                NAV_COMMAND_HOME -> AccessibilityService.GLOBAL_ACTION_HOME
                NAV_COMMAND_BACK -> AccessibilityService.GLOBAL_ACTION_BACK
                NAV_COMMAND_RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
                else -> {
                    reportFailure("Unknown system navigation command: \"$command\".")
                    return false
                }
            }
            val performed = service.performGlobalAction(globalAction)
            if (!performed) reportFailure("performGlobalAction(\"$command\") was rejected by the system.")
            return performed
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "NUA accessibility service connected (Tier 2: system navigation).")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event-driven Tier 2 action exists yet -- performSystemNavigation is invoked
        // directly, not in response to an accessibility event.
    }

    override fun onInterrupt() {
        reportFailure("Accessibility service interrupted by the system mid-action.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }
}
