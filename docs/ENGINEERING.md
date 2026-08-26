# NUA Engineering Practice

## The verification gate

Local Gradle is unavailable in the development sandbox this project is built in. **CI is
the authoritative build environment** — nothing else compiles the app. That makes the
order below non-negotiable rather than a nicety:

```text
CHANGE
 ↓
UNIT TESTS
 ↓
PUSH
 ↓
CI
 ↓
EXACT SHA GREEN
 ↓
SECURITY / BEHAVIOURAL VERIFICATION
 ↓
REVIEW
 ↓
MERGE
```

**A change is not verified until the exact commit SHA has passed CI.** Not the branch,
not "the last run I looked at" — the SHA in `git rev-parse HEAD`.

This rule exists because it was broken. `32887d3` (the command palette) was pushed
without its CI result being checked and sat red for five days. The failure was a Kotlin
compile error, so `testDebugUnitTest` never ran at all — including
`CommandPaletteTest`, the test written specifically to assert the feature's security
property. A capability was believed shipped while its evidence had never executed once.

Three corollaries, each earned the hard way:

- **Compilation is not behavioural verification.** Green means it built and the tests
  that exist passed. It says nothing about tests you did not write.
- **A passing test suite is not proof a specific test ran.** Check for the task
  (`:app:testDebugUnitTest`), not just `BUILD SUCCESSFUL`. `NO-SOURCE` means a module has
  no tests — `:wear:testDebugUnitTest` is `NO-SOURCE` today.
- **A green static check from an unvalidated tool is worth nothing.** See below.

## Kotlin: property initialisers run in declaration order

**A property initialiser may only read members declared above it.**

Kotlin compiles initialisers into the constructor in source order. Reading a member
declared further down reads an unassigned field. Two shapes:

| Shape | Example | Caught by |
|---|---|---|
| **Eager** — read while the initialiser runs | `val a = combine(b, c)` with `b`, `c` below | the compiler: `Variable 'b' must be initialized` |
| **Deferred** — captured in a lambda that runs later | `val a = flow.map { b.value }` with `b` below | **nothing** — compiles, then reads null through a non-null type |

The eager shape is what broke `32887d3`. The deferred shape is worse: it compiles
cleanly and fails at runtime.

`tools/forward_ref_audit.py` enforces the rule and runs in CI on every push. It reports
any forward reference regardless of shape, because the two cannot be reliably told apart:
lambda parameters routinely shadow the members they are built from
(`combine(facts, dreams) { facts, dreams -> ... }`), which defeats brace-depth analysis.
A simple sound rule beats a clever unreliable one.

**The detector self-tests before it audits.** It reconstructs the known `bcb4515` defect
and asserts it reports it; if it cannot, it exits non-zero instead of reporting a clean
tree. The first version of this script returned "0 findings, codebase clean" while being
structurally unable to see the bug it was written for — it never registered a class body
when the header spanned multiple lines (`class NuaViewModel @Inject constructor(` has no
brace on its line). Never trust a zero from a check that has not proven it can find a one.

## Retrieving CI logs

The GitHub MCP `get_job_logs` tool returns only a **tail** of the log. Gradle prints
roughly 200 lines of stack trace *after* the actual error, so small tails are pure noise —
tails of 60, 220 and 450 lines on the `32887d3` failure contained no error line at all.

```text
get_job_logs(job_id=…, return_content=true, tail_lines=1000)
  → oversized, saved to a file
  → grep that file for '^e: '     (Kotlin compiler errors)
```

Also useful: `Task :app:compile`, `FAILED`, `BUILD `, `actionable tasks`.

The `logs_url` fallback does **not** work in this environment —
`productionresultssa2.blob.core.windows.net` returns `CONNECT tunnel failed, 403`
through the agent proxy. Do not spend time on it.

## Testing pure logic without a device

Android types cannot be instantiated in a JVM unit test, so behaviour that must be
verified is extracted into pure functions over plain data, with the Android-facing wrapper
kept thin: `buildPalette`/`CommandPaletteSheet`, `evaluateDiagnostics`/
`SelfDiagnosticsRepository`, `deriveNuaState`/`NuaStateRepository`,
`OrbState.appearanceFor`, `SkillManifest`, `NuaDestination`.

This is why the palette's security property is assertable at all. It is also the pattern's
limit: it catches logic bugs, not wiring bugs. The `32887d3` failure was in the wiring
layer — exactly the part that cannot be exercised on the JVM here — which is what the
forward-reference audit now covers.

## Security invariants

Every autonomous capability is tagged `T0`–`T5` (`AutonomyTier`). The tier decides what
NUA may do unattended.

- **The tier gate is ordinal-based.** `AutonomyTier` declaration order therefore *is* the
  security policy. `CommandPaletteTest.tier declaration order is risk-ascending` pins it,
  because reordering the enum would silently invert every gate rather than fail to compile.
- **Fail closed.** A tier above `T2` never executes from the command palette; any future
  tier added below `T5` inherits that automatically via the ordinal comparison.
- **External content is data, never authority.** Remembered text, documents, notifications
  and email reach the model wrapped in delimiters (`wrapUntrusted`) and can only ever
  become a search query — never a dispatched action. Only a `UserUtterance` (the user's own
  words) and the closed skill set reach `intentRouter.route`.

When authority is ambiguous, deny. Do not weaken these to make a test pass; change the
test only when the invariant itself has been deliberately revised.
