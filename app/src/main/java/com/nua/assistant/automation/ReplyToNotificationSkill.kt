package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyToNotificationSkill @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val target = intent.parameters["target"]
        val message = intent.parameters["message"]
        if (target.isNullOrBlank() || message.isNullOrBlank()) return NuaRouteResult.FallThroughToChat

        return when (val notification = notificationRepository.findByTarget(target)) {
            null -> NuaRouteResult.ActionTaken("I don't see a recent notification from \"$target\" to reply to.", succeeded = false)
            else -> if (notification.replyAction == null) {
                NuaRouteResult.ActionTaken(
                    "That notification from ${notification.title} doesn't have a quick-reply NUA can use.",
                    succeeded = false,
                )
            } else {
                NuaRouteResult.ReplyProposed(notification, message)
            }
        }
    }
}
