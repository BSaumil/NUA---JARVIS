package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadNotificationsSkill @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : NuaSkill {

    override val manifest = SkillManifest()

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult =
        NuaRouteResult.ActionTaken(notificationRepository.summary().spokenSummary)
}
