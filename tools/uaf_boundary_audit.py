#!/usr/bin/env python3
"""UAF execution-boundary audit — Guardian Lab baseline (Feature 10).

The Universal Action Fabric's whole safety argument (automation/uaf/ActionAdapter.kt's
own doc comment) rests on one fact staying true: ActionAdapter.execute(...) — the method
that actually performs a capability, whichever mechanism it uses — is only ever called
from WorkflowExecutor.runOneStep, which is the one place isAuthorizationSufficient() is
consulted first. Kotlin's type system doesn't enforce that on its own: any code holding a
reference to an ActionAdapter (or constructing LocalNativeAdapter/
NotificationRemoteInputAdapter directly) could call .execute(...) itself and skip the
authorization check entirely — a second, unguarded execution path. Nothing stops a future
call site from doing exactly that except someone noticing in review.

Answers the final report's own security-review question #1 ("Can any side-effecting
action bypass UAF?") with an enforced check, not just an assertion — the same
self-proving static-audit discipline as forward_ref_audit.py/injection_boundary_audit.py.

Self-test:  python3 tools/uaf_boundary_audit.py --selftest
  Reconstructs a synthetic call site outside the allowlist (standing in for, e.g., a
  future feature that grabs an ActionAdapter and calls .execute(...) directly instead of
  going through WorkflowExecutor) and asserts the detector reports it.
"""
import re
import sys
import glob
import os
import tempfile

# Matches `<something>.execute(descriptor, ...)` — the literal call shape every
# ActionAdapter implementation's execute() is invoked with (see ActionAdapter.kt's
# `execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context:
# AdapterExecutionContext)`). Deliberately keyed on the first-parameter name
# (`descriptor`) rather than the receiver name, since the receiver varies (`adapter`,
# `local`, a future variable name) but every real call site passes a CapabilityDescriptor
# first.
EXECUTE_CALL = re.compile(r'\.execute\(\s*descriptor\s*,')

# (file path suffix, expected number of ActionAdapter.execute(...) call sites in that file)
ALLOWLIST = {
    'app/src/main/java/com/nua/assistant/automation/uaf/WorkflowExecutor.kt': 1,
}


def find_call_sites(roots):
    sites = {}
    for r in roots:
        for path in sorted(glob.glob(os.path.join(r, 'src', 'main', '**', '*.kt'), recursive=True)):
            text = open(path).read()
            count = len(EXECUTE_CALL.findall(text))
            if count:
                rel = path.replace(os.sep, '/')
                sites[rel] = count
    return sites


def audit(roots):
    """Returns (unexpected, missing) — same shape as injection_boundary_audit.py's audit()."""
    sites = find_call_sites(roots)
    unexpected = []
    for file, count in sites.items():
        allowed = next((v for k, v in ALLOWLIST.items() if file.endswith(k)), 0)
        if count > allowed:
            unexpected.append((file, count, allowed))
    missing = []
    for allowed_file in ALLOWLIST:
        if not any(f.endswith(allowed_file) for f in sites):
            missing.append(allowed_file)
    return unexpected, missing


SELFTEST_ALLOWED = '''
package com.nua.assistant.automation.uaf
class WorkflowExecutor {
    suspend fun runOneStep(step: PlanStep) {
        val result = adapter.execute(descriptor, step.parameters, context)
    }
}
'''

SELFTEST_UNAUTHORIZED = '''
package com.nua.assistant.automation.uaf
class QuickActionShortcut {
    // A hypothetical regression: a future "run this one action right now" convenience
    // path that grabs an adapter directly and calls execute(), skipping
    // isAuthorizationSufficient() and WorkflowExecutor's lineage recording entirely.
    suspend fun runNow(adapter: ActionAdapter, descriptor: CapabilityDescriptor) {
        adapter.execute(descriptor, emptyMap(), context)
    }
}
'''


def selftest():
    d = tempfile.mkdtemp()
    good_dir = os.path.join(d, 'app', 'src', 'main', 'java', 'com', 'nua', 'assistant', 'automation', 'uaf')
    os.makedirs(good_dir, exist_ok=True)
    open(os.path.join(good_dir, 'WorkflowExecutor.kt'), 'w').write(SELFTEST_ALLOWED)
    open(os.path.join(good_dir, 'QuickActionShortcut.kt'), 'w').write(SELFTEST_UNAUTHORIZED)

    old_allowlist = dict(ALLOWLIST)
    ALLOWLIST.clear()
    ALLOWLIST.update({'app/src/main/java/com/nua/assistant/automation/uaf/WorkflowExecutor.kt': 1})
    try:
        unexpected, missing = audit([os.path.join(d, 'app')])
    finally:
        ALLOWLIST.clear()
        ALLOWLIST.update(old_allowlist)

    flagged = {f for f, _, _ in unexpected}
    if any(f.endswith('QuickActionShortcut.kt') for f in flagged) and not missing:
        print('SELFTEST PASS — detector flags an unauthorized ActionAdapter.execute(...) call site')
        return 0
    print('SELFTEST FAIL — detector did not catch the unauthorized call site.')
    print('  unexpected:', unexpected, '\n  missing:', missing)
    return 1


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    if selftest():
        sys.exit(2)
    roots = [a for a in sys.argv[1:] if not a.startswith('-')] or ['app']
    unexpected, missing = audit(roots)
    print(f'\nUNEXPECTED CALL SITES: {len(unexpected)}')
    for f, found, allowed in unexpected:
        print(f'  {f}: {found} ActionAdapter.execute(...) call site(s), {allowed} reviewed and allowlisted')
    print(f'STALE ALLOWLIST ENTRIES: {len(missing)}')
    for f in missing:
        print(f'  {f}: allowlisted but no ActionAdapter.execute(...) call site found there anymore')
    print(f'\nroots={roots}  unexpected={len(unexpected)}  missing={len(missing)}')
    sys.exit(1 if (unexpected or missing) else 0)
