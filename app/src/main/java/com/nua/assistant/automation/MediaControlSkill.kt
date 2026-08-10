package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.media.MediaControlManager
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControlSkill @Inject constructor(
    private val mediaControlManager: MediaControlManager,
) : NuaSkill {

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val handled = when (intent.parameters["command"]) {
            "play" -> mediaControlManager.play()
            "pause" -> mediaControlManager.pause()
            "next" -> mediaControlManager.next()
            "previous" -> mediaControlManager.previous()
            else -> false
        }
        return if (handled) {
            NuaRouteResult.ActionTaken(ActionCopy.mediaControlHandled())
        } else {
            NuaRouteResult.ActionTaken("Nothing's playing right now — nothing to control.")
        }
    }
}
