package com.nua.assistant.state

/**
 * How loaded NUA thinks the user currently is. Derived from real counts (see
 * [deriveNuaState]) rather than being a mood NUA invents.
 */
enum class NuaFocus(val label: String) {
    CALM("Calm"),
    ATTENTIVE("Attentive"),
    BUSY("Busy"),
    OVERLOADED("Overloaded"),
}

/**
 * Whether the user is interruptible. [UNKNOWN] is a real, distinct answer — it means
 * NUA cannot read the calendar, not that the user is free. Collapsing it into [FREE]
 * would be NUA claiming to know something it doesn't.
 */
enum class UserAvailability(val label: String) {
    FREE("Free"),
    IN_EVENT("In something"),
    UNKNOWN("Unknown"),
}

/** Why something is waiting on the user, so the dashboard can group by kind. */
enum class PendingKind {
    NOTIFICATION,
    DOCUMENT_EXPIRY,
    VISION_RECHECK,
    GOAL_OBSERVATION,
}

data class PendingTask(val kind: PendingKind, val description: String)

/** Counts behind [NuaFocus], kept so the UI can show the arithmetic rather than just a verdict. */
data class Workload(
    val notificationsNeedingAttention: Int,
    val eventsRemainingToday: Int,
    val pendingItems: Int,
) {
    val total: Int get() = notificationsNeedingAttention + eventsRemainingToday + pendingItems
}

/**
 * NUA's internal state (`#8`), rendered by the Command Centre instead of a chat window.
 *
 * Every field is computed from data Phases 6–9 already produce — goals, trust outcomes,
 * notifications, calendar, documents, vision monitors. Nothing here is invented to make
 * the dashboard look alive: where a signal genuinely isn't available, the type says so
 * ([confidence] is null before there's any action history to score;
 * [UserAvailability.UNKNOWN] when the calendar can't be read) rather than defaulting to a
 * plausible-looking value.
 */
data class NuaState(
    val focus: NuaFocus,
    /** Trust score 0–100, or null when NUA hasn't taken enough actions to have earned one. */
    val confidence: Int?,
    /** The user's primary active goal, or null if they haven't set any. */
    val currentObjective: String?,
    val currentContext: String,
    val workload: Workload,
    val recentMistakes: List<String>,
    val pendingTasks: List<PendingTask>,
    val availability: UserAvailability,
    val isOffline: Boolean,
) {
    /** The one-line headline the Command Centre leads with. */
    val headline: String
        get() = when {
            isOffline -> "Offline — working from what's already on this device."
            pendingTasks.isEmpty() && workload.total == 0 -> "Nothing needs you right now."
            pendingTasks.size == 1 -> "1 thing needs your attention."
            pendingTasks.isNotEmpty() -> "${pendingTasks.size} things need your attention."
            else -> "Nothing pending, but today looks ${focus.label.lowercase()}."
        }
}

/** Raw signals for [deriveNuaState], gathered by `NuaStateRepository` from real sources. */
data class NuaStateInputs(
    val activeGoals: List<String> = emptyList(),
    /** Null when there's no action history yet — not zero, which would read as "untrustworthy". */
    val trustScore: Int? = null,
    val notificationsNeedingAttention: List<String> = emptyList(),
    val notificationsCanWait: Int = 0,
    val eventsRemainingToday: Int = 0,
    /** Null when the calendar can't be read at all (permission not granted). */
    val currentlyInEvent: Boolean? = null,
    val recentFailures: List<String> = emptyList(),
    val documentsExpiringSoon: List<String> = emptyList(),
    val visionMonitorsDue: List<String> = emptyList(),
    val recentGoalObservations: List<String> = emptyList(),
    val isOffline: Boolean = false,
)

private const val ATTENTIVE_THRESHOLD = 1
private const val BUSY_THRESHOLD = 4
private const val OVERLOADED_THRESHOLD = 8

/** How many recent failures the dashboard will own up to at once before it's just noise. */
private const val MAX_SURFACED_MISTAKES = 3

/**
 * Pure so the whole model is unit-testable on the JVM — the same split that
 * `SelfDiagnosticsRepository`/`evaluateDiagnostics` already uses. Given identical inputs
 * this always produces an identical state; nothing here reads a clock, a permission, or
 * a database.
 */
fun deriveNuaState(inputs: NuaStateInputs): NuaState {
    val pending = buildList {
        inputs.notificationsNeedingAttention.forEach { add(PendingTask(PendingKind.NOTIFICATION, it)) }
        inputs.documentsExpiringSoon.forEach { add(PendingTask(PendingKind.DOCUMENT_EXPIRY, it)) }
        inputs.visionMonitorsDue.forEach { add(PendingTask(PendingKind.VISION_RECHECK, it)) }
        inputs.recentGoalObservations.forEach { add(PendingTask(PendingKind.GOAL_OBSERVATION, it)) }
    }

    val workload = Workload(
        notificationsNeedingAttention = inputs.notificationsNeedingAttention.size,
        eventsRemainingToday = inputs.eventsRemainingToday,
        pendingItems = pending.size,
    )

    val availability = when (inputs.currentlyInEvent) {
        null -> UserAvailability.UNKNOWN
        true -> UserAvailability.IN_EVENT
        false -> UserAvailability.FREE
    }

    return NuaState(
        focus = focusFor(workload.total),
        confidence = inputs.trustScore,
        currentObjective = inputs.activeGoals.firstOrNull(),
        currentContext = describeContext(inputs, availability),
        workload = workload,
        recentMistakes = inputs.recentFailures.take(MAX_SURFACED_MISTAKES),
        pendingTasks = pending,
        availability = availability,
        isOffline = inputs.isOffline,
    )
}

private fun focusFor(total: Int): NuaFocus = when {
    total >= OVERLOADED_THRESHOLD -> NuaFocus.OVERLOADED
    total >= BUSY_THRESHOLD -> NuaFocus.BUSY
    total >= ATTENTIVE_THRESHOLD -> NuaFocus.ATTENTIVE
    else -> NuaFocus.CALM
}

/**
 * A plain sentence about right now. Deliberately built from facts NUA actually has —
 * it never guesses at where the user is or what they're doing.
 */
private fun describeContext(inputs: NuaStateInputs, availability: UserAvailability): String {
    if (inputs.isOffline) return "No connection — only on-device features are working."
    return when (availability) {
        UserAvailability.IN_EVENT -> "In a calendar event right now."
        UserAvailability.UNKNOWN -> when {
            inputs.notificationsNeedingAttention.isNotEmpty() ->
                "Calendar isn't readable, so NUA is going on notifications alone."
            else -> "Calendar isn't readable, so NUA can't tell how today is shaping up."
        }
        UserAvailability.FREE -> when {
            inputs.eventsRemainingToday == 0 -> "Nothing left on the calendar today."
            inputs.eventsRemainingToday == 1 -> "1 event still to come today."
            else -> "${inputs.eventsRemainingToday} events still to come today."
        }
    }
}
