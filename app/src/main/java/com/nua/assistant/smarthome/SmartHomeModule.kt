package com.nua.assistant.smarthome

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SmartHomeModule {

    @Binds
    abstract fun bindSmartHomeRepository(impl: UnconfiguredSmartHomeRepository): SmartHomeRepository
}
