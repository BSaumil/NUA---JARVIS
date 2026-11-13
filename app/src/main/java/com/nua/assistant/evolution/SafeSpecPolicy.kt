package com.nua.assistant.evolution

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PromptSpecPolicy(
    val schemaReminderCount: Int = 1,
    val maxRepairAttempts: Int = 1,
)

data class ResearchSpecPolicy(
    val maxSubquestions: Int = 6,
    val crossCheckDepth: Int = 2,
)

@Singleton
class SafeSpecPolicyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("nua_safe_spec_policy", Context.MODE_PRIVATE)

    fun prompt(): PromptSpecPolicy = PromptSpecPolicy(
        schemaReminderCount = prefs.getInt("prompt_schema_reminders", 1).coerceIn(0, 2),
        maxRepairAttempts = prefs.getInt("prompt_repair_attempts", 1).coerceIn(0, 2),
    )

    fun research(): ResearchSpecPolicy = ResearchSpecPolicy(
        maxSubquestions = prefs.getInt("research_max_subquestions", 6).coerceIn(3, 12),
        crossCheckDepth = prefs.getInt("research_crosscheck_depth", 2).coerceIn(1, 3),
    )

    fun replace(prompt: PromptSpecPolicy, research: ResearchSpecPolicy) {
        prefs.edit()
            .putInt("prompt_schema_reminders", prompt.schemaReminderCount.coerceIn(0, 2))
            .putInt("prompt_repair_attempts", prompt.maxRepairAttempts.coerceIn(0, 2))
            .putInt("research_max_subquestions", research.maxSubquestions.coerceIn(3, 12))
            .putInt("research_crosscheck_depth", research.crossCheckDepth.coerceIn(1, 3))
            .apply()
    }
}

fun applySpecPatches(
    prompt: PromptSpecPolicy,
    research: ResearchSpecPolicy,
    patches: List<SpecTuningPatch>,
): Pair<PromptSpecPolicy, ResearchSpecPolicy>? {
    var nextPrompt = prompt
    var nextResearch = research
    patches.forEach { patch ->
        when (patch.key) {
            SpecTuningKey.PROMPT_SCHEMA_REMINDERS -> {
                if (patch.intValue !in 0..2) return null
                nextPrompt = nextPrompt.copy(schemaReminderCount = patch.intValue)
            }
            SpecTuningKey.PROMPT_REPAIR_ATTEMPTS -> {
                if (patch.intValue !in 0..2) return null
                nextPrompt = nextPrompt.copy(maxRepairAttempts = patch.intValue)
            }
            SpecTuningKey.RESEARCH_MAX_SUBQUESTIONS -> {
                if (patch.intValue !in 3..12) return null
                nextResearch = nextResearch.copy(maxSubquestions = patch.intValue)
            }
            SpecTuningKey.RESEARCH_CROSSCHECK_DEPTH -> {
                if (patch.intValue !in 1..3) return null
                nextResearch = nextResearch.copy(crossCheckDepth = patch.intValue)
            }
        }
    }
    return nextPrompt to nextResearch
}
