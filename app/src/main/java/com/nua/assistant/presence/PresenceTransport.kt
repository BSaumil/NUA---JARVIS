package com.nua.assistant.presence

/**
 * The real-vs-fake inversion seam for Presence Mesh's transport — same pattern
 * `ai/mesh/ModelMesh.kt`'s `CloudCompletionProvider` and `automation/uaf/ActionAdapter.kt`
 * already use for an external system a pure-JVM test can't reach: a real implementation
 * for production, a fake for unit tests. See `docs/PRESENCE_MESH_RFC.md`.
 */
interface PresenceTransport {
    suspend fun publish(snapshot: PresenceSnapshot)
}
