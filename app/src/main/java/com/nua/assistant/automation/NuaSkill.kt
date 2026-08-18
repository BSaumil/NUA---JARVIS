package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.voice.NuaLanguage
import dagger.MapKey

/** Dagger multibinding key — see SkillModule.kt. */
@MapKey
annotation class ActionTypeKey(val value: NuaActionType)

/**
 * One concrete NUA capability, keyed by the NuaActionType it handles. NuaIntentRouter
 * no longer knows about any specific capability — it just looks up the matching skill
 * in the Map<NuaActionType, NuaSkill> Hilt assembles from every @IntoMap binding in
 * SkillModule.kt and dispatches to it. Adding a new Tier 1 action means adding one new
 * NuaSkill implementation and one new binding, not another NuaIntentRouter branch.
 */
interface NuaSkill {
    /**
     * This skill's declared contract — inputs, hard permission preconditions, and time
     * budget. Deliberately abstract with no default: the Agent Sandbox's premise is that
     * *every* tool declares itself, so a new skill can't slip through undeclared.
     * [SkillSandbox] enforces it before and during [execute].
     */
    val manifest: SkillManifest

    suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult
}

/**
 * Varied phrasing for the fast keyword/classification path, so action confirmations
 * carry a bit of NUA's voice without paying for a Claude call just to phrase "Done."
 * Picking randomly among a few options also keeps repeated actions from reading as
 * canned — the fast path can't evolve tone with familiarity the way chat replies do
 * (see PersonalityEngine), but it doesn't have to sound like a fixed script either.
 */
object ActionCopy {
    fun appOpened(app: String): String = listOf(
        "Opening $app.",
        "On it — launching $app.",
        "$app, coming right up.",
    ).random()

    fun appNotFound(app: String): String =
        "Couldn't find anything called \"$app\" on here — mistyped, or not installed?"

    fun mediaStarted(query: String): String = listOf(
        "Cueing up \"$query\" for you.",
        "Starting something for \"$query\".",
    ).random()

    fun mediaControlHandled(): String = listOf("Done.", "Handled.", "There you go.").random()

    fun weather(condition: String, currentTempC: Double, highTempC: Double, precipitationChancePercent: Int): String {
        val base = "It's $condition and ${currentTempC.toInt()}°C, high of ${highTempC.toInt()}°C today with a $precipitationChancePercent% chance of rain."
        val remark = when {
            precipitationChancePercent >= 60 -> " Bring an umbrella."
            precipitationChancePercent <= 10 && currentTempC >= 22 -> " Good excuse to get outside."
            else -> ""
        }
        return base + remark
    }
}
