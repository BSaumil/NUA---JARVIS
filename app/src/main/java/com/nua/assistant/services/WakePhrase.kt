package com.nua.assistant.services

import ai.picovoice.porcupine.Porcupine
import android.content.Context

/** Where a wake phrase's detection model comes from. */
sealed class WakePhraseSource {
    /** One of Porcupine's built-in keywords — ships inside the SDK, no asset file needed. */
    data class BuiltIn(val keyword: Porcupine.BuiltInKeyword) : WakePhraseSource()

    /**
     * A custom keyword trained on Picovoice Console for this specific phrase, exported
     * for Android, and dropped into app/src/main/assets/ under [assetFileName]. See
     * app/src/main/assets/README.md for the steps. Phrases whose file isn't present yet
     * are skipped at runtime rather than crashing — see NuaForegroundService.
     */
    data class CustomAsset(val assetFileName: String) : WakePhraseSource()
}

data class WakePhrase(
    val id: String,
    val displayText: String,
    val source: WakePhraseSource,
)

/**
 * Every phrase NUA can wake up to. "Jarvis" works out of the box (Porcupine built-in);
 * the rest need their trained model dropped into assets/ before they'll actually fire
 * (see app/src/main/assets/README.md) — NuaForegroundService only activates the ones
 * whose asset is actually present, so this list can grow ahead of having the files.
 *
 * To add a new one: train it on Picovoice Console, export the Android .ppn, add it to
 * assets/ following the naming convention below, then add one line here. Nothing else
 * needs to change.
 */
object WakePhrases {
    val ALL: List<WakePhrase> = listOf(
        WakePhrase("jarvis", "Jarvis", WakePhraseSource.BuiltIn(Porcupine.BuiltInKeyword.JARVIS)),
        WakePhrase("hey_nua", "Hey NUA", WakePhraseSource.CustomAsset("hey_nua_android.ppn")),
        WakePhrase("hello_nua", "Hello NUA", WakePhraseSource.CustomAsset("hello_nua_android.ppn")),
        WakePhrase("nua", "NUA", WakePhraseSource.CustomAsset("nua_android.ppn")),
        WakePhrase("daddys_home", "Daddy's Home", WakePhraseSource.CustomAsset("daddys_home_android.ppn")),
        WakePhrase("wake_up_sleepy_head", "Wake up Sleepy Head", WakePhraseSource.CustomAsset("wake_up_sleepy_head_android.ppn")),
    )
}

/** True if this phrase can actually be detected right now — built-ins always can; custom ones need their asset present. */
fun WakePhrase.isAvailable(context: Context): Boolean = when (val source = source) {
    is WakePhraseSource.BuiltIn -> true
    is WakePhraseSource.CustomAsset -> runCatching { context.assets.open(source.assetFileName).close() }.isSuccess
}

/** The subset of [WakePhrases.ALL] that will actually fire on this device right now. */
fun availableWakePhrases(context: Context): List<WakePhrase> = WakePhrases.ALL.filter { it.isAvailable(context) }
