package com.nua.assistant.privacy

import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.memory.UserFactEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain-text export of everything NUA has stored — not a proprietary format, just
 * readable text the user can save or share wherever they choose. Pure: no Android,
 * network, or DB access, so it's directly unit-testable.
 */
fun buildDataExport(
    facts: List<UserFactEntity>,
    goals: List<GoalEntity>,
    decisions: List<DecisionEntity>,
    dreams: List<DreamEntity>,
    generatedAtMillis: Long = System.currentTimeMillis(),
): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return buildString {
        appendLine("NUA data export — generated ${dateFormat.format(Date(generatedAtMillis))}")
        appendLine()
        appendLine("== Facts (${facts.size}) ==")
        if (facts.isEmpty()) appendLine("(none)")
        facts.forEach { appendLine("- [${it.memoryType}] ${it.value} (${it.category})") }
        appendLine()
        appendLine("== Goals (${goals.size}) ==")
        if (goals.isEmpty()) appendLine("(none)")
        goals.forEach { appendLine("- [${it.type}]${if (it.active) "" else " (inactive)"} ${it.text}") }
        appendLine()
        appendLine("== Decisions (${decisions.size}) ==")
        if (decisions.isEmpty()) appendLine("(none)")
        decisions.forEach {
            appendLine("- ${it.decision}")
            if (it.reasoning != null) appendLine("  Why: ${it.reasoning}")
            if (it.outcome != null) appendLine("  Outcome: ${it.outcome}")
        }
        appendLine()
        appendLine("== Dreams (${dreams.size}) ==")
        if (dreams.isEmpty()) appendLine("(none)")
        dreams.forEach { appendLine("- [${it.category}] ${it.text}") }
    }.trim()
}
