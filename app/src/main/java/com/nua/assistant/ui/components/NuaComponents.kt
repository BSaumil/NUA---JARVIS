package com.nua.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nua.assistant.ui.nav.InfoLabel
import com.nua.assistant.ui.nav.NuaDestination
import com.nua.assistant.ui.theme.NuaBorderWidth
import com.nua.assistant.ui.theme.NuaCardCorner
import com.nua.assistant.ui.theme.NuaGradients
import com.nua.assistant.ui.theme.NuaTheme

/**
 * Soft glass surface: elevated background, hairline border, large radius. [hero] adds the
 * brand gradient wash — one of the gradient's four permitted uses, so ordinary cards stay
 * flat and a hero actually stands out.
 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
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

/**
 * One of the seven information-hierarchy labels (`#40`) as a heading. Using the shared
 * vocabulary everywhere is what makes the labels worth having — a screen that invents its
 * own section names teaches the user nothing transferable.
 */
@Composable
fun SectionLabel(label: InfoLabel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorFor(label)))
        Text(
            text = label.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = NuaTheme.colors.textSecondary,
        )
    }
}

/**
 * Colour per label. Only [InfoLabel.ALERT] and [InfoLabel.NOW] get urgent colours, and
 * only [InfoLabel.INSIGHT] gets violet — if every label were coloured distinctly the
 * palette would stop meaning anything.
 */
@Composable
private fun colorFor(label: InfoLabel): Color = when (label) {
    InfoLabel.NOW -> NuaTheme.colors.brandIdentity
    InfoLabel.ALERT -> NuaTheme.colors.critical
    InfoLabel.INSIGHT -> NuaTheme.colors.brandIntelligence
    InfoLabel.ACTION -> NuaTheme.colors.success
    InfoLabel.NEXT, InfoLabel.LATER, InfoLabel.MEMORY -> NuaTheme.colors.textSecondary
}

/** Pure: the icon for each destination. Separate from the composable so it's trivially checkable. */
fun iconFor(destination: NuaDestination): ImageVector = when (destination) {
    NuaDestination.HOME -> Icons.Filled.Home
    NuaDestination.ASK -> Icons.Filled.Chat
    NuaDestination.MEMORY -> Icons.Filled.Psychology
    NuaDestination.ACT -> Icons.Filled.Bolt
    NuaDestination.YOU -> Icons.Filled.Person
}

/**
 * The five-destination bar (`#38`). Orange marks the selected destination — identity
 * colour for "where you are" — while unselected items stay secondary text, so the bar
 * reads as a map rather than a row of equally-shouting buttons.
 */
@Composable
fun NuaBottomBar(
    current: NuaDestination,
    onSelect: (NuaDestination) -> Unit,
    alertOn: Set<NuaDestination> = emptySet(),
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = NuaTheme.colors.surface,
    ) {
        NuaDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = {
                    Box {
                        Icon(
                            imageVector = iconFor(destination),
                            contentDescription = destination.contentDescription,
                        )
                        if (destination in alertOn) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(NuaTheme.colors.brandIdentity),
                            )
                        }
                    }
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NuaTheme.colors.brandIdentity,
                    selectedTextColor = NuaTheme.colors.brandIdentity,
                    unselectedIconColor = NuaTheme.colors.textSecondary,
                    unselectedTextColor = NuaTheme.colors.textSecondary,
                    indicatorColor = NuaTheme.colors.surfaceElevated,
                ),
            )
        }
    }
}

/** A titled row of content used by the hub screens, so sections look the same everywhere. */
@Composable
fun LabelledSection(label: InfoLabel, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(label)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Icon shorthand used where a card wants a small leading glyph in the brand's accent. */
@Composable
fun InsightGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = NuaTheme.colors.brandIntelligence,
        modifier = modifier.size(16.dp),
    )
}
