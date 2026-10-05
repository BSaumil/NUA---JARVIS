package com.nua.assistant.trust

import com.nua.assistant.ai.NuaActionType

/**
 * How much authority NUA has for a given action type, independent of confidence in any
 * specific request. This is the ceiling every NuaSkill dispatch is tagged with for the
 * audit trail (see ActionOutcomeEntity) — it documents in code what the README's
 * Tier 1/2/3 framework previously only described in prose.
 */
enum class AutonomyTier(val label: String, val description: String) {
    T0(
        "Read-only",
        "Looks something up or reads something aloud — nothing in the world changes.",
    ),
    T1(
        "Automatic",
        "Executes immediately — low-risk, easily repeated or undone (open an app, control media).",
    ),
    T2(
        "Notify + execute",
        "Executes immediately, then reports what happened — used where the action has a real-world effect (smart home).",
    ),
    T3(
        "Ask first",
        "Proposes the exact action and waits for a yes before doing it (replying to a notification).",
    ),
    T4(
        "Multi-step approval",
        "Proposes a whole plan and waits for confirmation before scheduling any of it.",
    ),
    T5(
        "Never autonomous",
        "Reserved for actions NUA should never take without a human doing it directly — nothing is wired to this tier yet.",
    ),
}

/** The autonomy ceiling for a given classified action — see AutonomyTier for what each level means. */
fun autonomyTierFor(action: NuaActionType): AutonomyTier = when (action) {
    NuaActionType.READ_NOTIFICATIONS,
    NuaActionType.GET_WEATHER,
    NuaActionType.MORNING_BRIEFING,
    NuaActionType.EMAIL,
    NuaActionType.CHAT,
    -> AutonomyTier.T0

    NuaActionType.OPEN_APP,
    NuaActionType.PLAY_MEDIA,
    NuaActionType.MEDIA_CONTROL,
    NuaActionType.SYSTEM_NAVIGATION,
    -> AutonomyTier.T1

    NuaActionType.SMART_HOME,
    NuaActionType.CALENDAR_INVITE,
    -> AutonomyTier.T2

    NuaActionType.REPLY_TO_NOTIFICATION,
    NuaActionType.SMS_SEND,
    -> AutonomyTier.T3

    NuaActionType.PLAN_TASK -> AutonomyTier.T4
}
