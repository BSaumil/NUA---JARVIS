package com.nua.assistant.email

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EmailModule {

    @Binds
    abstract fun bindEmailRepository(impl: UnconfiguredEmailRepository): EmailRepository
}
