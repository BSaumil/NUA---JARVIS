package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.smarthome.SmartHomeRepository
import com.nua.assistant.smarthome.SmartHomeResult
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartHomeSkill @Inject constructor(
    private val smartHomeRepository: SmartHomeRepository,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val device = intent.parameters["device"]
        val action = intent.parameters["action"]
        if (device.isNullOrBlank() || action.isNullOrBlank()) return NuaRouteResult.FallThroughToChat

        val message = when (val result = smartHomeRepository.controlDevice(device, action)) {
            is SmartHomeResult.Success -> result.message
            is SmartHomeResult.NotConfigured -> result.reason
            is SmartHomeResult.Failure -> "Couldn't do that — ${result.message}"
        }
        return NuaRouteResult.ActionTaken(message)
    }
}
