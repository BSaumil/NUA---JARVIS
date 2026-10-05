package com.nua.assistant.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun snapshot(deviceId: String, type: DeviceType, lastActiveAt: Long) =
    PresenceSnapshot(deviceId = deviceId, deviceType = type, lastActiveAt = lastActiveAt)

class PresenceSnapshotTest {

    @Test
    fun `an empty list has no most recently active device`() {
        assertNull(mostRecentlyActive(emptyList()))
    }

    @Test
    fun `a single snapshot is trivially the most recently active`() {
        val only = snapshot("phone-1", DeviceType.PHONE, 1_000L)
        assertEquals(only, mostRecentlyActive(listOf(only)))
    }

    @Test
    fun `the device with the latest lastActiveAt wins, regardless of device type`() {
        val phone = snapshot("phone-1", DeviceType.PHONE, 1_000L)
        val watch = snapshot("watch-1", DeviceType.WATCH, 2_000L)
        assertEquals(watch, mostRecentlyActive(listOf(phone, watch)))
        assertEquals(watch, mostRecentlyActive(listOf(watch, phone)))
    }

    @Test
    fun `an older snapshot never wins over a newer one from the same device`() {
        val older = snapshot("phone-1", DeviceType.PHONE, 1_000L)
        val newer = snapshot("phone-1", DeviceType.PHONE, 5_000L)
        assertEquals(newer, mostRecentlyActive(listOf(older, newer)))
    }
}
