package com.nua.assistant.remote

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.NuaSkill
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.automation.SkillParameter
import com.nua.assistant.automation.TIMEOUT_NETWORK_MILLIS
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteToolSkill @Inject constructor() : NuaSkill {
    override val manifest = SkillManifest(
        parameters = listOf(
            SkillParameter("endpointId", true),
            SkillParameter("protocol", true),
            SkillParameter("tool", true),
            SkillParameter("argumentsJson", true),
            SkillParameter("privacyCapsuleJson", true),
            SkillParameter("idempotencyKey", true),
        ),
        timeoutMillis = TIMEOUT_NETWORK_MILLIS,
    )

    override suspend fun execute(
        intent: ClassifiedIntent,
        originalUtterance: String,
        pinnedLanguage: NuaLanguage?,
    ): NuaRouteResult = NuaRouteResult.ActionTaken(
        "Remote tools execute only through NUA's approved UAF remote gateway.",
        succeeded = false,
    )
}
