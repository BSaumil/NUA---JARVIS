package com.nua.assistant.automation

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.voice.NuaLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "SkillSandbox"

/**
 * The single enforcement point every skill execution passes through — the Agent Sandbox
 * (`#45`). Before a skill runs it checks the skill's own [SkillManifest]: required
 * parameters present, required permissions granted, and inputs narrowed to exactly what
 * the skill declared. While it runs, execution is bounded by the declared timeout and
 * contained against exceptions, so one misbehaving skill degrades to a reported failure
 * instead of hanging the turn or crashing the app. Afterwards `NuaIntentRouter` writes the
 * outcome to the existing audit trail (`TrustRepository`), so every path — success,
 * validation refusal, timeout, crash — lands in the same log.
 *
 * Honest scope: this is a policy and lifecycle envelope, not OS-level isolation. Skills
 * still run in NUA's own process holding NUA's own permissions; nothing here would stop a
 * skill that deliberately reached around its manifest. What it does guarantee is that the
 * dispatch path can't invoke an undeclared tool, can't pass a skill inputs it never
 * declared, can't run one unbounded, and can't lose the result from the audit trail.
 */
@Singleton
class SkillSandbox @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun execute(
        skill: NuaSkill,
        intent: ClassifiedIntent,
        originalUtterance: String,
        pinnedLanguage: NuaLanguage?,
    ): NuaRouteResult {
        val manifest = skill.manifest

        // A required parameter missing means the classifier misread the request, not that
        // the user asked for something impossible — fall through to chat, same as before.
        val missing = missingRequiredParameters(manifest, intent.parameters)
        if (missing.isNotEmpty()) {
            Log.i(TAG, "Refused ${intent.action}: missing required parameter(s) ${missing.joinToString()}")
            return NuaRouteResult.FallThroughToChat
        }

        val ungranted = manifest.requiredPermissions.filterNot(::hasPermission)
        if (ungranted.isNotEmpty()) {
            val needed = ungranted.joinToString(" and ", transform = ::describePermission)
            return NuaRouteResult.ActionTaken("That needs $needed, which isn't granted yet.", succeeded = false)
        }

        val sandboxed = intent.copy(parameters = filterToDeclaredParameters(manifest, intent.parameters))

        return try {
            withTimeout(manifest.timeoutMillis) {
                skill.execute(sandboxed, originalUtterance, pinnedLanguage)
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.w(TAG, "Skill ${intent.action} exceeded ${manifest.timeoutMillis}ms and was stopped", timeout)
            NuaRouteResult.ActionTaken("That took too long, so NUA stopped it rather than leaving it hanging.", succeeded = false)
        } catch (cancellation: CancellationException) {
            // Genuine cancellation (the ViewModel scope going away) — never swallow it.
            throw cancellation
        } catch (failure: Exception) {
            Log.e(TAG, "Skill ${intent.action} threw", failure)
            NuaRouteResult.ActionTaken("That failed unexpectedly — ${failure.message ?: failure::class.simpleName}.", succeeded = false)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
