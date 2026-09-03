package com.nua.assistant.context

/**
 * What "What should I do now?" actually returns — three distinct states, not one string,
 * so the UI (and the user) can tell "nothing needs attention" apart from "NUA couldn't
 * work it out." Collapsing those into the same free-text blob was the gap: the roadmap's
 * signature experience explicitly wants an honest `Nothing unusual - you're clear`
 * anti-case, not a plausible-sounding action invented to fill the space.
 */
sealed class WhatNowResult {
    /**
     * [action] and [reason] are always short — one clause each, matching what the system
     * prompt asks Claude for. [confidence] is 0-1. [estimatedMinutes] is null when Claude
     * doesn't have enough to estimate honestly, not defaulted to a made-up number.
     */
    data class Recommendation(
        val action: String,
        val reason: String,
        val confidence: Float,
        val estimatedMinutes: Int? = null,
    ) : WhatNowResult()

    /** The honest anti-case: nothing in the current situation points to a next action. */
    data object NothingNeedsAttention : WhatNowResult()

    /** The Claude call failed, or its reply couldn't be parsed. */
    data class Unavailable(val message: String) : WhatNowResult()
}
