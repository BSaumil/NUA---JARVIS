package com.nua.assistant.ai

/**
 * Claude occasionally wraps a requested JSON reply in a markdown code fence or adds a
 * stray sentence around it despite being asked for JSON only. Every structured call in
 * ai/ (intent classification, fact extraction, planning, notification scoring) goes
 * through this before decoding so a small amount of chatter doesn't break parsing.
 */
fun extractJsonPayload(raw: String): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)
    val candidate = fenced?.groupValues?.get(1)?.trim() ?: raw.trim()

    val start = candidate.indexOfFirst { it == '{' || it == '[' }
    if (start == -1) return candidate

    val opening = candidate[start]
    val closing = if (opening == '{') '}' else ']'
    val end = candidate.lastIndexOf(closing)
    if (end == -1 || end < start) return candidate

    return candidate.substring(start, end + 1)
}
