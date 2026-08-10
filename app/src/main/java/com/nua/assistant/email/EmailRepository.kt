package com.nua.assistant.email

sealed class EmailResult {
    data class Success(val message: String) : EmailResult()
    data class NotConfigured(val reason: String) : EmailResult()
    data class Failure(val message: String) : EmailResult()
}

/**
 * Extension point for email (Gmail-style) access. Unlike calendar/CalendarReader.kt,
 * there's no on-device content-provider equivalent for email — every mail provider
 * requires its own OAuth app registration and consent flow, which has to happen
 * outside this app (a Google Cloud project with the Gmail API enabled, an OAuth
 * client ID, and Google's verification for anything beyond a handful of test users).
 * UnconfiguredEmailRepository is what's actually wired up today; swap the Hilt
 * binding in EmailModule.kt for a real implementation once that setup exists.
 */
interface EmailRepository {
    suspend fun checkInbox(): EmailResult
}
