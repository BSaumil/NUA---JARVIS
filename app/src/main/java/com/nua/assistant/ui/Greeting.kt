package com.nua.assistant.ui

import com.nua.assistant.state.NuaState
import java.util.Calendar

/**
 * Time-of-day greeting for the Command Centre header. Pure and hour-parameterised so it's
 * unit-testable without freezing a clock.
 *
 * No name is appended: NUA only knows the user's name if they happened to mention it, and
 * greeting someone by a name guessed from conversation is worse than not using one. If an
 * identity fact for the name is added later, this is the one place to thread it through.
 */
internal fun greetingForHour(hourOfDay: Int): String = when (hourOfDay) {
    in 0..4 -> "Still up?"
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

internal fun greetingFor(state: NuaState): String {
    val greeting = greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    // Offline is worth saying up front rather than burying below the fold.
    return if (state.isOffline) "$greeting — offline" else greeting
}
