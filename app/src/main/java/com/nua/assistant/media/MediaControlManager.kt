package com.nua.assistant.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import com.nua.assistant.notifications.NuaNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media playback control via MediaSessionManager — the official Android API for
 * inspecting/controlling whatever's currently playing. Requires notification listener
 * access (the same grant NuaNotificationListenerService already needs), not a
 * per-app integration.
 */
@Singleton
class MediaControlManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mediaSessionManager: MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val listenerComponent = ComponentName(context, NuaNotificationListenerService::class.java)

    private fun activeSession(): MediaController? = runCatching {
        mediaSessionManager?.getActiveSessions(listenerComponent)?.firstOrNull()
    }.getOrNull()

    fun nowPlayingTitle(): String? =
        activeSession()?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)

    fun play(): Boolean = activeSession()?.transportControls?.play() != null
    fun pause(): Boolean = activeSession()?.transportControls?.pause() != null
    fun next(): Boolean = activeSession()?.transportControls?.skipToNext() != null
    fun previous(): Boolean = activeSession()?.transportControls?.skipToPrevious() != null

    /**
     * Starts playback matching [query] via the standard media-search intent
     * (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) rather than a per-app SDK —
     * any installed music app that supports voice search (Spotify, YouTube Music, etc.)
     * can pick it up. Returns false if no app on the device handles it.
     */
    fun playByQuery(query: String): Boolean {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }
}
