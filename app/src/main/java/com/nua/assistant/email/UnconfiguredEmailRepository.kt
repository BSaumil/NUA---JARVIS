package com.nua.assistant.email

import javax.inject.Inject
import javax.inject.Singleton

/** Default binding for EmailRepository — always reports not-configured; see that interface's doc comment. */
@Singleton
class UnconfiguredEmailRepository @Inject constructor() : EmailRepository {

    override suspend fun checkInbox(): EmailResult =
        EmailResult.NotConfigured(
            "Email isn't connected yet — it needs a Gmail API project with OAuth " +
                "consent, which happens outside the app.",
        )
}
