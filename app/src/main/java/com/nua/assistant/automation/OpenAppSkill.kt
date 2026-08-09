package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAppSkill @Inject constructor(
    private val appLauncher: AppLauncher,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val app = intent.parameters["app"]
        return if (app.isNullOrBlank()) {
            NuaRouteResult.FallThroughToChat
        } else if (appLauncher.launch(app)) {
            NuaRouteResult.ActionTaken(ActionCopy.appOpened(app))
        } else {
            NuaRouteResult.ActionTaken(ActionCopy.appNotFound(app))
        }
    }
}
