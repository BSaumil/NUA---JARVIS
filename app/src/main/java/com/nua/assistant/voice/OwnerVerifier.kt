package com.nua.assistant.voice

import ai.picovoice.eagle.Eagle
import ai.picovoice.eagle.EagleProfile
import android.content.Context
import com.nua.assistant.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val OWNER_MATCH_THRESHOLD = 0.6f

/**
 * Runtime speaker verification against the enrolled owner profile (see
 * OwnerEnrollment). Nothing in the app calls this yet — Tier 2 has no concrete action
 * wired up to gate on it — but it's ready for the first Tier 2 action that wants "only
 * confirm this if it's actually the owner's voice, not just anyone nearby." Callers must
 * pass at least eagle.minProcessSamples worth of PCM (see getRequiredSampleCount) — the
 * SDK buffers audio internally and only scores once enough samples have accumulated.
 */
@Singleton
class OwnerVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileStore: OwnerVoiceProfileStore,
) {

    fun isEnrolled(): Boolean = profileStore.isEnrolled()

    /** Minimum PCM sample count [verify] needs to produce a score, or null if unconfigured. */
    fun getRequiredSampleCount(): Int? {
        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) return null
        var eagle: Eagle? = null
        return try {
            eagle = Eagle.Builder().setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY).build(context)
            eagle.minProcessSamples
        } catch (t: Exception) {
            null
        } finally {
            eagle?.delete()
        }
    }

    /** 0.0-1.0 similarity against the enrolled profile, or null if nothing is enrolled / verification failed. */
    fun verify(pcm: ShortArray): Float? {
        val profileBytes = profileStore.getProfileBytes() ?: return null
        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) return null

        var eagle: Eagle? = null
        var profile: EagleProfile? = null
        return try {
            eagle = Eagle.Builder().setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY).build(context)
            profile = EagleProfile(profileBytes)
            eagle.process(pcm, arrayOf(profile)).maxOrNull()
        } catch (t: Exception) {
            null
        } finally {
            profile?.delete()
            eagle?.delete()
        }
    }

    fun isLikelyOwner(pcm: ShortArray): Boolean = (verify(pcm) ?: 0f) >= OWNER_MATCH_THRESHOLD
}
