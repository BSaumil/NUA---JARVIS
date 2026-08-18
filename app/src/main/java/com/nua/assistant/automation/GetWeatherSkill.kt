package com.nua.assistant.automation

import android.Manifest
import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.weather.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetWeatherSkill @Inject constructor(
    private val weatherRepository: WeatherRepository,
) : NuaSkill {

    override val manifest = SkillManifest(
        requiredPermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
        timeoutMillis = TIMEOUT_NETWORK_MILLIS,
    )

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val snapshot = weatherRepository.currentSnapshot().getOrNull()
        val message = snapshot?.let { ActionCopy.weather(it.condition, it.currentTempC, it.highTempC, it.precipitationChancePercent) }
            // Location is guaranteed granted here — SkillSandbox checks the manifest first — so
            // a failure at this point is the lookup itself, not a missing permission.
            ?: "Couldn't get a weather reading just now — the lookup didn't come back."
        return NuaRouteResult.ActionTaken(message, succeeded = snapshot != null)
    }
}
