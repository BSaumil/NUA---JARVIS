package com.nua.assistant.ai.mesh

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelMeshModule {

    @Binds
    abstract fun bindModelAvailabilityDetector(impl: RealModelAvailabilityDetector): ModelAvailabilityDetector

    @Binds
    abstract fun bindCloudCompletionProvider(impl: ClaudeApiCloudCompletionProvider): CloudCompletionProvider
}
