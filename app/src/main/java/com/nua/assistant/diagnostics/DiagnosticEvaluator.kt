package com.nua.assistant.diagnostics

/**
 * Raw signals gathered from real Android state (permissions, DAOs, repositories) —
 * kept separate from [evaluateDiagnostics] so the status/message logic below is
 * plain-JVM testable without touching Context, ContentResolver, or Room.
 */
data class DiagnosticInputs(
    val hasApiKey: Boolean,
    val memoryDbReachable: Boolean,
    val memoryMessageCount: Int,
    val micPermissionGranted: Boolean,
    val voiceOwnerEnrolled: Boolean,
    val locationPermissionGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val activeGeofenceCount: Int,
    val calendarReadGranted: Boolean,
    val calendarWriteGranted: Boolean,
    val emailConfigured: Boolean,
    val emailStatusDetail: String,
    val contactsPermissionGranted: Boolean,
    val smsPermissionGranted: Boolean,
    val accessibilityServiceEnabled: Boolean,
)

/**
 * Turns raw device/app state into a specific, honest status per area — never a
 * generic "something went wrong." Wear and Auto are always [DiagnosticStatus.INFO]:
 * neither the Wearable Data Layer API nor a car-session hook is wired up yet, so
 * there's no live connection signal to check — that gap is reported plainly instead
 * of being faked as OK or hidden.
 */
fun evaluateDiagnostics(inputs: DiagnosticInputs): List<DiagnosticCheck> = listOf(
    apiCheck(inputs),
    memoryCheck(inputs),
    voiceCheck(inputs),
    locationCheck(inputs),
    calendarCheck(inputs),
    emailCheck(inputs),
    automationCheck(inputs),
    DiagnosticCheck(
        category = DiagnosticCategory.WEAR,
        status = DiagnosticStatus.INFO,
        detail = "Wear OS companion module (tile service) is bundled, but the phone app doesn't " +
            "yet use the Wearable Data Layer API — live connection/sync status can't be checked from here.",
    ),
    DiagnosticCheck(
        category = DiagnosticCategory.AUTO,
        status = DiagnosticStatus.INFO,
        detail = "Android Auto entry point (NuaCarAppService) is declared but untested against a " +
            "real head unit or the Desktop Head Unit emulator. Connection state is only observable " +
            "from within an active car session, not from this screen.",
    ),
)

private fun apiCheck(inputs: DiagnosticInputs): DiagnosticCheck = if (inputs.hasApiKey) {
    DiagnosticCheck(DiagnosticCategory.API, DiagnosticStatus.OK, "Claude API key configured. Use \"Test Connection\" to verify it actually works.")
} else {
    DiagnosticCheck(DiagnosticCategory.API, DiagnosticStatus.NOT_CONFIGURED, "No Claude API key set — add one in Settings to enable NUA.")
}

private fun memoryCheck(inputs: DiagnosticInputs): DiagnosticCheck = if (inputs.memoryDbReachable) {
    DiagnosticCheck(DiagnosticCategory.MEMORY, DiagnosticStatus.OK, "Local memory database reachable (${inputs.memoryMessageCount} messages stored).")
} else {
    DiagnosticCheck(DiagnosticCategory.MEMORY, DiagnosticStatus.ERROR, "Local memory database is not reachable — NUA can't read or write conversation history.")
}

private fun voiceCheck(inputs: DiagnosticInputs): DiagnosticCheck = when {
    !inputs.micPermissionGranted -> DiagnosticCheck(
        DiagnosticCategory.VOICE, DiagnosticStatus.ERROR,
        "Microphone permission not granted — wake-word listening and voice input are disabled.",
    )
    inputs.voiceOwnerEnrolled -> DiagnosticCheck(
        DiagnosticCategory.VOICE, DiagnosticStatus.OK,
        "Microphone permission granted; owner voice profile enrolled (speaker verification active).",
    )
    else -> DiagnosticCheck(
        DiagnosticCategory.VOICE, DiagnosticStatus.WARNING,
        "Microphone permission granted, but no owner voice profile is enrolled — anyone's voice can trigger voice actions.",
    )
}

private fun locationCheck(inputs: DiagnosticInputs): DiagnosticCheck = when {
    !inputs.locationPermissionGranted && inputs.activeGeofenceCount > 0 -> DiagnosticCheck(
        DiagnosticCategory.LOCATION, DiagnosticStatus.ERROR,
        "${inputs.activeGeofenceCount} geofence(s) saved but location permission is missing — they will not fire.",
    )
    !inputs.locationPermissionGranted -> DiagnosticCheck(
        DiagnosticCategory.LOCATION, DiagnosticStatus.NOT_CONFIGURED,
        "Location permission not granted — geofenced suggestions are unavailable (optional feature).",
    )
    !inputs.backgroundLocationGranted -> DiagnosticCheck(
        DiagnosticCategory.LOCATION, DiagnosticStatus.WARNING,
        "Foreground location granted but background location is not — geofence transitions won't fire while NUA isn't in the foreground.",
    )
    else -> DiagnosticCheck(
        DiagnosticCategory.LOCATION, DiagnosticStatus.OK,
        "Location permission granted (${inputs.activeGeofenceCount} geofence(s) active).",
    )
}

private fun calendarCheck(inputs: DiagnosticInputs): DiagnosticCheck = when {
    inputs.calendarReadGranted && inputs.calendarWriteGranted -> DiagnosticCheck(
        DiagnosticCategory.CALENDAR, DiagnosticStatus.OK, "Calendar read/write permission granted.",
    )
    inputs.calendarReadGranted -> DiagnosticCheck(
        DiagnosticCategory.CALENDAR, DiagnosticStatus.WARNING,
        "Calendar read granted but write is not — NUA can see events but can't create reminders or invitations.",
    )
    else -> DiagnosticCheck(
        DiagnosticCategory.CALENDAR, DiagnosticStatus.NOT_CONFIGURED,
        "Calendar permission not granted — briefings, reminders, and invitations are unavailable.",
    )
}

private fun emailCheck(inputs: DiagnosticInputs): DiagnosticCheck = if (inputs.emailConfigured) {
    DiagnosticCheck(DiagnosticCategory.EMAIL, DiagnosticStatus.OK, inputs.emailStatusDetail)
} else {
    DiagnosticCheck(DiagnosticCategory.EMAIL, DiagnosticStatus.NOT_CONFIGURED, inputs.emailStatusDetail)
}

private fun automationCheck(inputs: DiagnosticInputs): DiagnosticCheck {
    val accessibilityNote = if (inputs.accessibilityServiceEnabled) {
        "Tier-2 accessibility automation is enabled."
    } else {
        "Tier-2 accessibility automation is disabled (off by default, opt-in)."
    }
    return if (inputs.contactsPermissionGranted) {
        val smsNote = if (inputs.smsPermissionGranted) "granted" else "not requested yet (asked lazily on first send)"
        DiagnosticCheck(
            DiagnosticCategory.AUTOMATION, DiagnosticStatus.OK,
            "Contacts permission granted (resolves names for SMS/calendar invites). SMS permission: $smsNote. $accessibilityNote",
        )
    } else {
        DiagnosticCheck(
            DiagnosticCategory.AUTOMATION, DiagnosticStatus.WARNING,
            "Contacts permission not granted — SMS and calendar-invite skills can't resolve contact names (raw numbers/emails still work). $accessibilityNote",
        )
    }
}
