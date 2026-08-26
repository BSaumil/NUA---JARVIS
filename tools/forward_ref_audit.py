#!/usr/bin/env python3
"""Forward-reference audit for Kotlin property initialisers.

Kotlin compiles property initialisers into the constructor in DECLARATION ORDER.
A property whose initialiser reads a member declared further down the class body is
reading an uninitialised field. There are two shapes:

  EAGER    the member is read while the initialiser runs. The compiler rejects this:
           "Variable 'x' must be initialized."
           Real example: NuaViewModel.paletteMemories, fixed in bcb4515.

  DEFERRED the member is captured inside a lambda that runs later. The compiler
           ACCEPTS this. If the lambda can run before the later property is
           assigned, it observes null through a non-null type. This is the
           dangerous shape, and the reason this script exists.

The rule: a property initialiser may only read members declared ABOVE it.

Self-test:  python3 tools/forward_ref_audit.py --selftest
  Rebuilds the known bcb4515 defect and asserts the detector reports it. A version
  of this script that cannot catch that bug must not be trusted to report zero.
"""
import re, sys, glob, os, tempfile

def strip_noise(s):
    s = re.sub(r'"""(?:.|\n)*?"""', '""', s)
    s = re.sub(r'"(?:\\.|[^"\\])*"', '""', s)
    s = re.sub(r'//[^\n]*', '', s)
    return re.sub(r'/\*(?:.|\n)*?\*/', '', s)

DECL = re.compile(r'^\s*(?:private |internal |protected |lateinit |open |override |const )*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*[:=]')
TYPE = re.compile(r'\b(?:class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)')

def scopes_in(lines):
    """Yield (name, body_depth, first_body_line, end_line).

    Handles the multi-line header case — `class X @Inject constructor(...) : Y() {`
    puts the body brace many lines below `class X`, and constructor parameters must
    not be mistaken for members.
    """
    found, open_scopes = [], []
    depth = paren = 0
    pending = None
    for i, l in enumerate(lines):
        m = TYPE.search(l)
        if m:
            pending = m.group(1)   # a newer declaration always wins
        for ch in l:
            if ch == '(':
                paren += 1
            elif ch == ')':
                paren -= 1
            elif ch == '{':
                depth += 1
                if pending is not None and paren == 0:
                    open_scopes.append([pending, depth, i + 1, None])
                    pending = None
            elif ch == '}':
                for s in reversed(open_scopes):
                    if s[3] is None and s[1] == depth:
                        s[3] = i
                        break
                depth -= 1
        if pending is not None and re.search(r'^\s*(?:class|object|interface)\b.*\b(?:data|enum|sealed|value)?\b.*\)\s*$', l) and paren == 0 and '{' not in l:
            pending = None  # header-only declaration, no body
    found = [s for s in open_scopes if s[3] is not None]
    return found

def audit(roots):
    eager, deferred = [], []
    files = []
    for r in roots:
        files += glob.glob(os.path.join(r, 'src', '**', '*.kt'), recursive=True)
    for path in sorted(files):
        lines = strip_noise(open(path).read()).split('\n')
        for name, bdepth, s0, s1 in scopes_in(lines):
            d, members = 0, []
            for i in range(s0, s1):
                mm = DECL.match(lines[i])
                if mm and d == 0:
                    members.append((i, mm.group(1)))
                d += lines[i].count('{') - lines[i].count('}')
            if len(members) < 2:
                continue
            order = {n: i for i, n in members}
            for k, (i, n) in enumerate(members):
                j = members[k + 1][0] if k + 1 < len(members) else s1
                init = lines[i:j]
                for other, oi in order.items():
                    if other == n or oi <= i:
                        continue
                    pat = re.compile(r'(?<![A-Za-z0-9_.])' + re.escape(other) + r'(?![A-Za-z0-9_])')
                    if not any(pat.search(x) for x in init):
                        continue
                    eager.append((path, name, n, i + 1, other, oi + 1))
    return eager, deferred

SELFTEST = '''
class Probe @Inject constructor(
    private val dao: Dao,
) : ViewModel() {
    val paletteMemories: StateFlow<List<M>> =
        combine(facts, dreams) { f, d -> f + d }
            .stateIn(scope, Started, emptyList())

    val facts: StateFlow<List<F>> = dao.observeFacts()
        .stateIn(scope, Started, emptyList())

    val dreams: StateFlow<List<D>> = dao.observeDreams()
        .stateIn(scope, Started, emptyList())
}
'''

def selftest():
    d = tempfile.mkdtemp()
    os.makedirs(os.path.join(d, 'probe', 'src'), exist_ok=True)
    open(os.path.join(d, 'probe', 'src', 'Probe.kt'), 'w').write(SELFTEST)
    eager, deferred = audit([os.path.join(d, 'probe')])
    names = {(n, o) for _, _, n, _, o, _ in eager}
    want = {('paletteMemories', 'facts'), ('paletteMemories', 'dreams')}
    if want <= names:
        print('SELFTEST PASS — detector reproduces the bcb4515 defect:', sorted(want))
        return 0
    print('SELFTEST FAIL — detector did NOT catch the known defect.')
    print('  eager:', eager, '\n  deferred:', deferred)
    return 1

if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    if selftest():
        sys.exit(2)
    roots = [a for a in sys.argv[1:] if not a.startswith('-')] or ['app', 'wear']
    eager, deferred = audit(roots)
    print(f'\nFORWARD REFERENCES: {len(eager)}')
    for p, c, n, nl, o, ol in eager:
        print(f'  {p}\n    class {c}: {n}:{nl} reads {o}:{ol}')
    print(f'\nroots={roots}  findings={len(eager)}')
    sys.exit(1 if eager else 0)
