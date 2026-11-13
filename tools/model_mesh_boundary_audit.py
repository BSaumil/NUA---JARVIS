#!/usr/bin/env python3
"""Sovereign Model Mesh privacy-boundary audit.

NUA's OpenJarvis benchmark work adds powerful local/cloud routing, but the privacy
advantage only exists while a few structural invariants remain true:

1. Autonomous background jobs that read broad personal context (Dreams, memory
   consolidation, goal review) are private/on-device only.
2. HIGH-sensitivity TaskContracts default to no cloud egress.
3. Daily Intelligence uses a richer local prompt and a minimized cloud prompt.
4. Trace-driven routing only reorders the policy-safe fallback order; it never learns
   over the raw set of available providers.

This audit is intentionally static and narrow. It does not try to prove all privacy
properties. It catches these reviewed invariants silently drifting in future changes.
It self-tests first so a detector that cannot find a known violation cannot report a
trustworthy zero.
"""
from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

PRIVATE_WORKERS = (
    "app/src/main/java/com/nua/assistant/goals/GoalReviewWorker.kt",
    "app/src/main/java/com/nua/assistant/memory/MemoryConsolidationWorker.kt",
    "app/src/main/java/com/nua/assistant/dreams/DreamSynthesisWorker.kt",
)

WORKER_REQUIRED = (
    "ModelMesh",
    "PrivacySensitivity.HIGH",
    "requiresOffline = true",
)
WORKER_FORBIDDEN = (
    "ClaudeApiClient",
)

CONTRACT_PATH = "app/src/main/java/com/nua/assistant/ai/mesh/TaskContract.kt"
CONTRACT_REQUIRED = (
    "val allowCloud: Boolean = privacySensitivity != PrivacySensitivity.HIGH",
)

BRIEFING_PATH = "app/src/main/java/com/nua/assistant/briefing/MorningBriefing.kt"
BRIEFING_REQUIRED = (
    "DataEgressGateway.filterFacts",
    "cloudUserPrompt = cloudPrompt",
)

MESH_PATH = "app/src/main/java/com/nua/assistant/ai/mesh/ModelMesh.kt"
MESH_REQUIRED = (
    "val baseOrder = fallbackOrder(contract, available)",
    "routingLearning.order(contract.task, baseOrder)",
)


def read(root: Path, rel: str) -> str | None:
    path = root / rel
    if not path.exists():
        return None
    return path.read_text(encoding="utf-8")


def audit(root: Path) -> list[str]:
    issues: list[str] = []

    for rel in PRIVATE_WORKERS:
        text = read(root, rel)
        if text is None:
            issues.append(f"{rel}: missing privacy-critical worker")
            continue
        for required in WORKER_REQUIRED:
            if required not in text:
                issues.append(f"{rel}: missing required invariant {required!r}")
        for forbidden in WORKER_FORBIDDEN:
            if forbidden in text:
                issues.append(f"{rel}: forbidden direct cloud dependency {forbidden!r}")

    contract = read(root, CONTRACT_PATH)
    if contract is None:
        issues.append(f"{CONTRACT_PATH}: missing")
    else:
        for required in CONTRACT_REQUIRED:
            if required not in contract:
                issues.append(f"{CONTRACT_PATH}: HIGH privacy no longer defaults cloud off")

    briefing = read(root, BRIEFING_PATH)
    if briefing is None:
        issues.append(f"{BRIEFING_PATH}: missing")
    else:
        for required in BRIEFING_REQUIRED:
            if required not in briefing:
                issues.append(f"{BRIEFING_PATH}: missing cloud-minimization invariant {required!r}")

    mesh = read(root, MESH_PATH)
    if mesh is None:
        issues.append(f"{MESH_PATH}: missing")
    else:
        for required in MESH_REQUIRED:
            if required not in mesh:
                issues.append(f"{MESH_PATH}: learned routing is no longer bounded by fallbackOrder")

    return issues


def selftest() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for rel in PRIVATE_WORKERS:
            path = root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            # Two workers are good; one deliberately violates both direct-cloud and
            # local-only requirements so the detector has to prove it sees them.
            if rel.endswith("DreamSynthesisWorker.kt"):
                path.write_text(
                    "class DreamSynthesisWorker { val client: ClaudeApiClient? = null }",
                    encoding="utf-8",
                )
            else:
                path.write_text(
                    "class Worker { val mesh: ModelMesh? = null; "
                    "val p = PrivacySensitivity.HIGH; "
                    "val contract = TaskContract(requiresOffline = true) }",
                    encoding="utf-8",
                )

        contract = root / CONTRACT_PATH
        contract.parent.mkdir(parents=True, exist_ok=True)
        contract.write_text(
            "val allowCloud: Boolean = privacySensitivity != PrivacySensitivity.HIGH",
            encoding="utf-8",
        )

        briefing = root / BRIEFING_PATH
        briefing.parent.mkdir(parents=True, exist_ok=True)
        briefing.write_text(
            "DataEgressGateway.filterFacts(); cloudUserPrompt = cloudPrompt",
            encoding="utf-8",
        )

        mesh = root / MESH_PATH
        mesh.parent.mkdir(parents=True, exist_ok=True)
        mesh.write_text(
            "val baseOrder = fallbackOrder(contract, available)\n"
            "routingLearning.order(contract.task, baseOrder)",
            encoding="utf-8",
        )

        issues = audit(root)
        caught_direct_cloud = any("ClaudeApiClient" in issue for issue in issues)
        caught_missing_offline = any(
            "DreamSynthesisWorker.kt" in issue and "requiresOffline" in issue
            for issue in issues
        )
        if caught_direct_cloud and caught_missing_offline:
            print(
                "SELFTEST PASS — detector catches direct-cloud and missing-local-only "
                "regressions in a privacy-critical worker",
            )
            return 0

        print("SELFTEST FAIL — known privacy regression was not detected")
        for issue in issues:
            print(" ", issue)
        return 1


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        sys.exit(selftest())
    if selftest():
        sys.exit(2)

    root = Path(".")
    issues = audit(root)
    print(f"\nMODEL MESH PRIVACY BOUNDARY VIOLATIONS: {len(issues)}")
    for issue in issues:
        print(f"  {issue}")
    print(f"\nroot={os.fspath(root)}  violations={len(issues)}")
    sys.exit(1 if issues else 0)
