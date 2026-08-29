#!/usr/bin/env python3
"""Injection-boundary audit for UserUtterance construction.

The structural half of NUA's prompt-injection firewall (see
app/src/main/java/com/nua/assistant/security/UserUtterance.kt) is only as strong as one
fact staying true: NuaIntentRouter.route()/IntentClassifier.classify() are only ever
handed text the user actually typed or spoke. Kotlin's type system doesn't enforce that
on its own — UserUtterance is a value class, and it wraps whatever String is placed in
its constructor with equal willingness, including a document/vision/notification/email
body an attacker controls. Nothing stops a future call site from doing exactly that
except someone noticing in review.

This script is the same kind of static backstop as forward_ref_audit.py: it finds every
production call site that constructs a UserUtterance and fails if that set has grown
beyond the reviewed allowlist below. It does not (cannot) verify that the allowlisted
call sites are themselves fed genuine user input — that was checked by hand once, the
same way forward_ref_audit.py's own detector logic was checked by hand once — this
script's job is only to catch the set silently growing afterward.

Self-test:  python3 tools/injection_boundary_audit.py --selftest
  Reconstructs a synthetic call site outside the allowlist (standing in for, e.g., a
  DocumentAnalyzer that started routing extracted document text to dispatch) and asserts
  the detector reports it. A version of this script that cannot catch that shape must not
  be trusted to report zero.
"""
import re
import sys
import glob
import os
import tempfile

CONSTRUCTOR_CALL = re.compile(r'(?<![A-Za-z0-9_.])UserUtterance\s*\(')
DECLARATION_LINE = re.compile(r'\bvalue class UserUtterance\b')

# (file path suffix, expected number of constructor call sites in that file)
ALLOWLIST = {
    'app/src/main/java/com/nua/assistant/ui/NuaViewModel.kt': 1,
}


def find_call_sites(roots):
    """Returns {file_suffix: count} for every file under roots/src/main containing a
    UserUtterance(...) constructor call, keyed by the path relative to the repo root."""
    sites = {}
    for r in roots:
        for path in sorted(glob.glob(os.path.join(r, 'src', 'main', '**', '*.kt'), recursive=True)):
            text = open(path).read()
            if DECLARATION_LINE.search(text):
                continue  # UserUtterance.kt itself — the class declaration, not a call
            count = len(CONSTRUCTOR_CALL.findall(text))
            if count:
                rel = path.replace(os.sep, '/')
                sites[rel] = count
    return sites


def audit(roots):
    """Returns (unexpected, missing) — unexpected is [(file, found_count, allowed_count)]
    for a file with call sites the allowlist doesn't account for (new file, or more calls
    than expected); missing is [file, ...] for an allowlisted file with zero calls now
    (stale allowlist, likely a refactor that moved or removed the sanctioned call site)."""
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
package com.nua.assistant.ui
class NuaViewModel {
    fun sendMessage(message: String) {
        intentRouter.route(UserUtterance(message))
    }
}
'''

SELFTEST_UNAUTHORIZED = '''
package com.nua.assistant.documents
class DocumentAnalyzer {
    fun onExtracted(documentText: String) {
        // A hypothetical regression: routing extracted document text to dispatch as if
        // it were something the user typed.
        intentRouter.route(UserUtterance(documentText))
    }
}
'''


def selftest():
    d = tempfile.mkdtemp()
    good_dir = os.path.join(d, 'app', 'src', 'main', 'java', 'com', 'nua', 'assistant', 'ui')
    bad_dir = os.path.join(d, 'app', 'src', 'main', 'java', 'com', 'nua', 'assistant', 'documents')
    os.makedirs(good_dir, exist_ok=True)
    os.makedirs(bad_dir, exist_ok=True)
    open(os.path.join(good_dir, 'NuaViewModel.kt'), 'w').write(SELFTEST_ALLOWED)
    open(os.path.join(bad_dir, 'DocumentAnalyzer.kt'), 'w').write(SELFTEST_UNAUTHORIZED)

    old_allowlist = dict(ALLOWLIST)
    ALLOWLIST.clear()
    ALLOWLIST.update({'app/src/main/java/com/nua/assistant/ui/NuaViewModel.kt': 1})
    try:
        unexpected, missing = audit([os.path.join(d, 'app')])
    finally:
        ALLOWLIST.clear()
        ALLOWLIST.update(old_allowlist)

    flagged = {f for f, _, _ in unexpected}
    if any(f.endswith('DocumentAnalyzer.kt') for f in flagged) and not missing:
        print('SELFTEST PASS — detector flags an unauthorized UserUtterance(...) call site')
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
        print(f'  {f}: {found} UserUtterance(...) call site(s), {allowed} reviewed and allowlisted')
    print(f'STALE ALLOWLIST ENTRIES: {len(missing)}')
    for f in missing:
        print(f'  {f}: allowlisted but no UserUtterance(...) call site found there anymore')
    print(f'\nroots={roots}  unexpected={len(unexpected)}  missing={len(missing)}')
    sys.exit(1 if (unexpected or missing) else 0)
