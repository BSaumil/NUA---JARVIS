package com.nua.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nua.assistant.context.WhatNowResult
import com.nua.assistant.state.NuaState
import com.nua.assistant.state.PendingKind
import com.nua.assistant.state.PendingTask
import com.nua.assistant.state.UserAvailability
import com.nua.assistant.ui.orb.NuaOrb
import com.nua.assistant.ui.orb.OrbState
import com.nua.assistant.ui.theme.NuaBorderWidth
import com.nua.assistant.ui.theme.NuaCardCorner
import com.nua.assistant.ui.theme.NuaGradients
import com.nua.assistant.ui.theme.NuaTheme

/**
 * The Command Centre (`#13`) — NUA's home surface. This is a rendering of [NuaState],
 * not a chat window: what's happening, what NUA noticed, what to do next, and the Orb as
 * the way in. Chat is still one tap away, it's just no longer the whole UI.
 *
 * Everything shown here comes from real data (see `NuaStateRepository`). A card with no
 * data to show is omitted rather than filled with placeholder content — an empty
 * dashboard that says so is more honest than one padded out to look busy.
 */
@Composable
fun CommandCentreScreen(
    greeting: String,
    state: NuaState,
    orbState: OrbState,
    insight: String?,
    nextAction: WhatNowResult?,
    onOrbTap: () -> Unit,
    onOpenChat: () -> Unit,
    onAskWhatNow: () -> Unit,
    onDoNextAction: () -> Unit,
    onRemindNextActionLater: () -> Unit,
    onDismissNextAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(NuaTheme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { CommandCentreHeader(greeting, state) }
        item { TodayCard(state) }

        if (insight != null) {
            item { IntelligenceCard(insight) }
        }

        if (nextAction != null || state.currentObjective != null) {
            item {
                ActionCard(
                    nextAction = nextAction,
                    objective = state.currentObjective,
                    onAskWhatNow = onAskWhatNow,
                    onDoNextAction = onDoNextAction,
                    onRemindNextActionLater = onRemindNextActionLater,
                    onDismissNextAction = onDismissNextAction,
                )
            }
        }

        if (state.pendingTasks.isNotEmpty()) {
            item { PendingCard(state.pendingTasks) }
        }

        if (state.recentMistakes.isNotEmpty()) {
            item { MistakesCard(state.recentMistakes) }
        }

        item { OrbRow(orbState = orbState, onOrbTap = onOrbTap, onOpenChat = onOpenChat) }
    }
}

