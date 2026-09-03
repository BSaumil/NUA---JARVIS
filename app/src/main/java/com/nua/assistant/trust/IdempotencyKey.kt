package com.nua.assistant.trust

private const val KEY_SEPARATOR = "|"

/**
 * A deterministic key identifying "this exact action, with these exact inputs" — not a
 * random token. Two dispatches of the same confirmed SMS, reply, or skill invocation
 * produce the same key, so a duplicate dispatch (a replayed confirmation, a retry, a
 * future auto-approval firing twice for the same request) can be recognised and
 * suppressed by TrustRepository.wasRecentlyExecuted instead of repeating a real-world
 * side effect. A coincidental collision (two genuinely different actions hashing to the
 * same key) only means "treat these as the same action" — always the safe direction to
 * be wrong in here, unlike the reverse.
 */
fun idempotencyKeyFor(actionType: String, vararg components: String): String =
    (listOf(actionType) + components).joinToString(KEY_SEPARATOR)
