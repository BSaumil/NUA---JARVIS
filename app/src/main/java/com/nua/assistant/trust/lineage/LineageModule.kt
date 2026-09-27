package com.nua.assistant.trust.lineage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LineageModule {

    @Binds
    abstract fun bindLineageRecorder(impl: RoomLineageRecorder): LineageRecorder
}
