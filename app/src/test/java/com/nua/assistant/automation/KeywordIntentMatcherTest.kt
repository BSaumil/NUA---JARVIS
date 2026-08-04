package com.nua.assistant.automation

import com.nua.assistant.ai.NuaActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordIntentMatcherTest {

    @Test
    fun `open prefix matches OPEN_APP with the app name stripped`() {
        val result = KeywordIntentMatcher.match("open Spotify")
        assertEquals(NuaActionType.OPEN_APP, result?.action)
        assertEquals("spotify", result?.parameters?.get("app"))
    }

    @Test
    fun `launch prefix matches OPEN_APP`() {
        val result = KeywordIntentMatcher.match("launch Settings")
        assertEquals(NuaActionType.OPEN_APP, result?.action)
        assertEquals("settings", result?.parameters?.get("app"))
    }

    @Test
    fun `pause matches MEDIA_CONTROL pause`() {
        val result = KeywordIntentMatcher.match("pause the music")
        assertEquals(NuaActionType.MEDIA_CONTROL, result?.action)
        assertEquals("pause", result?.parameters?.get("command"))
    }

    @Test
    fun `bare play matches MEDIA_CONTROL play`() {
        val result = KeywordIntentMatcher.match("play")
        assertEquals(NuaActionType.MEDIA_CONTROL, result?.action)
        assertEquals("play", result?.parameters?.get("command"))
    }

    @Test
    fun `resume matches MEDIA_CONTROL play`() {
        val result = KeywordIntentMatcher.match("resume please")
        assertEquals("play", result?.parameters?.get("command"))
    }

    @Test
    fun `skip matches MEDIA_CONTROL next`() {
        val result = KeywordIntentMatcher.match("skip this one")
        assertEquals("next", result?.parameters?.get("command"))
    }

    @Test
    fun `go back matches MEDIA_CONTROL previous`() {
        val result = KeywordIntentMatcher.match("go back a track")
        assertEquals("previous", result?.parameters?.get("command"))
    }

    @Test
    fun `weather mention matches GET_WEATHER`() {
        assertEquals(NuaActionType.GET_WEATHER, KeywordIntentMatcher.match("what's the weather like")?.action)
    }

    @Test
    fun `notification mention matches READ_NOTIFICATIONS`() {
        assertEquals(NuaActionType.READ_NOTIFICATIONS, KeywordIntentMatcher.match("read my notifications")?.action)
    }

    @Test
    fun `brief me matches MORNING_BRIEFING`() {
        assertEquals(NuaActionType.MORNING_BRIEFING, KeywordIntentMatcher.match("brief me")?.action)
    }

    @Test
    fun `ambiguous mood statement misses the fast path`() {
        assertNull(KeywordIntentMatcher.match("I'm bored"))
    }

    @Test
    fun `conversational planning request misses the fast path`() {
        // This is exactly what should fall through to Claude-based classification (PLAN_TASK).
        assertNull(KeywordIntentMatcher.match("I'm going camping tomorrow"))
    }
}
