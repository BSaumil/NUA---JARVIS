package com.nua.assistant.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import com.nua.assistant.automation.NuaAccessibilityService
import com.nua.assistant.email.EmailRepository
import com.nua.assistant.email.EmailResult
import com.nua.assistant.memory.GeofenceDao
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.voice.OwnerVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers real signals (permissions, DAOs, the actual injected EmailRepository) and
 * hands them to the pure [evaluateDiagnostics] to turn into a system-health view. No
 * signal here is fabricated: a category that genuinely can't be checked yet (Wear,
 * Auto) is reported as such rather than guessed at.
 */
@Singleton
class SelfDiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val geofenceDao: GeofenceDao,
    private val secureKeyRepository: SecureKeyRepository,
    private val ownerVerifier: OwnerVerifier,
    private val emailRepository: EmailRepository,
    private val modelMesh: ModelMesh,
) {

    suspend fun runChecks(): List<DiagnosticCheck> = evaluateDiagnostics(gatherInputs())

    /** A real, lightweight round trip to Claude — the only way to honestly confirm the key works, not just that one is set. */
    suspend fun testApiConnection(): DiagnosticCheck {
        if (!secureKeyRepository.hasApiKey()) {
            return DiagnosticCheck(DiagnosticCategory.API, DiagnosticStatus.NOT_CONFIGURED, "No Claude API key set — add one in Settings to enable NUA.")
        }
        val contract = TaskContract(task = InferenceTaskType.REASONING, privacySensitivity = PrivacySensitivity.LOW)
        return when (val result = modelMesh.complete(contract = contract, userPrompt = "Reply with the single word: ok", maxTokens = 8)) {
            is ClaudeResult.Success -> DiagnosticCheck(DiagnosticCategory.API, DiagnosticStatus.OK, "Connection verified — Claude responded successfully.")
            is ClaudeResult.Failure -> DiagnosticCheck(DiagnosticCategory.API, DiagnosticStatus.ERROR, "Connection failed: ${result.message}")
        }
    }

    private suspend fun gatherInputs(): DiagnosticInputs {
        val emailResult = runCatching { emailRepository.checkInbox() }.getOrNull()
        val (emailConfigured, emailDetail) = when (emailResult) {
            is EmailResult.Success -> true to emailResult.message
            is EmailResult.Failure -> false to emailResult.message
            is EmailResult.NotConfigured -> false to emailResult.reason
            null -> false to "Email status check failed unexpectedly."
        }
        val memoryCount = runCatching { memoryDao.countMessages() }.getOrNull()
        val backgroundLocationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        return DiagnosticInputs(
            hasApiKey = secureKeyRepository.hasApiKey(),
            memoryDbReachable = memoryCount != null,
            memoryMessageCount = memoryCount ?: 0,
            micPermissionGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            voiceOwnerEnrolled = ownerVerifier.isEnrolled(),
            locationPermissionGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            backgroundLocationGranted = backgroundLocationGranted,
            activeGeofenceCount = runCatching { geofenceDao.getAll().size }.getOrDefault(0),
            calendarReadGranted = hasPermission(Manifest.permission.READ_CALENDAR),
            calendarWriteGranted = hasPermission(Manifest.permission.WRITE_CALENDAR),
            emailConfigured = emailConfigured,
            emailStatusDetail = emailDetail,
            contactsPermissionGranted = hasPermission(Manifest.permission.READ_CONTACTS),
            smsPermissionGranted = hasPermission(Manifest.permission.SEND_SMS),
            accessibilityServiceEnabled = NuaAccessibilityService.isEnabled(context),
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
