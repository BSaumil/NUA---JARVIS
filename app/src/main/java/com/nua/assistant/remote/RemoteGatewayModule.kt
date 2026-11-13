package com.nua.assistant.remote

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteGatewayModule {
    @Binds abstract fun bindEndpointResolver(impl: RemoteEndpointRegistry): RemoteEndpointResolver
    @Binds abstract fun bindMcpTransport(impl: SovereignMcpClient): McpTransport
    @Binds abstract fun bindA2ATransport(impl: SovereignA2AClient): A2ATransport
}
