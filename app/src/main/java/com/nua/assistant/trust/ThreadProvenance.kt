package com.nua.assistant.trust

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Start of the trailing-24h window used for "how many times have I sent this recipient
 *  something today" — a same-day provenance question, distinct from
 *  TrustRepository.wasRecentlyExecuted's 5-minute exact-duplicate window. */
fun sameDayWindowStart(now: Long): Long = now - DAY_MILLIS

/** Pure: turns a same-day prior-send count into the trailing note appended to a send
 *  confirmation, or null when there's nothing to say (the common case — most sends are
 *  the only one that day). */
fun threadProvenanceNote(priorSendCount: Int): String? =
    if (priorSendCount <= 0) null else "This is message #${priorSendCount + 1} to them today."
