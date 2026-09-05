package com.nua.assistant.briefing

/**
 * What the daily briefing actually returns — structured sections rather than one prose
 * paragraph, so a notification/chat rendering can show what changed, what needs
 * attention, risks, opportunities, and one recommendation separately, and so
 * "genuinely nothing unusual" is a distinct, honest state rather than Claude padding out
 * every section to have something to say.
 */
sealed class DailyBriefing {
    /**
     * Every list is empty, and [recommendation] is null, when nothing in that category
     * applies today — never filled with restated raw data just to have content.
     */
    data class Summary(
        val today: String,
        val attention: List<String> = emptyList(),
        val contextChanges: List<String> = emptyList(),
        val risks: List<String> = emptyList(),
        val opportunities: List<String> = emptyList(),
        val recommendation: String? = null,
    ) : DailyBriefing()

    /** The honest anti-case: nothing about today rises above routine. */
    data object NothingUnusual : DailyBriefing()
}
