#!/usr/bin/env python3
"""NUA Sovereign Evolution Engine boundary audit.

The Evolution Engine may learn, test and tune itself, but it must not become a second
authority or execution path. This static gate pins the reviewed architecture:
- no OS process/shell execution in the Android Evolution package;
- no dynamic class loading/reflection escape;
- no direct networking/filesystem API in the runner/evolution package;
- no direct UAF adapter/executor access from self-repair code;
- Android runner enables only the bounded NUA_TRANSFORM_V1 runtime;
- source/authority-expanding repairs cannot silently auto-promote;
- runtime auto-promotion remains limited to explicit routing/prompt/research whitelists;
- self-improvement code cannot acquire direct privacy, permission, biometric, Guardian
  or UAF-authorization control.

The detector self-tests against a known-bad fixture before auditing the repository.
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

EVOLUTION_DIR = Path("app/src/main/java/com/nua/assistant/evolution")
RUNNER = EVOLUTION_DIR / "SovereignCodeRunner.kt"
POLICY = EVOLUTION_DIR / "EvolutionPolicy.kt"
TUNING = EVOLUTION_DIR / "EvolutionRuntimeTuningApplier.kt"
SPEC = EVOLUTION_DIR / "SafeSpecPolicy.kt"
SYNTH = EVOLUTION_DIR / "TransformRepairSynthesizer.kt"

FORBIDDEN_TOKENS = (
    "Runtime.getRuntime().exec",
    "ProcessBuilder(",
    "DexClassLoader",
    "PathClassLoader",
    "Class.forName",
    "setAccessible(",
    "java.net.",
    "okhttp",
    "java.io.File",
    "Socket(",
    "ActionAdapter",
    "CapabilityRegistry",
    "WorkflowExecutor",
    "System.loadLibrary",
    "System.load(",
    "android.Manifest",
    "DataEgressGateway",
    "ConfirmationPolicy",
    "isAuthorizationSufficient",
    "BiometricGate",
    "RuntimeSafetySentinel",
)

RUNNER_REQUIRED = (
    "request.language != SandboxLanguage.NUA_TRANSFORM_V1",
    "CodeExecutionStatus.UNSUPPORTED_RUNTIME",
    "maxOperations",
    "maxInputBytes",
    "maxOutputBytes",
    "maxWallTimeMillis",
)

POLICY_REQUIRED = (
    "candidate.impact.isForbidden()",
    "PromotionDecision.REQUIRES_OWNER_APPROVAL",
    "RepairKind.SOURCE_PATCH",
    "PromotionDecision.AUTO_PROMOTE_RUNTIME",
    "RepairKind.SPEC_TUNING",
)

TUNING_REQUIRED = (
    "RuntimeTuningKey.MODEL_ROUTING_MIN_SAMPLES",
    "RuntimeTuningKey.MODEL_ROUTING_MIN_SUCCESS_RATE_BPS",
    "RuntimeTuningKey.MODEL_ROUTING_MEMORY_DAYS",
    "patch.intValue !in 3..20",
    "patch.intValue !in 3_000..8_000",
    "patch.intValue !in 1..30",
    "RepairKind.SPEC_TUNING",
    "saveSpecRollback",
)

SYNTH_REQUIRED = (
    "PrivacySensitivity.HIGH",
    "requiresOffline = true",
    "InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM",
)

SPEC_REQUIRED = (
    "SpecTuningKey.PROMPT_SCHEMA_REMINDERS",
    "SpecTuningKey.PROMPT_REPAIR_ATTEMPTS",
    "SpecTuningKey.RESEARCH_MAX_SUBQUESTIONS",
    "SpecTuningKey.RESEARCH_CROSSCHECK_DEPTH",
    "patch.intValue !in 0..2",
    "patch.intValue !in 3..12",
    "patch.intValue !in 1..3",
)


def audit_root(root: Path) -> list[str]:
    issues: list[str] = []
    evolution = root / EVOLUTION_DIR
    if not evolution.exists():
        return [f"{EVOLUTION_DIR}: missing"]

    for path in evolution.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(root)
        for token in FORBIDDEN_TOKENS:
            if token in text:
                issues.append(f"{rel}: forbidden Evolution capability token {token!r}")

    for rel, required in (
        (RUNNER, RUNNER_REQUIRED),
        (POLICY, POLICY_REQUIRED),
        (TUNING, TUNING_REQUIRED),
        (SPEC, SPEC_REQUIRED),
        (SYNTH, SYNTH_REQUIRED),
    ):
        path = root / rel
        if not path.exists():
            issues.append(f"{rel}: missing")
            continue
        text = path.read_text(encoding="utf-8")
        for token in required:
            if token not in text:
                issues.append(f"{rel}: missing invariant {token!r}")

    return issues


def selftest() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        evolution = root / EVOLUTION_DIR
        evolution.mkdir(parents=True)

        (root / RUNNER).write_text(
            "request.language != SandboxLanguage.NUA_TRANSFORM_V1\n"
            "CodeExecutionStatus.UNSUPPORTED_RUNTIME\n"
            "maxOperations maxInputBytes maxOutputBytes maxWallTimeMillis\n"
            "ProcessBuilder(\"sh\")",
            encoding="utf-8",
        )
        (root / POLICY).write_text(
            "candidate.impact.isForbidden()\n"
            "PromotionDecision.REQUIRES_OWNER_APPROVAL\n"
            "RepairKind.SOURCE_PATCH\n"
            "PromotionDecision.AUTO_PROMOTE_RUNTIME\n"
            "RepairKind.SPEC_TUNING",
            encoding="utf-8",
        )
        (root / TUNING).write_text(
            "RuntimeTuningKey.MODEL_ROUTING_MIN_SAMPLES\n"
            "RuntimeTuningKey.MODEL_ROUTING_MIN_SUCCESS_RATE_BPS\n"
            "RuntimeTuningKey.MODEL_ROUTING_MEMORY_DAYS\n"
            "patch.intValue !in 3..20\n"
            "patch.intValue !in 3_000..8_000\n"
            "patch.intValue !in 1..30\n"
            "RepairKind.SPEC_TUNING\n"
            "saveSpecRollback",
            encoding="utf-8",
        )
        synth = root / SYNTH
        synth.write_text(
            "PrivacySensitivity.HIGH\n"
            "requiresOffline = true\n"
            "InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM",
            encoding="utf-8",
        )
        (root / SPEC).write_text(
            "SpecTuningKey.PROMPT_SCHEMA_REMINDERS\n"
            "SpecTuningKey.PROMPT_REPAIR_ATTEMPTS\n"
            "SpecTuningKey.RESEARCH_MAX_SUBQUESTIONS\n"
            "SpecTuningKey.RESEARCH_CROSSCHECK_DEPTH\n"
            "patch.intValue !in 0..2\n"
            "patch.intValue !in 3..12\n"
            "patch.intValue !in 1..3",
            encoding="utf-8",
        )

        issues = audit_root(root)
        if any("ProcessBuilder(" in issue for issue in issues):
            print("SELFTEST PASS — Evolution audit catches process/shell escape")
            return 0
        print("SELFTEST FAIL — known-bad process execution was not detected")
        for issue in issues:
            print(" ", issue)
        return 1


if __name__ == "__main__":
    if selftest():
        sys.exit(2)
    issues = audit_root(Path("."))
    print(f"\nEVOLUTION BOUNDARY VIOLATIONS: {len(issues)}")
    for issue in issues:
        print(" ", issue)
    sys.exit(1 if issues else 0)
