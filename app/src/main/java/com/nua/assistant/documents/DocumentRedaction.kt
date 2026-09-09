package com.nua.assistant.documents

private val SSN_PATTERN = Regex("""\b\d{3}-\d{2}-\d{4}\b""")

/** Matches a run of 13-19 digits (the real range of card lengths in use), allowing spaces
 *  or dashes between groups the way cards are usually printed/typed. Luhn-checked below
 *  before actually redacting, since a raw digit-run regex alone would also catch long
 *  account/tracking/reference numbers that happen to fall in the same length range. */
private val DIGIT_RUN_PATTERN = Regex("""\b(?:\d[ -]?){12,18}\d\b""")

/**
 * Redacts the two structured PII patterns worth catching mechanically before document text
 * reaches a Claude prompt (DocumentAnalyzer.summarize/answer) — not before it's persisted:
 * this app's own local storage is already the trust boundary for everything else in it (see
 * PrivacyCentreScreen's "local-vs-Claude" framing), so redacting at rest would just make the
 * user's own document harder for NUA to read locally without protecting anything. Free-text
 * PII (a name, an address written in prose) isn't attempted — pattern-matching structured
 * numbers is the honestly-achievable slice; classifying arbitrary prose as sensitive is
 * exactly the kind of guess MemoryPrivacyLevel's own doc comment already argues against.
 */
fun redactSensitivePatterns(text: String): String {
    val ssnRedacted = SSN_PATTERN.replace(text) { "[REDACTED-SSN]" }
    return DIGIT_RUN_PATTERN.replace(ssnRedacted) { match ->
        val digitsOnly = match.value.filter { it.isDigit() }
        if (digitsOnly.length in 13..19 && passesLuhnCheck(digitsOnly)) "[REDACTED-CARD]" else match.value
    }
}

/** The standard Luhn checksum used by every major card network — pure so it's directly testable. */
internal fun passesLuhnCheck(digits: String): Boolean {
    if (digits.isEmpty()) return false
    var sum = 0
    var doubleNext = false
    for (index in digits.length - 1 downTo 0) {
        var digit = digits[index] - '0'
        if (doubleNext) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        doubleNext = !doubleNext
    }
    return sum % 10 == 0
}
