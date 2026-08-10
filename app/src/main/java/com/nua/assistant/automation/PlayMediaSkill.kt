package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.media.MediaControlManager
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayMediaSkill @Inject constructor(
    private val mediaControlManager: MediaControlManager,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val query = intent.parameters["query"] ?: originalUtterance
        return if (mediaControlManager.playByQuery(query)) {
            NuaRouteResult.ActionTaken(ActionCopy.mediaStarted(query))
        } else {
            NuaRouteResult.ActionTaken("No music app on here picked that up — is one actually installed?", succeeded = false)
        }
    }
}
