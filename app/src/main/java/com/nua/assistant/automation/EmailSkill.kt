package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.email.EmailRepository
import com.nua.assistant.email.EmailResult
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailSkill @Inject constructor(
    private val emailRepository: EmailRepository,
) : NuaSkill {

    override val manifest = SkillManifest(timeoutMillis = TIMEOUT_NETWORK_MILLIS)

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val result = emailRepository.checkInbox()
        val message = when (result) {
            is EmailResult.Success -> result.message
            is EmailResult.NotConfigured -> result.reason
            is EmailResult.Failure -> "Couldn't check email — ${result.message}"
        }
        return NuaRouteResult.ActionTaken(message, succeeded = result is EmailResult.Success)
    }
}
