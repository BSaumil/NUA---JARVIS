package com.nua.assistant.ui.orb

/**
 * The eight states the NUA Orb can be in. The Orb is NUA's visual identity, so each state
 * has to be distinguishable at a glance — not a generic glowing circle that changes hue.
 */
enum class OrbState {
    /** Slow breathing. Nothing is happening; NUA is present but quiet. */
    IDLE,

    /** Expands subtly. NUA is hearing the user. */
    LISTENING,

    /** Internal particles move. NUA is working on something. */
    THINKING,

    /** Directional energy movement. NUA is carrying out an action in the world. */
    ACTING,

    /** Orange pulse. Something needs the user's attention. */
    WARNING,

    /** Short confirmation animation. Something just completed. */
    SUCCESS,

    /** Controlled red pulse — controlled, not frantic. Something failed. */
    ERROR,

    /** Muted, static. No connection, or NUA can't act. */
    OFFLINE,
}

/**
 * What the Orb announces to TalkBack. The Orb carries real information — it's the main
 * signal for what NUA is doing — so it must not be decorative to a screen reader. Pure,
 * so the wording is unit-testable.
 */
fun orbContentDescription(state: OrbState): String = when (state) {
    OrbState.IDLE -> "NUA is ready"
    OrbState.LISTENING -> "NUA is listening"
    OrbState.THINKING -> "NUA is thinking"
    OrbState.ACTING -> "NUA is doing something"
    OrbState.WARNING -> "NUA needs your attention"
    OrbState.SUCCESS -> "NUA finished successfully"
    OrbState.ERROR -> "Something went wrong"
    OrbState.OFFLINE -> "NUA is offline"
}

/**
 * How a given [OrbState] should be drawn. Kept as plain data with no Compose or Android
 * types so the state→appearance mapping is unit-testable on the JVM, the same way
 * `NuaPalette` keeps the colour tokens testable.
 *
 * [breathPeriodMillis] of null means the state doesn't breathe at all — used by [OrbState.OFFLINE],
 * which must read as genuinely inert rather than slowly alive.
 */
data class OrbAppearance(
    /** Multiplier on the Orb's base radius at rest. */
    val restScale: Float,
    /** Multiplier at the peak of the breath/pulse. Equal to [restScale] means no size change. */
    val peakScale: Float,
    /** Full cycle duration, or null for a state that doesn't animate its size. */
    val breathPeriodMillis: Int?,
    /** Whether the orbiting "thinking" particles are drawn. */
    val showsParticles: Boolean,
    /** Whether energy sweeps around the Orb, reading as directed activity. */
    val showsSweep: Boolean,
    /** 0f–1f. Below 1f the Orb is visibly dimmed — [OrbState.OFFLINE] is the muted state. */
    val intensity: Float,
    /** Which colour treatment the Orb takes; see `NuaOrb` for how each maps to a brush. */
    val palette: OrbPalette,
)

/**
 * Which colours an Orb state draws with. Most states use the brand gradient; the alert
 * states drop to a single flat status colour, because a warning that still looks like the
 * brand gradient doesn't read as a warning.
 */
enum class OrbPalette {
    /** Orange→violet, NUA's identity. */
    BRAND,

    /** Reaches into Future Pink — the exceptional/active state. */
    EXCEPTIONAL,

    /** Flat warning colour. */
    WARNING,

    /** Flat success colour. */
    SUCCESS,

    /** Flat critical colour. */
    CRITICAL,

    /** Desaturated brand, for offline. */
    MUTED,
}

/**
 * Pure: the drawing parameters for a state. The timings sit on the animation scale used
 * across Phase 12 — 150ms for immediate feedback, 250ms for transitions, 400–600ms for
 * expressive movement — with the slow ambient states deliberately far longer, since a
 * "breathing" orb that cycles every 600ms reads as panicking rather than resting.
 */
fun appearanceFor(state: OrbState): OrbAppearance = when (state) {
    OrbState.IDLE -> OrbAppearance(
        restScale = 1f,
        peakScale = 1.04f,
        breathPeriodMillis = 4000,
        showsParticles = false,
        showsSweep = false,
        intensity = 1f,
        palette = OrbPalette.BRAND,
    )

    OrbState.LISTENING -> OrbAppearance(
        restScale = 1.08f,
        peakScale = 1.16f,
        breathPeriodMillis = 1200,
        showsParticles = false,
        showsSweep = false,
        intensity = 1f,
        palette = OrbPalette.EXCEPTIONAL,
    )

    OrbState.THINKING -> OrbAppearance(
        restScale = 1f,
        peakScale = 1.03f,
        breathPeriodMillis = 1800,
        showsParticles = true,
        showsSweep = false,
        intensity = 1f,
        palette = OrbPalette.BRAND,
    )

    OrbState.ACTING -> OrbAppearance(
        restScale = 1.05f,
        peakScale = 1.05f,
        breathPeriodMillis = null,
        showsParticles = false,
        showsSweep = true,
        intensity = 1f,
        palette = OrbPalette.BRAND,
    )

    OrbState.WARNING -> OrbAppearance(
        restScale = 1f,
        peakScale = 1.12f,
        breathPeriodMillis = 900,
        showsParticles = false,
        showsSweep = false,
        intensity = 1f,
        palette = OrbPalette.WARNING,
    )

    OrbState.SUCCESS -> OrbAppearance(
        restScale = 1f,
        peakScale = 1.10f,
        breathPeriodMillis = 600,
        showsParticles = false,
        showsSweep = false,
        intensity = 1f,
        palette = OrbPalette.SUCCESS,
    )

    OrbState.ERROR -> OrbAppearance(
        // Deliberately a slower, smaller pulse than WARNING: an error should read as
        // controlled, not alarmed. A frantic red orb makes failures feel worse than they are.
        restScale = 1f,
        peakScale = 1.06f,
        breathPeriodMillis = 1400,
        showsParticles = false,
        showsSweep = false,
        intensity = 1f,
        palette = OrbPalette.CRITICAL,
    )

    OrbState.OFFLINE -> OrbAppearance(
        restScale = 0.94f,
        peakScale = 0.94f,
        breathPeriodMillis = null,
        showsParticles = false,
        showsSweep = false,
        intensity = 0.45f,
        palette = OrbPalette.MUTED,
    )
}
