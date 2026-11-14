package com.nua.assistant.sandbox.wasm

import com.nua.assistant.presence.protocol.ComputeJobRequest
import com.nua.assistant.presence.protocol.ComputeJobResult

/**
 * Real-vs-fake inversion for the one call this package can't make itself: dispatching a
 * [ComputeJobRequest] to a paired companion node and waiting for its
 * [ComputeJobResult] — same pattern `ai/mesh/ModelMesh.kt`'s `CloudCompletionProvider`
 * and `automation/uaf/ActionAdapter.kt` already use. The real implementation is the
 * companion Local API client (workstream 2); this interface lets
 * [SandboxJobRepository]'s own routing/authorization logic be fully unit-tested against
 * a fake without a paired device.
 */
interface SandboxTransport {
    suspend fun submit(request: ComputeJobRequest): ComputeJobResult
}
