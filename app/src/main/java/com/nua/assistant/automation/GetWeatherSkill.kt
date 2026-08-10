package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.weather.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetWeatherSkill @Inject constructor(
    private val weatherRepository: WeatherRepository,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val snapshot = weatherRepository.currentSnapshot().getOrNull()
        val message = snapshot?.let { ActionCopy.weather(it.condition, it.currentTempC, it.highTempC, it.precipitationChancePercent) }
            ?: "Couldn't get a weather reading — check that location access is granted."
        return NuaRouteResult.ActionTaken(message, succeeded = snapshot != null)
    }
}
