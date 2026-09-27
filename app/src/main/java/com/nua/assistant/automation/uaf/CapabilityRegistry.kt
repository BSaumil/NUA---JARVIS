package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.NuaSkill
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates every [CapabilityDescriptor] from the same closed Hilt-multibound skill map
 * [com.nua.assistant.automation.NuaIntentRouter] dispatches through and
 * [com.nua.assistant.automation.SkillCatalog] already reads for the Act screen — a third
 * reader of the same one registry, not a second registry. An action with no bound skill
 * simply has no descriptor; there is no way to register a capability the dispatch path
 * doesn't already know about.
 */
@Singleton
class CapabilityRegistry @Inject constructor(
    private val skills: Map<NuaActionType, @JvmSuppressWildcards NuaSkill>,
) {
    fun all(): List<CapabilityDescriptor> = skills.entries
        .map { (action, skill) -> capabilityDescriptorFor(action, skill.manifest) }
        .sortedBy { it.action.name }

    fun forAction(action: NuaActionType): CapabilityDescriptor? =
        skills[action]?.let { capabilityDescriptorFor(action, it.manifest) }
}
