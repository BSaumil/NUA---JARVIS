package com.nua.assistant.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun healthyInputs() = DiagnosticInputs(
    hasApiKey = true,
    memoryDbReachable = true,
    memoryMessageCount = 42,
    micPermissionGranted = true,
    voiceOwnerEnrolled = true,
    locationPermissionGranted = true,
    backgroundLocationGranted = true,
    activeGeofenceCount = 2,
    calendarReadGranted = true,
    calendarWriteGranted = true,
    emailConfigured = true,
    emailStatusDetail = "Inbox reachable.",
    contactsPermissionGranted = true,
    smsPermissionGranted = true,
    accessibilityServiceEnabled = false,
)

class DiagnosticEvaluatorTest {

    @Test
    fun `every category is present exactly once`() {
        val checks = evaluateDiagnostics(healthyInputs())
        assertEquals(DiagnosticCategory.entries.toSet(), checks.map { it.category }.toSet())
        assertEquals(DiagnosticCategory.entries.size, checks.size)
    }

    @Test
    fun `fully healthy inputs report OK for every checkable category`() {
        val checks = evaluateDiagnostics(healthyInputs())
        val checkable = checks.filter { it.category != DiagnosticCategory.WEAR && it.category != DiagnosticCategory.AUTO }
        checkable.forEach { assertEquals("${it.category} expected OK but was ${it.status}: ${it.detail}", DiagnosticStatus.OK, it.status) }
    }

    @Test
    fun `wear and auto are always INFO regardless of input`() {
        val checks = evaluateDiagnostics(healthyInputs())
        val wear = checks.first { it.category == DiagnosticCategory.WEAR }
        val auto = checks.first { it.category == DiagnosticCategory.AUTO }
        assertEquals(DiagnosticStatus.INFO, wear.status)
        assertEquals(DiagnosticStatus.INFO, auto.status)
    }

    @Test
    fun `missing api key is NOT_CONFIGURED`() {
        val checks = evaluateDiagnostics(healthyInputs().copy(hasApiKey = false))
        val api = checks.first { it.category == DiagnosticCategory.API }
        assertEquals(DiagnosticStatus.NOT_CONFIGURED, api.status)
    }

    @Test
    fun `unreachable memory db is an error`() {
        val checks = evaluateDiagnostics(healthyInputs().copy(memoryDbReachable = false))
        val memory = checks.first { it.category == DiagnosticCategory.MEMORY }
        assertEquals(DiagnosticStatus.ERROR, memory.status)
    }

    @Test
    fun `no mic permission is an error, mic without enrollment is a warning`() {
        val noMic = evaluateDiagnostics(healthyInputs().copy(micPermissionGranted = false))
        assertEquals(DiagnosticStatus.ERROR, noMic.first { it.category == DiagnosticCategory.VOICE }.status)

        val notEnrolled = evaluateDiagnostics(healthyInputs().copy(voiceOwnerEnrolled = false))
        assertEquals(DiagnosticStatus.WARNING, notEnrolled.first { it.category == DiagnosticCategory.VOICE }.status)
    }

    @Test
    fun `geofences saved without location permission is an error, not just not-configured`() {
        val checks = evaluateDiagnostics(
            healthyInputs().copy(locationPermissionGranted = false, activeGeofenceCount = 3),
        )
        val location = checks.first { it.category == DiagnosticCategory.LOCATION }
        assertEquals(DiagnosticStatus.ERROR, location.status)
        assertTrue(location.detail.contains("3"))
    }

    @Test
    fun `no location permission and no geofences is merely not configured`() {
        val checks = evaluateDiagnostics(
            healthyInputs().copy(locationPermissionGranted = false, activeGeofenceCount = 0),
        )
        val location = checks.first { it.category == DiagnosticCategory.LOCATION }
        assertEquals(DiagnosticStatus.NOT_CONFIGURED, location.status)
    }

    @Test
    fun `foreground location without background location is a warning`() {
        val checks = evaluateDiagnostics(healthyInputs().copy(backgroundLocationGranted = false))
        val location = checks.first { it.category == DiagnosticCategory.LOCATION }
        assertEquals(DiagnosticStatus.WARNING, location.status)
    }

    @Test
    fun `calendar read without write is a warning, no permission is not configured`() {
        val readOnly = evaluateDiagnostics(healthyInputs().copy(calendarWriteGranted = false))
        assertEquals(DiagnosticStatus.WARNING, readOnly.first { it.category == DiagnosticCategory.CALENDAR }.status)

        val none = evaluateDiagnostics(healthyInputs().copy(calendarReadGranted = false, calendarWriteGranted = false))
        assertEquals(DiagnosticStatus.NOT_CONFIGURED, none.first { it.category == DiagnosticCategory.CALENDAR }.status)
    }

    @Test
    fun `email status mirrors the real repository result rather than being fabricated`() {
        val checks = evaluateDiagnostics(
            healthyInputs().copy(emailConfigured = false, emailStatusDetail = "Email isn't connected yet."),
        )
        val email = checks.first { it.category == DiagnosticCategory.EMAIL }
        assertEquals(DiagnosticStatus.NOT_CONFIGURED, email.status)
        assertEquals("Email isn't connected yet.", email.detail)
    }

    @Test
    fun `no contacts permission degrades automation to a warning`() {
        val checks = evaluateDiagnostics(healthyInputs().copy(contactsPermissionGranted = false))
        assertEquals(DiagnosticStatus.WARNING, checks.first { it.category == DiagnosticCategory.AUTOMATION }.status)
    }
}
