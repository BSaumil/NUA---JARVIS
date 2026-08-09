package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.briefing.MorningBriefing
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningBriefingSkill @Inject constructor(
    private val morningBriefing: MorningBriefing,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult =
        NuaRouteResult.ActionTaken(morningBriefing.generate(originalUtterance, pinnedLanguage))
}
