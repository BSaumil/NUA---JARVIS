package com.nua.assistant.automation.uaf

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/** Dagger multibinding key — mirrors ActionTypeKey in SkillModule.kt, one map per key type. */
@MapKey
annotation class AdapterTypeKey(val value: ExecutionAdapterType)

/**
 * Registers every [ActionAdapter] implementation into the
 * `Map<ExecutionAdapterType, ActionAdapter>` [WorkflowExecutor] injects. Only the adapter
 * types with a real implementation are bound here — an [ExecutionAdapterType] with no
 * binding simply has no entry in the map, and [WorkflowExecutor.runOneStep] already treats
 * a missing adapter as a normal [StepOutcomeState.FAILED] rather than a crash.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdapterModule {

    @Binds
    @IntoMap
    @AdapterTypeKey(ExecutionAdapterType.LOCAL_NATIVE)
    abstract fun bindLocalNativeAdapter(impl: LocalNativeAdapter): ActionAdapter

    @Binds
    @IntoMap
    @AdapterTypeKey(ExecutionAdapterType.NOTIFICATION_REMOTE_INPUT)
    abstract fun bindNotificationRemoteInputAdapter(impl: NotificationRemoteInputAdapter): ActionAdapter

    @Binds
    @IntoMap
    @AdapterTypeKey(ExecutionAdapterType.SMS_MANAGER)
    abstract fun bindSmsManagerAdapter(impl: SmsManagerAdapter): ActionAdapter

    @Binds
    @IntoMap
    @AdapterTypeKey(ExecutionAdapterType.PLAN_CONFIRMATION)
    abstract fun bindPlanConfirmationAdapter(impl: PlanConfirmationAdapter): ActionAdapter
}
