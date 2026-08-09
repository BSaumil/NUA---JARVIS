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

private const val CLIP_SECONDS = 3

sealed class EnrollmentStep {
    data class Progress(val percentage: Int) : EnrollmentStep()
    data object Complete : EnrollmentStep()
    data class Failed(val reason: String) : EnrollmentStep()
}

/**
 * Wraps Picovoice Eagle's enrollment flow — a separate product/entitlement from
 * Porcupine (wake word), possibly needing its own enablement in the Picovoice console
 * even though it reuses the same PICOVOICE_ACCESS_KEY build property. Eagle accumulates
 * enrollment progress inside a single EagleProfiler instance across many short audio
 * clips, so the profiler here is kept alive between enrollOneClip() calls instead of
 * being rebuilt each time (that would silently discard all prior progress). The caller
 * (see NuaViewModel) calls enrollOneClip() repeatedly until it reports Complete.
 */
@Singleton
class OwnerEnrollment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileStore: OwnerVoiceProfileStore,
) {
    private var profiler: EagleProfiler? = null

    suspend fun enrollOneClip(): EnrollmentStep = withContext(Dispatchers.IO) {
        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
            return@withContext EnrollmentStep.Failed("No Picovoice access key configured.")
        }
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return@withContext EnrollmentStep.Failed("Microphone permission not granted.")
        }

        var audioRecord: AudioRecord? = null
        try {
            val eagleProfiler = profiler ?: EagleProfiler.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                .build(context)
                .also { profiler = it }

            val frameLength = eagleProfiler.frameLength
            val sampleRate = eagleProfiler.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferSize, frameLength * 4),
            )
            audioRecord.startRecording()

            val frame = ShortArray(frameLength)
            var percentage = 0f
            val framesPerClip = (sampleRate * CLIP_SECONDS) / frameLength
            for (i in 0 until framesPerClip) {
                var offset = 0
                while (offset < frame.size) {
                    val read = audioRecord.read(frame, offset, frame.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                percentage = eagleProfiler.enroll(frame)
                if (percentage >= 100f) break
            }
            audioRecord.stop()

            if (percentage >= 100f) {
                val exported = eagleProfiler.export()
                profileStore.saveProfileBytes(exported.bytes)
                exported.delete()
                eagleProfiler.delete()
                profiler = null
                EnrollmentStep.Complete
            } else {
                EnrollmentStep.Progress(percentage.toInt())
            }
        } catch (t: Exception) {
            profiler?.delete()
            profiler = null
            EnrollmentStep.Failed(t.message ?: "Enrollment failed")
        } finally {
            audioRecord?.release()
        }
    }

    fun resetEnrollment() {
        profiler?.delete()
        profiler = null
        profileStore.clear()
    }
}
