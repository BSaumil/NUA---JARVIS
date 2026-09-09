package com.nua.assistant.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.nua.assistant.memory.GeofenceDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Resolves "which saved place is the user near right now," if any — never raw
 * coordinates. Raw lat/lng never leaves this class or reaches a Claude prompt; only a
 * saved place's own name does (see [ContextSnapshot.currentPlace]/[describe]) — the same
 * "minimise what's transmitted" reasoning this project already applies to what enters a
 * prompt at all. Needs only `ACCESS_COARSE_LOCATION` (already requested at app start —
 * see `MainActivity`), not `ACCESS_FINE_LOCATION`/`ACCESS_BACKGROUND_LOCATION`, which are
 * `GeofenceManager`'s continuous-monitoring requirement, not this one-shot lookup's.
 */
@Singleton
class CurrentPlaceResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofenceDao: GeofenceDao,
) {
    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun currentPlaceName(): String? {
        if (!hasPermission()) return null
        val geofences = geofenceDao.getAll()
        // Nothing to match against — skip the location read entirely rather than pay its
        // cost (and touch location data at all) for a query that can only return null.
        if (geofences.isEmpty()) return null
        val location = awaitLastLocation() ?: return null
        return nearestContainingGeofence(location.latitude, location.longitude, geofences)?.name
    }

    @Suppress("MissingPermission") // hasPermission() guards every call site in currentPlaceName()
    private suspend fun awaitLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { continuation.resume(null) }
        }
}
