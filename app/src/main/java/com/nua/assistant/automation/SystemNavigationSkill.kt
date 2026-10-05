package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 2's first real action (directive item 14) — see
 * [NuaAccessibilityService.performSystemNavigation]'s doc comment for why this is
 * genuinely accessibility-exclusive rather than another Tier 1 Intent-based skill in
 * disguise. Same "resolve a command parameter, report success/failure, never throw"
 * shape [MediaControlSkill] already uses for an identical reason (both dispatch on a
 * closed set of string commands from [ClassifiedIntent.parameters]).
 */
@Singleton
class SystemNavigationSkill @Inject constructor() : NuaSkill {

    override val manifest = SkillManifest(parameters = listOf(SkillParameter("command", required = true)))

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val command = intent.parameters["command"]
        if (command == null) {
            return NuaRouteResult.ActionTaken("I need to know which navigation gesture — home, back, or recents.", succeeded = false)
        }
        val performed = NuaAccessibilityService.performSystemNavigation(command)
        return if (performed) {
            NuaRouteResult.ActionTaken("Done.")
        } else {
            NuaRouteResult.ActionTaken(
                NuaAccessibilityService.lastFailure.value ?: "Couldn't perform that navigation gesture.",
                succeeded = false,
            )
        }
    }
}
