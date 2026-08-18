package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanTaskSkill @Inject constructor(
    private val taskPlanner: TaskPlanner,
) : NuaSkill {

    override val manifest = SkillManifest(
        parameters = listOf(SkillParameter("activity")),
        timeoutMillis = TIMEOUT_CLAUDE_MILLIS,
    )

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val activity = intent.parameters["activity"] ?: originalUtterance
        val plan = taskPlanner.propose(activity, pinnedLanguage)
        return if (plan != null) NuaRouteResult.PlanProposed(plan) else NuaRouteResult.FallThroughToChat
    }
}
