#!/usr/bin/env python3
"""NUA remote MCP/A2A execution-boundary audit.

Pins three invariants:
1. transport.invoke(...) calls live only in RemoteActionAdapter.kt's reviewed executor;
2. REMOTE_TOOL remains T3 / confirmation-required and descriptor-routed through MCP/A2A;
3. every remote invocation requires validateRemoteInvocation(...) + PrivacyCapsule.

Self-test injects an unauthorized mcpTransport.invoke(...) call outside the reviewed file.
"""
from __future__ import annotations
import glob
import os
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path("app/src/main/java/com/nua/assistant")
REMOTE_ADAPTER = ROOT / "remote/RemoteActionAdapter.kt"
REMOTE_MODELS = ROOT / "remote/RemoteGatewayModels.kt"
AUTONOMY = ROOT / "trust/AutonomyTier.kt"
CAPABILITY = ROOT / "automation/uaf/CapabilityDescriptor.kt"

TRANSPORT_CALL = re.compile(r"\b(?:mcpTransport|a2aTransport)\.invoke\(")

REQUIRED = {
    REMOTE_ADAPTER: (
        "validateRemoteInvocation(",
        "PrivacyCapsule.serializer()",
        "RemoteProtocol.MCP -> mcpTransport.invoke",
        "RemoteProtocol.A2A -> a2aTransport.invoke",
    ),
    REMOTE_MODELS: (
        'if (capsule == null)',
        "capsule.networkEgressPermitted",
        "remoteEndpointFingerprint(manifest)",
        "capability !in manifest.allowedCapabilities",
    ),
    AUTONOMY: (
        "NuaActionType.REMOTE_TOOL",
        "-> AutonomyTier.T3",
    ),
    CAPABILITY: (
        "NuaActionType.REMOTE_TOOL",
        "ExecutionAdapterType.MCP",
        "ExecutionAdapterType.A2A",
        "ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE",
    ),
}

def audit(root: Path) -> list[str]:
    issues: list[str] = []
    app_root = root / "app/src/main"
    for path_str in glob.glob(str(app_root / "**/*.kt"), recursive=True):
        path = Path(path_str)
        text = path.read_text(encoding="utf-8")
        count = len(TRANSPORT_CALL.findall(text))
        if count and not path.as_posix().endswith(REMOTE_ADAPTER.as_posix()):
            issues.append(f"{path.relative_to(root)}: unauthorized remote transport invocation ({count})")

    for rel, tokens in REQUIRED.items():
        path = root / rel
        if not path.exists():
            issues.append(f"{rel}: missing")
            continue
        text = path.read_text(encoding="utf-8")
        for token in tokens:
            if token not in text:
                issues.append(f"{rel}: missing invariant {token!r}")
    return issues

def selftest() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for rel, tokens in REQUIRED.items():
            path = root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join(tokens), encoding="utf-8")
        bad = root / "app/src/main/java/com/nua/assistant/ui/RemoteShortcut.kt"
        bad.parent.mkdir(parents=True, exist_ok=True)
        bad.write_text("fun bypass() { mcpTransport.invoke(trust, request) }", encoding="utf-8")
        issues = audit(root)
        if any("RemoteShortcut.kt" in issue for issue in issues):
            print("SELFTEST PASS — remote gateway audit catches transport bypass")
            return 0
        print("SELFTEST FAIL — unauthorized remote transport call was not detected")
        print("\n".join(issues))
        return 1

if __name__ == "__main__":
    if selftest():
        sys.exit(2)
    issues = audit(Path("."))
    print(f"\nREMOTE GATEWAY BOUNDARY VIOLATIONS: {len(issues)}")
    for issue in issues:
        print(" ", issue)
    sys.exit(1 if issues else 0)
