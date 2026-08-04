package com.nua.assistant.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationBindingsModule {

    @Binds
    abstract fun bindNotificationStatsProvider(impl: NotificationStatsStore): NotificationStatsProvider
}
