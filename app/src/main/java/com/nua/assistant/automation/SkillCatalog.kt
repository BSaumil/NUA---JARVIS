package com.nua.assistant.automation

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.autonomyTierFor
import javax.inject.Inject
import javax.inject.Singleton

/** One capability, described for the user rather than for the dispatcher. */
data class SkillDescriptor(
    val action: NuaActionType,
    val displayName: String,
    val tier: AutonomyTier,
    val manifest: SkillManifest,
) {
    /** e.g. "needs contact, message" — what NUA has to know before it can do this. */
    val requiredInputs: List<String> get() = manifest.parameters.filter { it.required }.map { it.name }
}

/**
 * Reads the same closed Hilt multibinding the router dispatches through, so the "what NUA
 * can do" screen is generated from the actual registered skills rather than a hand-kept
 * list that drifts. Adding a skill makes it appear here automatically; there's no second
 * place to remember to update.
 *
 * This is the Agent Sandbox's manifests turned outward: the declared inputs, permissions,
 * and autonomy tier that constrain dispatch are exactly what a user needs to see to
 * understand what NUA is allowed to do on their behalf.
 */
@Singleton
class SkillCatalog @Inject constructor(
    private val skills: Map<NuaActionType, @JvmSuppressWildcards NuaSkill>,
) {
    fun all(): List<SkillDescriptor> = skills.entries
        .map { (action, skill) ->
            SkillDescriptor(
                action = action,
                displayName = displayNameFor(action),
                tier = autonomyTierFor(action),
                manifest = skill.manifest,
            )
        }
        // Grouped by how much authority NUA has, so the read-only capabilities read first
        // and the ones that act on the world are visibly separate.
        .sortedWith(compareBy({ it.tier.ordinal }, { it.displayName }))
}

/** Pure: a human name for an action type. Unit-tested so a new action can't ship as `SMS_SEND`. */
fun displayNameFor(action: NuaActionType): String = when (action) {
    NuaActionType.OPEN_APP -> "Open an app"
    NuaActionType.PLAY_MEDIA -> "Play music"
    NuaActionType.MEDIA_CONTROL -> "Control playback"
    NuaActionType.READ_NOTIFICATIONS -> "Read your notifications"
    NuaActionType.REPLY_TO_NOTIFICATION -> "Reply to a message"
    NuaActionType.GET_WEATHER -> "Check the weather"
    NuaActionType.MORNING_BRIEFING -> "Give your morning briefing"
    NuaActionType.PLAN_TASK -> "Plan something out"
    NuaActionType.SMART_HOME -> "Control smart-home devices"
    NuaActionType.EMAIL -> "Check your email"
    NuaActionType.SMS_SEND -> "Send a text"
    NuaActionType.CALENDAR_INVITE -> "Create a calendar event"
    NuaActionType.CHAT -> "Talk with you"
}
