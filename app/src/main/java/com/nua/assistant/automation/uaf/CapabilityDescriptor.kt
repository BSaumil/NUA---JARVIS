package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.autonomyTierFor

/**
 * Which execution mechanism actually carries out a capability. Every current NuaSkill runs
 * as an in-process Kotlin call, so today every descriptor's [ExecutionAdapterType] set is
 * `[LOCAL_NATIVE]` — the other members exist so a *future* capability can declare a
 * different mechanism without inventing a new registry shape, per the Universal Action
 * Fabric directive. Adding a member here does not grant it any authority on its own: every
 * adapter still executes behind the same [com.nua.assistant.automation.SkillSandbox]
 * boundary — see [ActionAdapter]'s own doc comment for why.
 */
enum class ExecutionAdapterType {
    LOCAL_NATIVE,
    ANDROID_INTENT,
    APP_FUNCTIONS,
    MCP,
    NOTIFICATION_REMOTE_INPUT,
    ACCESSIBILITY,
    EXTERNAL_API,
}

/** Whether a capability changes anything outside NUA's own database. */
enum class SideEffectClass { NONE, LOCAL_STATE_ONLY, EXTERNAL_WORLD }

/** Whether a real-world side effect, once it happens, can be undone by NUA itself. */
enum class Reversibility { REVERSIBLE, MANUALLY_REVERSIBLE, IRREVERSIBLE }

/** Whether repeating this capability's execution with the same inputs is safe. */
enum class IdempotencyPolicy { SAFE_TO_REPEAT, DEDUPE_REQUIRED }

/** Whether a capability may run without an explicit user confirmation in the moment. */
enum class ConfirmationPolicy { NONE_REQUIRED, CONFIRM_BEFORE_EXECUTE }

/**
 * One capability's machine-readable contract — the Universal Action Fabric directive's
 * descriptor model, generated *from* the real, closed [NuaActionType] registry
 * ([com.nua.assistant.automation.SkillCatalog] already does the display-facing half of
 * this; this is the execution-facing half). Every field here is derived from data that
 * already exists and is already enforced elsewhere — [riskTier] from
 * [autonomyTierFor], [permissions]/[parameters]/[timeoutMillis] from the skill's own
 * [SkillManifest] — nothing here is a second source of truth a future change could let
 * drift from what [com.nua.assistant.automation.SkillSandbox] actually enforces.
 */
data class CapabilityDescriptor(
    val action: NuaActionType,
    val version: Int = 1,
    val purpose: String,
    val parameters: List<String>,
    val riskTier: AutonomyTier,
    val permissions: List<String>,
    val sideEffect: SideEffectClass,
    val reversibility: Reversibility,
    val idempotency: IdempotencyPolicy,
    val confirmation: ConfirmationPolicy,
    val timeoutMillis: Long,
    val preferredAdapter: ExecutionAdapterType,
    val fallbackAdapters: List<ExecutionAdapterType> = emptyList(),
)

/**
 * Pure: builds a [CapabilityDescriptor] for [action] from its own [manifest] and the same
 * risk-tier mapping the sandbox already enforces. [sideEffect]/[reversibility]/
 * [idempotency]/[confirmation] are a per-action classification — not derivable from
 * existing fields — reviewed by hand against each action's real behavior, the same way
 * [autonomyTierFor]'s own mapping was hand-reviewed when it was written. Exhaustive `when`,
 * no `else`: a new [NuaActionType] must be classified here explicitly, the same invariant
 * [autonomyTierFor] already holds.
 */
fun capabilityDescriptorFor(action: NuaActionType, manifest: SkillManifest): CapabilityDescriptor {
    val (sideEffect, reversibility, idempotency, confirmation) = classificationFor(action)
    return CapabilityDescriptor(
        action = action,
        purpose = purposeFor(action),
        parameters = manifest.parameters.map { it.name },
        riskTier = autonomyTierFor(action),
        permissions = manifest.requiredPermissions,
        sideEffect = sideEffect,
        reversibility = reversibility,
        idempotency = idempotency,
        confirmation = confirmation,
        timeoutMillis = manifest.timeoutMillis,
        preferredAdapter = ExecutionAdapterType.LOCAL_NATIVE,
        // REPLY_TO_NOTIFICATION's LOCAL_NATIVE path (ReplyToNotificationSkill via
        // SkillSandbox) only ever *proposes* a reply -- confirming and actually sending it
        // is a distinct official mechanism (NotificationRemoteInputAdapter). Named here so
        // WorkflowExecutor can route to it once a step's authorization is already proven,
        // instead of re-proposing something already confirmed.
        fallbackAdapters = if (action == NuaActionType.REPLY_TO_NOTIFICATION) {
            listOf(ExecutionAdapterType.NOTIFICATION_REMOTE_INPUT)
        } else {
            emptyList()
        },
    )
}

