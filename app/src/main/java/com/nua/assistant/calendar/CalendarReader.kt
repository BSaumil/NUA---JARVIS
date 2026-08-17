package com.nua.assistant.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String?,
)

/**
 * On-device calendar access via CalendarContract — the official Android API, no OAuth,
 * no Google Calendar API round trip. Covers both reading upcoming events (used by
 * MorningBriefing and TaskPlanner) and writing a reminder once the user has explicitly
 * confirmed a plan; both are Tier 1 since CalendarContract.Events is a first-party
 * content provider, not app-UI automation.
 */
@Singleton
class CalendarReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private fun hasCalendarPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun eventsBetween(startMillis: Long, endMillis: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission(Manifest.permission.READ_CALENDAR)) return@withContext emptyList()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Events.DTSTART} ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val locationCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

            while (cursor.moveToNext()) {
                events += CalendarEvent(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "(untitled)",
                    startTimeMillis = cursor.getLong(startCol),
                    endTimeMillis = cursor.getLong(endCol),
                    location = cursor.getString(locationCol),
                )
            }
        }
        events
    }

    suspend fun eventsForTomorrow(): List<CalendarEvent> {
        val startOfTomorrow = startOfDayMillis(daysFromNow = 1)
        val startOfDayAfter = startOfDayMillis(daysFromNow = 2)
        return eventsBetween(startOfTomorrow, startOfDayAfter)
    }

    /**
     * Creates a calendar reminder after the user has confirmed a NUA-proposed plan.
     * Never called automatically — see TaskPlanner, which only invokes this from an
     * explicit user confirmation action, never while assembling the plan itself.
     */
    suspend fun createReminder(title: String, whenMillis: Long, notes: String? = null): Result<Long> =
        insertEvent(title, whenMillis, notes)

    /**
     * Creates a calendar event and, if an attendee email was resolved, invites them via
     * CalendarContract.Attendees — the same official on-device API as the event itself,
     * no Google Calendar API round trip. Unlike [createReminder], called directly once
     * NUA classifies a CALENDAR_INVITE intent (Tier 2: executes immediately, then
     * reports what happened) — creating an event a user explicitly asked for is squarely
     * "do it and say so" territory, the same reasoning as smart-home actions.
     */
    suspend fun createInvitation(title: String, whenMillis: Long, attendeeEmail: String?, notes: String? = null): Result<Long> {
        val created = insertEvent(title, whenMillis, notes)
        val eventId = created.getOrNull() ?: return created
        if (attendeeEmail != null) {
            withContext(Dispatchers.IO) {
                val attendeeValues = ContentValues().apply {
                    put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendeeEmail)
                    put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.RELATIONSHIP_ATTENDEE)
                    put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_REQUIRED)
                    put(CalendarContract.Attendees.ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
                    put(CalendarContract.Attendees.EVENT_ID, eventId)
                }
                context.contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, attendeeValues)
            }
        }
        return created
    }

    private suspend fun insertEvent(title: String, whenMillis: Long, notes: String?): Result<Long> =
        withContext(Dispatchers.IO) {
            if (!hasCalendarPermission(Manifest.permission.WRITE_CALENDAR)) {
                return@withContext Result.failure(IllegalStateException("Calendar write permission not granted."))
            }
            val calendarId = defaultWritableCalendarId()
                ?: return@withContext Result.failure(IllegalStateException("No writable calendar found on this device."))

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, notes)
                put(CalendarContract.Events.DTSTART, whenMillis)
                put(CalendarContract.Events.DTEND, whenMillis + DEFAULT_REMINDER_DURATION_MS)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Calendar insert failed."))

            Result.success(ContentUris.parseId(uri))
        }

    private fun defaultWritableCalendarId(): Long? {
        if (!hasCalendarPermission(Manifest.permission.READ_CALENDAR)) return null

        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            var fallbackId: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                if (fallbackId == null) fallbackId = id
                if (primaryCol >= 0 && cursor.getInt(primaryCol) == 1) return id
            }
            return fallbackId
        }
        return null
    }

    private fun startOfDayMillis(daysFromNow: Int): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysFromNow)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private companion object {
        const val DEFAULT_REMINDER_DURATION_MS = 60L * 60L * 1000L
    }
}
