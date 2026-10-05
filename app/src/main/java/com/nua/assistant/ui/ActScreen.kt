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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.automation.SkillDescriptor
import com.nua.assistant.automation.describePermission
import com.nua.assistant.memory.ActionOutcomeEntity
import com.nua.assistant.memory.RecipeEntity
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
 *
 * Recipes section: create/review/run-now/edit/delete for NUA Recipes (Feature 6).
 * Deliberately not here yet: enable/disable toggles and scheduled/automatic triggering --
 * both need a RecipeEntity column this codebase doesn't have (see
 * docs/DATABASE_MIGRATION_POLICY.md's version-21 rule for why that's not a change to make
 * casually), so every recipe here only ever runs when you tap "Run now," the same as a
 * recipe run from any other caller.
 */
@Composable
fun ActScreen(
    skills: List<SkillDescriptor>,
    pendingTasks: List<PendingTask>,
    recentActions: List<ActionOutcomeEntity>,
    recipes: List<RecipeEntity>,
    recipeStatus: String?,
    onCreateRecipe: (name: String, description: String) -> Unit,
    onEditRecipe: (id: Long, name: String, description: String) -> Unit,
    onDeleteRecipe: (id: Long) -> Unit,
    onRunRecipeNow: (id: Long, name: String) -> Unit,
    onDismissRecipeStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var recipeBeingEdited by remember { mutableStateOf<RecipeEntity?>(null) }
    var recipePendingDelete by remember { mutableStateOf<RecipeEntity?>(null) }

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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Recipes",
                        style = MaterialTheme.typography.titleMedium,
                        color = NuaTheme.colors.textPrimary,
                    )
                    TextButton(onClick = { showCreateDialog = true }) { Text("New recipe") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Describe a few things to do, one after another or independently " +
                        "(\"check the weather, then open spotify\") — NUA compiles it into real " +
                        "steps and shows you exactly what it understood, and what it didn't.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuaTheme.colors.textSecondary,
                )
            }
        }

        if (recipeStatus != null) {
            item {
                GlassCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = recipeStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = NuaTheme.colors.textPrimary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        TextButton(onClick = onDismissRecipeStatus) { Text("Dismiss") }
                    }
                }
            }
        }

        if (recipes.isEmpty()) {
            item {
                Text(
                    "No recipes yet — tap \"New recipe\" to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuaTheme.colors.textSecondary,
                )
            }
        } else {
            items(recipes.size) { index ->
                RecipeRow(
                    recipe = recipes[index],
                    onRunNow = { onRunRecipeNow(recipes[index].id, recipes[index].name) },
                    onEdit = { recipeBeingEdited = recipes[index] },
                    onDelete = { recipePendingDelete = recipes[index] },
                )
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

    if (showCreateDialog) {
        RecipeEditorDialog(
            title = "New recipe",
            initialName = "",
            initialDescription = "",
            onConfirm = { name, description ->
                onCreateRecipe(name, description)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    recipeBeingEdited?.let { recipe ->
        RecipeEditorDialog(
            title = "Edit recipe",
            initialName = recipe.name,
            initialDescription = recipe.description,
            onConfirm = { name, description ->
                onEditRecipe(recipe.id, name, description)
                recipeBeingEdited = null
            },
            onDismiss = { recipeBeingEdited = null },
        )
    }

    recipePendingDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { recipePendingDelete = null },
            title = { Text("Delete \"${recipe.name}\"?") },
            text = { Text("This removes the recipe and its run history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteRecipe(recipe.id); recipePendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { recipePendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecipeEditorDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("e.g. \"check the weather, then open spotify\"") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && description.isNotBlank()) onConfirm(name, description) },
                enabled = name.isNotBlank() && description.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecipeRow(recipe: RecipeEntity, onRunNow: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall, color = NuaTheme.colors.textPrimary)
            if (recipe.unresolvedClauseCount > 0) {
                Text(
                    text = "${recipe.unresolvedClauseCount} not understood",
                    style = MaterialTheme.typography.labelSmall,
                    color = NuaTheme.colors.warning,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(recipe.description, style = MaterialTheme.typography.bodySmall, color = NuaTheme.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRunNow) { Text("Run now") }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
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
