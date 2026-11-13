package com.nua.assistant.evolution

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EvolutionModule {
    @Binds
    abstract fun bindEvolutionFailureSink(engine: EvolutionEngine): EvolutionFailureSink
}
