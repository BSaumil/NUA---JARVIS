package com.nua.assistant.automation

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1: launches apps via PackageManager's launch-intent lookup, no accessibility
 * automation involved. [commonPackageNames] is a best-effort shortcut for popular
 * apps; [launch] always falls back to searching installed launcher activities by
 * label, so it isn't broken by a stale package name — verify against what's actually
 * installed on a given device before trusting the map alone (see README known gaps).
 */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val commonPackageNames = mapOf(
        "spotify" to "com.spotify.music",
        "youtube" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera",
        "settings" to "com.android.settings",
        "chrome" to "com.android.chrome",
        "calendar" to "com.google.android.calendar",
        "phone" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
    )

    fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
    }.isSuccess

    /** Returns true if an installed app was found and launched. */
    fun launch(appName: String): Boolean {
        val normalized = appName.trim().lowercase()
        val packageName = commonPackageNames[normalized]?.takeIf { isInstalled(it) }
            ?: findInstalledPackageByLabel(normalized)
            ?: return false

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun findInstalledPackageByLabel(query: String): String? {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .firstOrNull { it.loadLabel(packageManager).toString().lowercase().contains(query) }
            ?.activityInfo?.packageName
    }
}
