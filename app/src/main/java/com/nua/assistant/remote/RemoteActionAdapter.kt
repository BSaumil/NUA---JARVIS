package com.nua.assistant.remote

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.uaf.ActionAdapter
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.security.egress.PrivacyCapsule
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

private const val MAX_REMOTE_RESULT_CHARS = 4_000

@Singleton
class McpActionAdapter @Inject constructor(
    private val endpoints: RemoteEndpointResolver,
    private val mcpTransport: McpTransport,
    private val a2aTransport: A2ATransport,
    private val json: Json,
) : ActionAdapter {
    override val type = ExecutionAdapterType.MCP

    override suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult {
        if (descriptor.action != NuaActionType.REMOTE_TOOL) return NuaRouteResult.FallThroughToChat
        val endpointId = parameters["endpointId"].orEmpty()
        val protocol = runCatching { RemoteProtocol.valueOf(parameters["protocol"].orEmpty().uppercase()) }.getOrNull()
            ?: return NuaRouteResult.ActionTaken("Remote protocol is invalid.", false)
        val tool = parameters["tool"].orEmpty()
        val argumentsJson = parameters["argumentsJson"].orEmpty()
        val idempotencyKey = parameters["idempotencyKey"].orEmpty()
        val capsule = parameters["privacyCapsuleJson"]?.let {
            runCatching { json.decodeFromString(PrivacyCapsule.serializer(), it) }.getOrNull()
        }
        if (endpointId.isBlank() || tool.isBlank() || idempotencyKey.isBlank()) {
            return NuaRouteResult.ActionTaken("Remote tool request is incomplete.", false)
        }
        val trust = endpoints.approved(endpointId)
        val validation = validateRemoteInvocation(trust, protocol, tool, argumentsJson, capsule)
        if (!validation.allowed || trust == null) {
            return NuaRouteResult.ActionTaken("Remote tool refused: ${validation.reason ?: "endpoint is not trusted"}.", false)
        }
        val request = RemoteInvocationRequest(
            UUID.randomUUID().toString(),
            endpointId,
            tool,
            argumentsJson,
            idempotencyKey,
        )
        val result = when (protocol) {
            RemoteProtocol.MCP -> mcpTransport.invoke(trust, request)
            RemoteProtocol.A2A -> a2aTransport.invoke(trust, request)
        }
        if (!result.succeeded) {
            return NuaRouteResult.ActionTaken("Remote tool failed (${result.errorCode ?: "UNKNOWN"}).", false)
        }
        val safeText = result.outputText.orEmpty().take(MAX_REMOTE_RESULT_CHARS)
        return NuaRouteResult.ActionTaken(
            if (safeText.isBlank()) "Remote tool completed through $endpointId."
            else "Remote tool completed. Untrusted remote result:\n$safeText",
            true,
        )
    }
}

@Singleton
class A2AActionAdapter @Inject constructor(
    private val delegate: McpActionAdapter,
) : ActionAdapter {
    override val type = ExecutionAdapterType.A2A

    override suspend fun execute(
        descriptor: CapabilityDescriptor,
        parameters: Map<String, String>,
        context: AdapterExecutionContext,
    ): NuaRouteResult = delegate.execute(
        descriptor,
        parameters + ("protocol" to RemoteProtocol.A2A.name),
        context,
    )
}
