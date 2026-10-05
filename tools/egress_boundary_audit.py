#!/usr/bin/env python3
"""Data Egress Gateway boundary audit — Privacy Capsules directive (Feature 7).

DataEgressGateway.kt's own doc comment states the property this enforces: every
cloud-bound personal-data flow passes through one gateway and produces a real
EgressDecision, rather than reaching ClaudeApiClient/ModelMesh with no decision point at
all. ModelMesh.complete(...) already carries that guarantee structurally (every caller
builds a TaskContract, see tools covering Model Mesh). The two call shapes that bypass
ModelMesh entirely -- ClaudeApiClient.describeImage(...) (images) and
ClaudeApiClient.streamMessage(...) (the live conversation history) -- don't carry any
such guarantee on their own; nothing stops a future call site from sending a photo or the
chat history straight to Claude with no egress decision recorded at all, the exact gap
this pass closed for VisionAnalyzer/DocumentAnalyzer/NuaViewModel.

This audit's claim: every file with a real `.describeImage(` or `.streamMessage(` *call*
(not ClaudeApiClient.kt's own definitions, which use the `fun` keyword, not a leading
dot) also calls `DataEgressGateway.` at least once. File-level, not control-flow-level --
the same granularity as uaf_boundary_audit.py/injection_boundary_audit.py already use --
so a future call site in an already-allowlisted file still needs its own egress call to
not trip other, finer review, but a genuinely new file calling either method with zero
DataEgressGateway involvement is exactly what this catches.

Self-test:  python3 tools/egress_boundary_audit.py --selftest
  Reconstructs a synthetic caller of describeImage(...) with no DataEgressGateway
  involvement and asserts the detector reports it.
"""
import re
import sys
import glob
import os
import tempfile

CLOUD_CALL = re.compile(r'\.(describeImage|streamMessage)\(')
EGRESS_CALL = re.compile(r'\bDataEgressGateway\.')

# File path suffixes expected to call a sensitive cloud method AND already call through
# DataEgressGateway for it.
ALLOWLIST = {
    'app/src/main/java/com/nua/assistant/ui/NuaViewModel.kt',
    'app/src/main/java/com/nua/assistant/vision/VisionAnalyzer.kt',
    'app/src/main/java/com/nua/assistant/documents/DocumentAnalyzer.kt',
}


def find_cloud_callers(roots):
    """Returns {file: bool} -- True if that file also calls DataEgressGateway."""
    callers = {}
    for r in roots:
        for path in sorted(glob.glob(os.path.join(r, 'src', 'main', '**', '*.kt'), recursive=True)):
            text = open(path).read()
            if CLOUD_CALL.search(text):
                rel = path.replace(os.sep, '/')
                callers[rel] = bool(EGRESS_CALL.search(text))
    return callers


def audit(roots):
    """Returns (unguarded, missing). unguarded: files calling describeImage/streamMessage
    with no DataEgressGateway call anywhere in the file. missing: allowlisted files that
    no longer call either cloud method at all (a stale allowlist entry)."""
    callers = find_cloud_callers(roots)
    unguarded = [f for f, guarded in callers.items() if not guarded]
    missing = [f for f in ALLOWLIST if not any(c.endswith(f) for c in callers)]
    return unguarded, missing


SELFTEST_UNGUARDED = '''
package com.nua.assistant.vision
class RogueVisionShortcut {
    // A hypothetical regression: a new call site that sends a photo straight to Claude
    // with no egress decision recorded at all.
    suspend fun leak(client: ClaudeApiClient, imageBase64: String) {
        client.describeImage(imageBase64, "image/jpeg", "describe", null)
    }
}
'''

SELFTEST_GUARDED = '''
package com.nua.assistant.vision
class CarefulVisionCaller {
    suspend fun analyze(client: ClaudeApiClient, imageBase64: String) {
        DataEgressGateway.recordEgress(DataCategory.VISION_CONTENT, 1, purpose = "test")
        client.describeImage(imageBase64, "image/jpeg", "describe", null)
    }
}
'''


def selftest():
    d = tempfile.mkdtemp()
    vision_dir = os.path.join(d, 'app', 'src', 'main', 'java', 'com', 'nua', 'assistant', 'vision')
    os.makedirs(vision_dir, exist_ok=True)
    open(os.path.join(vision_dir, 'RogueVisionShortcut.kt'), 'w').write(SELFTEST_UNGUARDED)
    open(os.path.join(vision_dir, 'CarefulVisionCaller.kt'), 'w').write(SELFTEST_GUARDED)

    old_allowlist = set(ALLOWLIST)
    ALLOWLIST.clear()
    ALLOWLIST.add('app/src/main/java/com/nua/assistant/vision/CarefulVisionCaller.kt')
    try:
        unguarded, missing = audit([os.path.join(d, 'app')])
    finally:
        ALLOWLIST.clear()
        ALLOWLIST.update(old_allowlist)

    if any(f.endswith('RogueVisionShortcut.kt') for f in unguarded) and \
            not any(f.endswith('CarefulVisionCaller.kt') for f in unguarded) and not missing:
        print('SELFTEST PASS — detector flags a describeImage(...) caller with no DataEgressGateway involvement')
        return 0
    print('SELFTEST FAIL — detector did not catch the unguarded call site.')
    print('  unguarded:', unguarded, '\n  missing:', missing)
    return 1


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    if selftest():
        sys.exit(2)
    roots = [a for a in sys.argv[1:] if not a.startswith('-')] or ['app']
    unguarded, missing = audit(roots)
    print(f'\nUNGUARDED CLOUD CALL SITES: {len(unguarded)}')
    for f in unguarded:
        print(f'  {f}: calls describeImage/streamMessage with no DataEgressGateway call in the file')
    print(f'STALE ALLOWLIST ENTRIES: {len(missing)}')
    for f in missing:
        print(f'  {f}: allowlisted but no describeImage/streamMessage call found there anymore')
    print(f'\nroots={roots}  unguarded={len(unguarded)}  missing={len(missing)}')
    sys.exit(1 if (unguarded or missing) else 0)
