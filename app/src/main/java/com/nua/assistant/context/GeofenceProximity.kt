package com.nua.assistant.context

import com.nua.assistant.memory.GeofenceEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Great-circle distance between two lat/lng points, in meters. Deliberately not
 * `android.location.Location.distanceBetween` — that's an Android framework call this
 * project's JVM unit tests (no Robolectric) can't exercise; this is the same formula,
 * implemented in pure Kotlin so it's directly testable.
 */
fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

/** The first saved place whose radius actually contains (lat, lng), or null if none does. */
fun nearestContainingGeofence(latitude: Double, longitude: Double, geofences: List<GeofenceEntity>): GeofenceEntity? =
    geofences.firstOrNull { geofence ->
        haversineDistanceMeters(latitude, longitude, geofence.latitude, geofence.longitude) <= geofence.radiusMeters
    }