private data class Classification(
    val sideEffect: SideEffectClass,
    val reversibility: Reversibility,
    val idempotency: IdempotencyPolicy,
    val confirmation: ConfirmationPolicy,
)

private fun classificationFor(action: NuaActionType): Classification = when (action) {
    NuaActionType.READ_NOTIFICATIONS,
    NuaActionType.GET_WEATHER,
    NuaActionType.MORNING_BRIEFING,
    NuaActionType.EMAIL,
    NuaActionType.CHAT,
    ->
        Classification(SideEffectClass.NONE, Reversibility.REVERSIBLE, IdempotencyPolicy.SAFE_TO_REPEAT, ConfirmationPolicy.NONE_REQUIRED)

    NuaActionType.OPEN_APP,
    NuaActionType.PLAY_MEDIA,
    NuaActionType.MEDIA_CONTROL,
    ->
        Classification(SideEffectClass.LOCAL_STATE_ONLY, Reversibility.REVERSIBLE, IdempotencyPolicy.SAFE_TO_REPEAT, ConfirmationPolicy.NONE_REQUIRED)

    NuaActionType.SMART_HOME ->
        Classification(SideEffectClass.EXTERNAL_WORLD, Reversibility.MANUALLY_REVERSIBLE, IdempotencyPolicy.SAFE_TO_REPEAT, ConfirmationPolicy.NONE_REQUIRED)

    NuaActionType.CALENDAR_INVITE ->
        Classification(SideEffectClass.EXTERNAL_WORLD, Reversibility.MANUALLY_REVERSIBLE, IdempotencyPolicy.DEDUPE_REQUIRED, ConfirmationPolicy.NONE_REQUIRED)

    NuaActionType.REPLY_TO_NOTIFICATION,
    NuaActionType.SMS_SEND,
    ->
        Classification(SideEffectClass.EXTERNAL_WORLD, Reversibility.IRREVERSIBLE, IdempotencyPolicy.DEDUPE_REQUIRED, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE)

    NuaActionType.PLAN_TASK ->
        Classification(SideEffectClass.LOCAL_STATE_ONLY, Reversibility.MANUALLY_REVERSIBLE, IdempotencyPolicy.SAFE_TO_REPEAT, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE)
}

/** Pure: a short machine-readable purpose string, distinct from SkillCatalog's user-facing [com.nua.assistant.automation.displayNameFor]. */
private fun purposeFor(action: NuaActionType): String = when (action) {
    NuaActionType.OPEN_APP -> "Launch an installed app by name."
    NuaActionType.PLAY_MEDIA -> "Start media playback matching a query."
    NuaActionType.MEDIA_CONTROL -> "Control the active media session (pause/skip/etc)."
    NuaActionType.READ_NOTIFICATIONS -> "Summarize current notifications."
    NuaActionType.REPLY_TO_NOTIFICATION -> "Send a reply through a notification's own quick-reply action."
    NuaActionType.GET_WEATHER -> "Report current weather conditions."
    NuaActionType.MORNING_BRIEFING -> "Generate the structured daily briefing."
    NuaActionType.PLAN_TASK -> "Propose a multi-step plan for user confirmation."
    NuaActionType.SMART_HOME -> "Control a configured smart-home device."
    NuaActionType.EMAIL -> "Check inbox status."
    NuaActionType.SMS_SEND -> "Send a text message to a resolved contact."
    NuaActionType.CALENDAR_INVITE -> "Create a calendar event, optionally inviting an attendee."
    NuaActionType.CHAT -> "Fall through to a normal conversational reply."
}
