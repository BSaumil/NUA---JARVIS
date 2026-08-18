package com.nua.assistant.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The animation timing scale (`#41`). Three named bands rather than a scatter of magic
 * numbers, so motion across NUA feels like one system:
 *
 *  - [INSTANT] — immediate feedback on a direct touch. Fast enough to read as "the tap
 *    registered", not as an animation.
 *  - [TRANSITION] — moving between states or surfaces.
 *  - [EXPRESSIVE] / [EXPRESSIVE_SLOW] — deliberate, noticeable movement, used where the
 *    motion itself is carrying meaning (the Orb, a hero card settling).
 *
 * Ambient, indefinitely-looping motion — the Orb's breath — deliberately sits *outside*
 * this scale and is far slower. A "breathing" element cycling at 600ms reads as
 * panicking, not resting.
 */
object NuaMotion {
    const val INSTANT: Int = 150
    const val TRANSITION: Int = 250
    const val EXPRESSIVE: Int = 450
    const val EXPRESSIVE_SLOW: Int = 600
}

/**
 * Pure: the duration to actually use, given whether the user allows motion. Returns 0 —
 * an instant cut rather than a slow one — when motion is off, so state still changes but
 * nothing moves. Unit-tested, since "respects reduced motion" is the kind of claim that
 * quietly stops being true.
 */
fun motionDuration(base: Int, motionEnabled: Boolean): Int = if (motionEnabled) base else 0

/**
 * Whether the user permits animation. Reads the system animator duration scale, which is
 * 0 when animations are turned off — via Developer options, or "Remove animations" under
 * Accessibility on most devices.
 *
 * This lives here rather than inside a single component so reduced motion is honoured
 * app-wide: it was originally private to the Orb, which meant every other animated
 * surface silently ignored the setting.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * A short confirmation tap for actions that change something in the world — sending a
 * text, confirming a plan. Deliberately only on *commit*, never on ordinary navigation:
 * haptics everywhere is the same mistake as colour everywhere, and stops meaning
 * "something just happened".
 */
@Composable
fun rememberCommitHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
}
