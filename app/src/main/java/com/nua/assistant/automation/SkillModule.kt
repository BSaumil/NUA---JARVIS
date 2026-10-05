package com.nua.assistant.automation

import com.nua.assistant.ai.NuaActionType
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * Registers every NuaSkill implementation into a Map<NuaActionType, NuaSkill> that
 * NuaIntentRouter injects and dispatches through — see NuaSkill.kt's doc comment.
 * NuaActionType.CHAT deliberately has no binding: the router falls through to chat
 * for it without ever consulting this map.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SkillModule {

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.OPEN_APP)
    abstract fun bindOpenAppSkill(impl: OpenAppSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.PLAY_MEDIA)
    abstract fun bindPlayMediaSkill(impl: PlayMediaSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.MEDIA_CONTROL)
    abstract fun bindMediaControlSkill(impl: MediaControlSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.READ_NOTIFICATIONS)
    abstract fun bindReadNotificationsSkill(impl: ReadNotificationsSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.REPLY_TO_NOTIFICATION)
    abstract fun bindReplyToNotificationSkill(impl: ReplyToNotificationSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.GET_WEATHER)
    abstract fun bindGetWeatherSkill(impl: GetWeatherSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.MORNING_BRIEFING)
    abstract fun bindMorningBriefingSkill(impl: MorningBriefingSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.PLAN_TASK)
    abstract fun bindPlanTaskSkill(impl: PlanTaskSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.SMART_HOME)
    abstract fun bindSmartHomeSkill(impl: SmartHomeSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.EMAIL)
    abstract fun bindEmailSkill(impl: EmailSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.SMS_SEND)
    abstract fun bindSmsSendSkill(impl: SmsSendSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.CALENDAR_INVITE)
    abstract fun bindCalendarInviteSkill(impl: CalendarInviteSkill): NuaSkill

    @Binds
    @IntoMap
    @ActionTypeKey(NuaActionType.SYSTEM_NAVIGATION)
    abstract fun bindSystemNavigationSkill(impl: SystemNavigationSkill): NuaSkill
}
