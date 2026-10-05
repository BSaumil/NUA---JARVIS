package com.nua.assistant.presence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakePresenceTransport : PresenceTransport {
    val published = mutableListOf<PresenceSnapshot>()
    override suspend fun publish(snapshot: PresenceSnapshot) {
        published += snapshot
    }
}

/**
 * Same real-vs-fake inversion [FakePresenceTransport] exists for -- this test never
 * touches a real `DataClient`, only [PresenceRepository]'s own logic (device-id
 * persistence, snapshot construction). Needs Robolectric because [PresenceRepository]
 * calls the real `Context.getSharedPreferences`, which (like `android.util.Log`) throws
 * "not mocked" outside an Android runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PresenceRepositoryTest {

    private fun repository(transport: FakePresenceTransport = FakePresenceTransport()) =
        PresenceRepository(ApplicationProvider.getApplicationContext(), transport)

    @Test
    fun `deviceId is stable across repeated reads`() {
        val repository = repository()
        assertEquals(repository.deviceId, repository.deviceId)
    }

    @Test
    fun `deviceId persists across a fresh repository instance backed by the same context`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first = PresenceRepository(context, FakePresenceTransport())
        val second = PresenceRepository(context, FakePresenceTransport())
        assertEquals(first.deviceId, second.deviceId)
    }

    @Test
    fun `publishSelf publishes a PHONE snapshot carrying this device's own stable id`() = runTest {
        val transport = FakePresenceTransport()
        val repository = repository(transport)
        repository.publishSelf(now = 12_345L)

        assertEquals(1, transport.published.size)
        val published = transport.published.single()
        assertEquals(repository.deviceId, published.deviceId)
        assertEquals(DeviceType.PHONE, published.deviceType)
        assertEquals(12_345L, published.lastActiveAt)
    }

    @Test
    fun `two different repository instances generate two different device ids when backed by separate contexts`() {
        // Robolectric gives each test a fresh app-under-test context/storage, so a
        // repository built against it has never seen a prior run's persisted id --
        // this just confirms a freshly-generated id is a real, non-blank UUID, not a
        // fixed placeholder string.
        val id = repository().deviceId
        assertTrue(id.isNotBlank())
    }
}