@Composable
private fun CommandCentreHeader(greeting: String, state: NuaState) {
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.displaySmall,
            color = NuaTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(state)
            Text(
                text = statusLine(state),
                style = MaterialTheme.typography.bodyMedium,
                color = NuaTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatusDot(state: NuaState) {
    val color = when {
        state.isOffline -> NuaTheme.colors.textSecondary
        state.recentMistakes.isNotEmpty() -> NuaTheme.colors.warning
        else -> NuaTheme.colors.success
    }
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** e.g. "NUA ready · Busy · 2 events still to come today." */
private fun statusLine(state: NuaState): String {
    val readiness = if (state.isOffline) "NUA offline" else "NUA ready"
    return "$readiness · ${state.focus.label} · ${state.currentContext}"
}

/**
 * The hero card — the one place the brand gradient is allowed on a surface, per the
 * gradient's reserved uses.
 */
@Composable
private fun TodayCard(state: NuaState) {
    GlassCard(hero = true) {
        Text(
            text = "TODAY",
            style = MaterialTheme.typography.labelMedium,
            color = NuaTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.headline,
            style = MaterialTheme.typography.headlineSmall,
            color = NuaTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Metric(state.workload.eventsRemainingToday.toString(), "events left")
            Metric(state.workload.notificationsNeedingAttention.toString(), "need you")
            Metric(state.pendingTasks.size.toString(), "pending")
            // Confidence is genuinely absent until NUA has a track record — an em dash
            // says that, where "0" would read as "NUA is untrustworthy".
            Metric(state.confidence?.toString() ?: "—", "trust")
        }
        if (state.availability == UserAvailability.UNKNOWN) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "NUA can't read your calendar, so this may be incomplete.",
                style = MaterialTheme.typography.labelSmall,
                color = NuaTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = NuaTheme.colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = NuaTheme.colors.textSecondary)
    }
}

/** "NUA noticed something" — sourced from Dreams, never generated to fill the slot. */
@Composable
private fun IntelligenceCard(insight: String) {
    GlassCard {
        LabelRow(icon = Icons.Filled.Lightbulb, label = "NUA NOTICED SOMETHING", tint = NuaTheme.colors.brandIntelligence)
        Spacer(Modifier.height(10.dp))
        Text(insight, style = MaterialTheme.typography.bodyLarge, color = NuaTheme.colors.textPrimary)
    }
}

@Composable
private fun ActionCard(
    nextAction: WhatNowResult?,
    objective: String?,
    onAskWhatNow: () -> Unit,
    onDoNextAction: () -> Unit,
    onRemindNextActionLater: () -> Unit,
    onDismissNextAction: () -> Unit,
) {
    GlassCard {
        Text("NEXT BEST ACTION", style = MaterialTheme.typography.labelMedium, color = NuaTheme.colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        when (nextAction) {
            is WhatNowResult.Recommendation -> {
                Text(nextAction.action, style = MaterialTheme.typography.bodyLarge, color = NuaTheme.colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(nextAction.reason, style = MaterialTheme.typography.bodyMedium, color = NuaTheme.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                val estimate = nextAction.estimatedMinutes?.let { " · ~${it}m" } ?: ""
                Text(
                    "Confidence ${(nextAction.confidence * 100).toInt()}%$estimate",
                    style = MaterialTheme.typography.labelSmall,
                    color = NuaTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDoNextAction) { Text("Do it") }
                    TextButton(onClick = onRemindNextActionLater) { Text("Remind later") }
                    TextButton(onClick = onDismissNextAction) { Text("Not relevant") }
                }
            }

            WhatNowResult.NothingNeedsAttention -> {
                Text(
                    "Nothing unusual — you're clear.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuaTheme.colors.textPrimary,
                )
            }

            is WhatNowResult.Unavailable -> {
                Text(
                    "Couldn't work that out right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuaTheme.colors.textSecondary,
                )
            }

            null -> {
                Text(
                    "Ask NUA what's worth doing next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuaTheme.colors.textSecondary,
                )
            }
        }
        if (objective != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Working toward: $objective",
                style = MaterialTheme.typography.labelSmall,
                color = NuaTheme.colors.brandIdentity,
            )
        }
        if (nextAction !is WhatNowResult.Recommendation) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAskWhatNow) { Text("What should I do now?") }
        }
    }
}

@Composable
private fun PendingCard(tasks: List<PendingTask>) {
    GlassCard {
        Text("NEEDS YOU", style = MaterialTheme.typography.labelMedium, color = NuaTheme.colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        tasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NuaTheme.colors.brandIdentity),
                )
                Column {
                    Text(task.description, style = MaterialTheme.typography.bodyMedium, color = NuaTheme.colors.textPrimary)
                    Text(
                        pendingKindLabel(task.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = NuaTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

internal fun pendingKindLabel(kind: PendingKind): String = when (kind) {
    PendingKind.NOTIFICATION -> "Notification"
    PendingKind.DOCUMENT_EXPIRY -> "Document expiring"
    PendingKind.VISION_RECHECK -> "Photo recheck due"
    PendingKind.GOAL_OBSERVATION -> "About one of your goals"
}

/** NUA owning its failures on the home screen, not burying them in Settings. */
@Composable
private fun MistakesCard(mistakes: List<String>) {
    GlassCard {
        Text("WHAT NUA GOT WRONG", style = MaterialTheme.typography.labelMedium, color = NuaTheme.colors.warning)
        Spacer(Modifier.height(10.dp))
        mistakes.forEach {
            Text(
                "• $it",
                style = MaterialTheme.typography.bodySmall,
                color = NuaTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun OrbRow(orbState: OrbState, onOrbTap: () -> Unit, onOpenChat: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NuaOrb(state = orbState, modifier = Modifier.clickable(onClick = onOrbTap), size = 112.dp)
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onOpenChat) {
            Icon(Icons.Filled.Chat, contentDescription = "Open chat", tint = NuaTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun LabelRow(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = NuaTheme.colors.textSecondary)
    }
}

/**
 * Soft glass surface: elevated background, hairline border, large radius. [hero] adds the
 * brand gradient wash — one of the gradient's four permitted uses, so ordinary cards stay
 * flat and the hero actually stands out.
 */
@Composable
private fun GlassCard(hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuaCardCorner))
            .background(NuaTheme.colors.surfaceElevated)
            .then(if (hero) Modifier.background(NuaGradients.glassTint()) else Modifier)
            .border(NuaBorderWidth, NuaTheme.colors.border, RoundedCornerShape(NuaCardCorner))
            .padding(18.dp),
    ) {
        Column(content = content)
    }
}
