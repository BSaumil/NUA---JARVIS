package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.SuggestedReminder
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.NuaSkill
import com.nua.assistant.automation.SkillSandbox
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.notifications.NotificationReplySender
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.sms.SmsSender
import com.nua.assistant.trust.ActionOutcomeState
import com.nua.assistant.ui.planConfirmationMessage
import com.nua.assistant.ui.planConfirmationOutcome
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Carries what every [ActionAdapter] needs but a [CapabilityDescriptor]/param map doesn't. */
data class AdapterExecutionContext(
    val originalUtterance: String,
    val pinnedLanguage: NuaLanguage?,
)

/**
 * Executes one capability by one concrete mechanism. Implementations must never perform
 * their own authorization check — [WorkflowExecutor] is the single place
 * [isAuthorizationSufficient] is consulted, exactly as [SkillSandbox] is the single place
 * a direct dispatch is authorized. An adapter that checked authorization itself would be a
 * second, divergeable copy of that rule; an adapter that skipped it would be a bypass. Both
 * are exactly what the Universal Action Fabric directive's "no adapter may become a second
 * authorization path" rule forbids.
 */
interface ActionAdapter {
    val type: ExecutionAdapterType

    suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult
}

/**
 * The adapter for every capability that's a registered [NuaSkill] — which today is all of
 * them. Delegates to [SkillSandbox.execute] verbatim: this is not a reimplementation of the
 * sandbox's manifest/permission/timeout enforcement, it's the same call
 * [com.nua.assistant.automation.NuaIntentRouter.dispatch] already makes, reached through a
 * second entry point rather than a second policy.
 */
@Singleton
class LocalNativeAdapter @Inject constructor(
    private val skillSandbox: SkillSandbox,
    private val skills: Map<NuaActionType, @JvmSuppressWildcards NuaSkill>,
) : ActionAdapter {
    override val type = ExecutionAdapterType.LOCAL_NATIVE

    override suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult {
        val skill = skills[descriptor.action] ?: return NuaRouteResult.FallThroughToChat
        val intent = ClassifiedIntent(action = descriptor.action, confidence = 1.0, parameters = parameters)
        return skillSandbox.execute(skill, intent, context.originalUtterance, context.pinnedLanguage)
    }
}

/**
 * A second, genuinely distinct execution mechanism: sends a reply through a notification's
 * own official RemoteInput quick-reply action directly, the same official API
 * [com.nua.assistant.automation.ReplyToNotificationSkill] resolves a target through, but
 * without routing back through [SkillSandbox] a second time — this adapter *is* the
 * mechanism, the way [LocalNativeAdapter] wraps one. Only handles
 * [NuaActionType.REPLY_TO_NOTIFICATION]; every other action falls through untouched, since
 * this adapter has no other mechanism to offer.
 */
@Singleton
class NotificationRemoteInputAdapter @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val notificationReplySender: NotificationReplySender,
) : ActionAdapter {
    override val type = ExecutionAdapterType.NOTIFICATION_REMOTE_INPUT

    override suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult {
        if (descriptor.action != NuaActionType.REPLY_TO_NOTIFICATION) return NuaRouteResult.FallThroughToChat
        val target = parameters["target"]
        val message = parameters["message"]
        if (target.isNullOrBlank() || message.isNullOrBlank()) return NuaRouteResult.FallThroughToChat

        val notification = notificationRepository.findByTarget(target)
            ?: return NuaRouteResult.ActionTaken("I don't see a recent notification from \"$target\" to reply to.", succeeded = false)
        val replyAction = notification.replyAction
            ?: return NuaRouteResult.ActionTaken(
                "That notification from ${notification.title} doesn't have a quick-reply NUA can use.",
                succeeded = false,
            )

        val outcome = notificationReplySender.sendReply(replyAction, message)
        return NuaRouteResult.ActionTaken(
            message = "Sent — replied to ${notification.title}. (NUA can only confirm it was handed off, not that it was delivered.)",
            succeeded = outcome == ActionOutcomeState.ACCEPTED,
        )
    }
}

/**
 * [NuaActionType.SMS_SEND]'s real send mechanism (Android's [SmsSender]), reached only
 * once a step's authorization is already proven -- [LocalNativeAdapter]'s SmsSendSkill
 * path only ever *resolves a contact and proposes* a message, the same
 * propose/confirm split [NotificationRemoteInputAdapter] documents for replies. Expects
 * "phoneNumber" and "message" parameters -- already-resolved values from the proposal,
 * not a contact name to re-resolve, so confirming can never silently text a different
 * number than the one actually shown to the user.
 */
@Singleton
class SmsManagerAdapter @Inject constructor(
    private val smsSender: SmsSender,
) : ActionAdapter {
    override val type = ExecutionAdapterType.SMS_MANAGER

    override suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult {
        if (descriptor.action != NuaActionType.SMS_SEND) return NuaRouteResult.FallThroughToChat
        val phoneNumber = parameters["phoneNumber"]
        val message = parameters["message"]
        if (phoneNumber.isNullOrBlank() || message.isNullOrBlank()) return NuaRouteResult.FallThroughToChat

        val outcome = smsSender.send(phoneNumber, message)
        return NuaRouteResult.ActionTaken(
            message = "Sent — texted $phoneNumber. (NUA can only confirm it was handed off, not that it was delivered.)",
            succeeded = outcome == ActionOutcomeState.ACCEPTED,
        )
    }
}

/**
 * [NuaActionType.PLAN_TASK]'s real confirmation mechanism: creates the plan's actual
 * calendar reminders via [CalendarReader] -- [LocalNativeAdapter]'s PlanTaskSkill path
 * only ever *proposes* a plan (calls [com.nua.assistant.ai.TaskPlanner.propose]).
 * Deliberately does not re-call [com.nua.assistant.ai.TaskPlanner.propose] or re-ask
 * Claude for a plan: that could produce a *different* plan than the one actually shown
 * to and confirmed by the user. Instead takes exactly the reminders the proposal already
 * decided on, carried through the plan step's own "reminders" parameter as a JSON-encoded
 * [SuggestedReminder] list (the same shape [com.nua.assistant.ai.TaskPlanner] already
 * uses) -- a flat string because [PlanStep.parameters] is `Map<String, String>`, not
 * because this data is naturally string-shaped.
 */
@Singleton
class PlanConfirmationAdapter @Inject constructor(
    private val calendarReader: CalendarReader,
    private val json: Json,
) : ActionAdapter {
    override val type = ExecutionAdapterType.PLAN_CONFIRMATION

    override suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult {
        if (descriptor.action != NuaActionType.PLAN_TASK) return NuaRouteResult.FallThroughToChat
        val remindersJson = parameters["reminders"] ?: return NuaRouteResult.FallThroughToChat
        val reminders = runCatching {
            json.decodeFromString(ListSerializer(SuggestedReminder.serializer()), remindersJson)
        }.getOrNull() ?: return NuaRouteResult.FallThroughToChat

        val results = reminders.map { calendarReader.createReminder(title = it.title, whenMillis = it.whenMillis) }
        return NuaRouteResult.ActionTaken(
            message = planConfirmationMessage(results),
            succeeded = planConfirmationOutcome(results) == ActionOutcomeState.COMPLETED,
        )
    }
}

/** Encodes a plan's reminders for [PlanConfirmationAdapter]'s "reminders" parameter -- see its doc comment for why this can't just carry the [com.nua.assistant.ai.TaskPlan] object directly. */
fun encodePlanReminders(json: Json, reminders: List<SuggestedReminder>): String =
    json.encodeToString(ListSerializer(SuggestedReminder.serializer()), reminders)
