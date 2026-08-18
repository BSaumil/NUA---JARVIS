package com.nua.assistant.automation

import android.Manifest
import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.contacts.ContactResolver
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarInviteSkill @Inject constructor(
    private val calendarReader: CalendarReader,
    private val contactResolver: ContactResolver,
) : NuaSkill {

    override val manifest = SkillManifest(
        parameters = listOf(
            SkillParameter("title", required = true),
            SkillParameter("offsetHours", required = true),
            SkillParameter("attendee"),
        ),
        requiredPermissions = listOf(Manifest.permission.WRITE_CALENDAR),
    )

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val title = intent.parameters["title"]
        val offsetHours = intent.parameters["offsetHours"]?.toDoubleOrNull()
        if (title.isNullOrBlank() || offsetHours == null) return NuaRouteResult.FallThroughToChat

        val attendee = intent.parameters["attendee"]
        val attendeeEmail = attendee?.let { contactResolver.emailFor(it) }

        val whenMillis = System.currentTimeMillis() + (offsetHours * 3_600_000L).toLong()
        val created = calendarReader.createInvitation(title, whenMillis, attendeeEmail)

        val message = when {
            created.isFailure -> "Couldn't create that event — ${created.exceptionOrNull()?.message ?: "calendar write failed"}."
            attendee != null && attendeeEmail == null ->
                "Added \"$title\" to your calendar, but couldn't find an email for \"$attendee\" to invite them — it's on there as a personal event only."
            attendeeEmail != null -> "Added \"$title\" to your calendar and invited $attendeeEmail."
            else -> "Added \"$title\" to your calendar."
        }
        return NuaRouteResult.ActionTaken(message, succeeded = created.isSuccess)
    }
}
