package com.nua.assistant.automation.packages

import com.nua.assistant.ai.NuaActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pkg(
    id: String,
    actions: Set<String> = setOf(NuaActionType.GET_WEATHER.name),
    dependencies: Set<String> = emptySet(),
) = SovereignSkillPackageManifest(
    id = id,
    version = "1.0.0",
    name = id,
    description = "test package",
    author = "NUA",
    declaredActions = actions,
    recipeIds = listOf(1),
    dependencies = dependencies,
)

class SovereignSkillPackageTest {
    @Test fun `imported package cannot self activate without verified provenance`() {
        assertFalse(
            provenanceAllowsActivation(
                SkillPackageProvenance(
                    source = SkillPackageSource.IMPORTED,
                    sourceUri = "https://example.invalid/pkg",
                    sourceCommit = "abc",
                    signatureVerified = false,
                ),
            ),
        )
    }

    @Test fun `verified imported package needs immutable source provenance`() {
        assertFalse(
            provenanceAllowsActivation(
                SkillPackageProvenance(
                    source = SkillPackageSource.IMPORTED,
                    signatureVerified = true,
                ),
            ),
        )
        assertTrue(
            provenanceAllowsActivation(
                SkillPackageProvenance(
                    source = SkillPackageSource.IMPORTED,
                    sourceUri = "https://example.invalid/pkg",
                    sourceCommit = "012345",
                    signatureVerified = true,
                ),
            ),
        )
    }

    @Test fun `dependency cycle is rejected`() {
        val graph = mapOf(
            "a" to pkg("a", dependencies = setOf("b")),
            "b" to pkg("b", dependencies = setOf("a")),
        )
        assertFalse(validateSkillPackageGraph(graph).valid)
    }

    @Test fun `dependency cannot escalate parent capability envelope`() {
        val graph = mapOf(
            "parent" to pkg("parent", dependencies = setOf("child")),
            "child" to pkg("child", actions = setOf(NuaActionType.SMS_SEND.name)),
        )
        assertFalse(validateSkillPackageGraph(graph).valid)
    }

    @Test fun `parent that explicitly declares dependency capability is valid`() {
        val graph = mapOf(
            "parent" to pkg(
                "parent",
                actions = setOf(NuaActionType.GET_WEATHER.name, NuaActionType.SMS_SEND.name),
                dependencies = setOf("child"),
            ),
            "child" to pkg("child", actions = setOf(NuaActionType.SMS_SEND.name)),
        )
        assertTrue(validateSkillPackageGraph(graph).valid)
    }

    @Test fun `recipe action envelope rejects undeclared action`() {
        val manifest = pkg("weather")
        assertTrue(
            recipeActionEnvelopeSatisfied(
                manifest,
                mapOf(1L to setOf(NuaActionType.GET_WEATHER)),
            ),
        )
        assertFalse(
            recipeActionEnvelopeSatisfied(
                manifest,
                mapOf(1L to setOf(NuaActionType.GET_WEATHER, NuaActionType.SMS_SEND)),
            ),
        )
    }
}
