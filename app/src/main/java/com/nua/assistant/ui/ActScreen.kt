package com.nua.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.automation.SkillDescriptor
import com.nua.assistant.automation.describePermission
import com.nua.assistant.memory.ActionOutcomeEntity
import com.nua.assistant.state.PendingTask
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.countsAsFailure
import com.nua.assistant.trust.pastTenseClause
import com.nua.assistant.ui.components.GlassCard
import com.nua.assistant.ui.components.LabelledSection
import com.nua.assistant.ui.nav.InfoLabel
import com.nua.assistant.ui.theme.NuaTheme

/**
 * "Act" — what NUA can do, what it's waiting on you for, and what it has already done.
 *
 * The capability list is generated from `SkillCatalog`, which reads the same closed Hilt
 * multibinding the router dispatches through. That means this screen can't drift from
 * reality: a skill that isn't registered can't appear here, and a registered one appears
 * automatically. It's the Agent Sandbox's declared manifests turned outward — the inputs,
 * permissions, and autonomy tier that constrain dispatch are exactly what you need to see
 * to understand what NUA may do on your behalf.
 */
@Composable
fun ActScreen(
    skills: List<SkillDescriptor>,
    pendingTasks: List<PendingTask>,
    recentActions: List<ActionOutcomeEntity>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (pendingTasks.isNotEmpty()) {
            item {
                LabelledSection(InfoLabel.NOW) {
                    GlassCard {
                        pendingTasks.forEach { task ->
                            Text(
                                text = "• ${task.description}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuaTheme.colors.textPrimary,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            LabelledSection(InfoLabel.ACTION) {
                Text(
                    text = "What NUA can do",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuaTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Generated from the skills actually registered in this build — not a " +
                        "brochure list. Each shows how much authority it has and what it needs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuaTheme.colors.textSecondary,
                )
            }
        }

        items(skills.size) { index -> SkillRow(skills[index]) }

        item {
            LabelledSection(InfoLabel.MEMORY) {
                Text(
                    text = "Recent actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuaTheme.colors.textPrimary,
                )
            }
        }

        if (recentActions.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuaTheme.colors.textSecondary,
                )
            }
        } else {
            items(recentActions.size) { index -> ActionOutcomeRow(recentActions[index]) }
        }
    }
}

@Composable
private fun SkillRow(skill: SkillDescriptor) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(skill.displayName, style = MaterialTheme.typography.titleSmall, color = NuaTheme.colors.textPrimary)
            Text(
                text = skill.tier.label,
                style = MaterialTheme.typography.labelSmall,
                color = tierColor(skill.tier),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = skill.tier.description,
            style = MaterialTheme.typography.bodySmall,
            color = NuaTheme.colors.textSecondary,
        )
        val requirements = buildList {
            if (skill.requiredInputs.isNotEmpty()) add("needs ${skill.requiredInputs.joinToString(", ")}")
            skill.manifest.requiredPermissions.forEach { add("requires ${describePermission(it)}") }
        }
        if (requirements.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = requirements.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = NuaTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * Higher-authority tiers get the identity colour so they stand out in the list — the ones
 * that act on the world are the ones worth noticing.
 */
@Composable
private fun tierColor(tier: AutonomyTier) = when (tier) {
    AutonomyTier.T0, AutonomyTier.T1 -> NuaTheme.colors.textSecondary
    AutonomyTier.T2 -> NuaTheme.colors.success
    AutonomyTier.T3, AutonomyTier.T4, AutonomyTier.T5 -> NuaTheme.colors.brandIdentity
}

@Composable
private fun ActionOutcomeRow(outcome: ActionOutcomeEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = outcome.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = NuaTheme.colors.textPrimary,
        )
        Text(
            text = buildString {
                append(outcome.tier.label)
                append(" · ")
                append(
                    if (outcome.wasRejection) "you declined" else outcome.outcomeState.pastTenseClause(),
                )
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (outcome.outcomeState.countsAsFailure() && !outcome.wasRejection) NuaTheme.colors.critical else NuaTheme.colors.textSecondary,
        )
    }
}
