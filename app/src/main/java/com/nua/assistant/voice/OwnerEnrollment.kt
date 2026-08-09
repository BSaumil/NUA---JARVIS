package com.nua.assistant.voice

import ai.picovoice.eagle.EagleProfiler
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.nua.assistant.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class EnrollmentStep {
    data class Progress(val percentage: Int, val feedback: String) : EnrollmentStep()
    data object Complete : EnrollmentStep()
    data class Failed(val reason: String) : EnrollmentStep()
}

/**
 * Wraps Picovoice Eagle's enrollment flow — a separate product/entitlement from
 * Porcupine (wake word), possibly needing its own enablement in the Picovoice console
 * even though it reuses the same PICOVOICE_ACCESS_KEY build property. Records one short
 * mic clip per call and feeds it to EagleProfiler; the caller (see NuaViewModel) calls
 * this repeatedly until it reports Complete, matching how Eagle enrollment actually
 * works (it needs several clips from different moments, not one long recording).
 */
@Singleton
class OwnerEnrollment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileStore: OwnerVoiceProfileStore,
) {

    suspend fun enrollOneClip(): EnrollmentStep = withContext(Dispatchers.IO) {
        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
            return@withContext EnrollmentStep.Failed("No Picovoice access key configured.")
        }
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return@withContext EnrollmentStep.Failed("Microphone permission not granted.")
        }

        var profiler: EagleProfiler? = null
        var audioRecord: AudioRecord? = null
        try {
            profiler = EagleProfiler.Builder().setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY).build(context)

            val minBufferSize = AudioRecord.getMinBufferSize(
                profiler.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                profiler.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferSize, profiler.minEnrollSamples * 2),
            )
            audioRecord.startRecording()

            val pcm = ShortArray(profiler.minEnrollSamples)
            var offset = 0
            while (offset < pcm.size) {
                val read = audioRecord.read(pcm, offset, pcm.size - offset)
                if (read <= 0) break
                offset += read
            }
            audioRecord.stop()

            val result = profiler.enroll(pcm)
            val percentage = result.percentage.toInt()

            if (percentage >= 100) {
                val profile = profiler.export()
                profileStore.saveProfileBytes(profile.bytes)
                EnrollmentStep.Complete
            } else {
                EnrollmentStep.Progress(percentage, result.feedback.name)
            }
        } catch (t: Exception) {
            EnrollmentStep.Failed(t.message ?: "Enrollment failed")
        } finally {
            audioRecord?.release()
            profiler?.delete()
        }
    }

    fun resetEnrollment() {
        profileStore.clear()
    }
}
