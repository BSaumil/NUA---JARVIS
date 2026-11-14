# ChatGPT protocol handoff — OpenJarvis Final 10

**Date:** 2026-11-14  
**ChatGPT branch:** `chatgpt/openjarvis-final-10-20261114`  
**Protocol/API head to consume:** `f576004f7fd2b0f10b8a0a2721a8f1c6e83698e7`

Claude Code: continue your owned workstreams 2, 4, 5, 6, 7 and platform implementation of 10.

The canonical shared contracts now exist on the ChatGPT branch:

- `app/src/main/java/com/nua/assistant/presence/protocol/PresenceProtocol.kt`
- `app/src/main/java/com/nua/assistant/localapi/LocalApiContract.kt`

The governed remote execution layer also exists and MUST remain the only MCP/A2A action path:

- `app/src/main/java/com/nua/assistant/remote/**`
- UAF action: `NuaActionType.REMOTE_TOOL`
- adapters: `ExecutionAdapterType.MCP` and `ExecutionAdapterType.A2A`

Important integration rules:

1. Do not redefine DeviceIdentity, PresenceState, ComputeJobRequest/Result, PairingChallenge/Approval, ResearchJob, SandboxJob or Local API request metadata.
2. Implement Windows/iOS/macOS/Linux consumers/servers against these semantics.
3. Connector writes and companion side effects must come back through UAF.
4. Pairing/revocation must map to PresenceTrustState and the shared identity model.
5. Local API privileged operations must require scopes, nonces/replay protection and paired identity.
6. If a platform needs additional schema fields, put them in your handoff as requested additions rather than silently forking the wire model.
7. Continue without waiting for routine approval. Do not merge to Main.

ChatGPT is continuing workstreams 1, 3, 8, 9 and the protocol/security portion of 10 in parallel.
