package com.nua.assistant.ui.orb

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nua.assistant.ui.theme.FuturePink
import com.nua.assistant.ui.theme.NeuralViolet
import com.nua.assistant.ui.theme.NuaCritical
import com.nua.assistant.ui.theme.NuaOrange
import com.nua.assistant.ui.theme.NuaSuccess
import com.nua.assistant.ui.theme.rememberMotionEnabled
import com.nua.assistant.ui.theme.NuaWarning
import kotlin.math.cos
import kotlin.math.sin

private const val PARTICLE_COUNT = 6
private const val PARTICLE_ORBIT_PERIOD_MILLIS = 2600
private const val SWEEP_PERIOD_MILLIS = 1500
private const val GLOW_RADIUS_MULTIPLIER = 1.55f

/**
 * NUA's Orb — the visual identity, not a generic glowing circle. Its eight states are
 * described in [OrbState]; the drawing parameters for each live in the pure
 * [appearanceFor] so they're unit-testable without a device.
 *
 * Motion respects the system "remove animations" setting: when the animator duration
 * scale is 0, every state renders at its resting size with no breathing, orbit, or sweep.
 * Reduced motion is a Phase 12 accessibility requirement, and an orb is exactly the kind
 * of persistent ambient movement that makes an app unusable for people who need it off.
 */
@Composable
fun NuaOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentDescription: String = orbContentDescription(state),
) {
    val appearance = remember(state) { appearanceFor(state) }
    val animationsEnabled = rememberMotionEnabled()
    // Held in a local so the semantics block below doesn't shadow it — inside
    // `semantics { }`, `contentDescription` resolves to the write-only semantics property.
    val orbDescription = contentDescription

    val transition = rememberInfiniteTransition(label = "orb")

    val breath = if (animationsEnabled && appearance.breathPeriodMillis != null) {
        transition.animateFloat(
            initialValue = appearance.restScale,
            targetValue = appearance.peakScale,
            animationSpec = infiniteRepeatable(
                animation = tween(appearance.breathPeriodMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orb-breath",
        ).value
    } else {
        appearance.restScale
    }

    val orbit = if (animationsEnabled && appearance.showsParticles) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(PARTICLE_ORBIT_PERIOD_MILLIS, easing = LinearEasing)),
            label = "orb-orbit",
        ).value
    } else {
        0f
    }

    val sweep = if (animationsEnabled && appearance.showsSweep) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(SWEEP_PERIOD_MILLIS, easing = LinearEasing)),
            label = "orb-sweep",
        ).value
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = orbDescription },
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = minOf(this.size.width, this.size.height) / 2f
        val radius = baseRadius * breath * 0.72f

        drawGlow(center, radius, appearance)
        drawBody(center, radius, appearance, sweep)
        if (appearance.showsParticles) drawParticles(center, radius, orbit, appearance.intensity)
    }
}

/** The soft halo. Radial, always centre-out, so the Orb reads as lit from within. */
private fun DrawScope.drawGlow(center: Offset, radius: Float, appearance: OrbAppearance) {
    val glow = paletteColors(appearance.palette).first().copy(alpha = 0.22f * appearance.intensity)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow, Color.Transparent),
            center = center,
            radius = radius * GLOW_RADIUS_MULTIPLIER,
        ),
        radius = radius * GLOW_RADIUS_MULTIPLIER,
        center = center,
    )
}

private fun DrawScope.drawBody(center: Offset, radius: Float, appearance: OrbAppearance, sweepDegrees: Float) {
    val colors = paletteColors(appearance.palette).map { it.copy(alpha = it.alpha * appearance.intensity) }

    if (appearance.showsSweep) {
        // Directional energy: the gradient itself rotates, so movement reads as NUA
        // pushing outward into the world rather than idling.
        rotate(degrees = sweepDegrees, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(colors = colors + colors.first(), center = center),
                radius = radius,
                center = center,
            )
        }
        return
    }

    drawCircle(
        brush = Brush.radialGradient(colors = colors, center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

/** Internal particles for THINKING — movement inside the Orb, not around its edge. */
private fun DrawScope.drawParticles(center: Offset, radius: Float, orbitDegrees: Float, intensity: Float) {
    val orbitRadius = radius * 0.55f
    val particleRadius = radius * 0.07f
    repeat(PARTICLE_COUNT) { index ->
        val angle = Math.toRadians((orbitDegrees + index * (360f / PARTICLE_COUNT)).toDouble())
        val position = Offset(
            x = center.x + (orbitRadius * cos(angle)).toFloat(),
            y = center.y + (orbitRadius * sin(angle)).toFloat(),
        )
        drawCircle(
            color = NuaOrange.copy(alpha = 0.75f * intensity),
            radius = particleRadius,
            center = position,
        )
    }
}

private fun paletteColors(palette: OrbPalette): List<Color> = when (palette) {
    OrbPalette.BRAND -> listOf(NuaOrange, NeuralViolet)
    OrbPalette.EXCEPTIONAL -> listOf(NuaOrange, NeuralViolet, FuturePink)
    OrbPalette.WARNING -> listOf(NuaWarning, NuaWarning.copy(alpha = 0.55f))
    OrbPalette.SUCCESS -> listOf(NuaSuccess, NuaSuccess.copy(alpha = 0.55f))
    OrbPalette.CRITICAL -> listOf(NuaCritical, NuaCritical.copy(alpha = 0.55f))
    OrbPalette.MUTED -> listOf(NuaOrange.copy(alpha = 0.5f), NeuralViolet.copy(alpha = 0.5f))
}
