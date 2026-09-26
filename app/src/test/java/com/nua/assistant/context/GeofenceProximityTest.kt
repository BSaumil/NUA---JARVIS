package com.nua.assistant.context

import com.nua.assistant.memory.GeofenceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun geofence(name: String, lat: Double, lon: Double, radiusMeters: Float) =
    GeofenceEntity(name = name, latitude = lat, longitude = lon, radiusMeters = radiusMeters, message = "")

class GeofenceProximityTest {

    @Test
    fun `the same point is zero meters from itself`() {
        assertEquals(0.0, haversineDistanceMeters(37.7749, -122.4194, 37.7749, -122.4194), 0.001)
    }

    @Test
    fun `a known distance is roughly correct`() {
        // San Francisco to Oakland city halls, roughly 13 km apart.
        val distance = haversineDistanceMeters(37.7793, -122.4193, 37.8044, -122.2712)
        assertTrue("expected roughly 13km, got ${distance}m", distance in 12_000.0..14_000.0)
    }

    @Test
    fun `nearestContainingGeofence finds a place whose radius contains the point`() {
        val home = geofence("Home", 37.7749, -122.4194, radiusMeters = 100f)
        val result = nearestContainingGeofence(37.7749, -122.4194, listOf(home))
        assertEquals(home, result)
    }

    @Test
    fun `a point outside every radius matches nothing`() {
        val home = geofence("Home", 37.7749, -122.4194, radiusMeters = 50f)
        // Oakland — tens of kilometers away, well outside a 50m radius.
        val result = nearestContainingGeofence(37.8044, -122.2712, listOf(home))
        assertNull(result)
    }

    @Test
    fun `an empty geofence list never matches`() {
        assertNull(nearestContainingGeofence(37.7749, -122.4194, emptyList()))
    }

    @Test
    fun `the first containing geofence wins when radii overlap`() {
        val work = geofence("Work", 37.7749, -122.4194, radiusMeters = 200f)
        val cafe = geofence("Cafe", 37.7750, -122.4195, radiusMeters = 200f)
        val result = nearestContainingGeofence(37.7749, -122.4194, listOf(work, cafe))
        assertEquals(work, result)
    }
}
