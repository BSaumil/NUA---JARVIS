package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.contacts.ContactResolver
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSendSkill @Inject constructor(
    private val contactResolver: ContactResolver,
) : NuaSkill {

    override val manifest = SkillManifest(
        parameters = listOf(SkillParameter("contact", required = true), SkillParameter("message", required = true)),
    )

    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val contact = intent.parameters["contact"]
        val message = intent.parameters["message"]
        if (contact.isNullOrBlank() || message.isNullOrBlank()) return NuaRouteResult.FallThroughToChat

        val phoneNumber = contactResolver.phoneNumberFor(contact)
            ?: return NuaRouteResult.ActionTaken("Couldn't find a number for \"$contact\".", succeeded = false)

        return NuaRouteResult.SmsProposed(contactName = contact, phoneNumber = phoneNumber, message = message)
    }
}
