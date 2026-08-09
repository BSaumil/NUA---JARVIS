package com.nua.assistant.voice

/**
 * Coarse tone read off SpeechRecognizer's RMS-dB stream for one utterance — not a
 * trained model, just energy/variance heuristics. Good enough to nudge
 * PersonalityEngine's tone, not to diagnose anything.
 */
enum class VoiceTone { NEUTRAL, LOW_ENERGY, HIGH_INTENSITY }

/**
 * Classifies a spoken utterance from its RMS-dB samples (SpeechRecognizer's
 * onRmsChanged callback, collected once per utterance) — pure and unit-testable
 * without touching SpeechRecognizer itself.
 */
object VoiceProsody {

    private const val MIN_SAMPLES = 4
    private const val LOW_ENERGY_MEAN_THRESHOLD = 2.5f
    private const val HIGH_INTENSITY_MEAN_THRESHOLD = 7.5f
    private const val HIGH_INTENSITY_VARIANCE_THRESHOLD = 6.0f

    fun classify(rmsSamples: List<Float>): VoiceTone {
        if (rmsSamples.size < MIN_SAMPLES) return VoiceTone.NEUTRAL

        val mean = rmsSamples.average().toFloat()
        val variance = rmsSamples.sumOf { sample -> ((sample - mean) * (sample - mean)).toDouble() }.toFloat() / rmsSamples.size

        return when {
            mean >= HIGH_INTENSITY_MEAN_THRESHOLD || variance >= HIGH_INTENSITY_VARIANCE_THRESHOLD -> VoiceTone.HIGH_INTENSITY
            mean <= LOW_ENERGY_MEAN_THRESHOLD -> VoiceTone.LOW_ENERGY
            else -> VoiceTone.NEUTRAL
        }
    }

    /** A directive line for PersonalityEngine's system prompt, or null for [VoiceTone.NEUTRAL] (no adjustment). */
    fun directiveFor(tone: VoiceTone): String? = when (tone) {
        VoiceTone.NEUTRAL -> null
        VoiceTone.LOW_ENERGY -> "The user's voice sounded low-energy or tired just now — keep this reply a bit gentler and lower-effort to engage with, without commenting on it directly."
        VoiceTone.HIGH_INTENSITY -> "The user's voice sounded loud or urgent just now — be more direct and get to the point quickly, without commenting on their tone directly."
    }
}
