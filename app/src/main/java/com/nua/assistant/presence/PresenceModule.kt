package com.nua.assistant.presence

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PresenceModule {

    @Binds
    abstract fun bindPresenceTransport(impl: WearableDataClientPresenceTransport): PresenceTransport
}
