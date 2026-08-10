package com.nua.assistant.geofencing

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.nua.assistant.memory.GeofenceDao
import com.nua.assistant.memory.GeofenceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1: Play Services Geofencing is an official API, not automation. Registering a
 * geofence needs ACCESS_FINE_LOCATION always, plus ACCESS_BACKGROUND_LOCATION on API
 * 29+ for transitions to fire while NUA isn't in the foreground — both declared in the
 * manifest. Geofences persist in Room (GeofenceDao) so they survive process death and
 * can be re-registered after a reboot (see BootCompletedReceiver-style re-registration
 * — not wired to a boot receiver yet, so a device reboot currently drops active
 * geofences until the app is next opened, which calls [registerAll]).
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofenceDao: GeofenceDao,
) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    suspend fun addGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        message: String,
    ): Result<Long> {
        val entity = GeofenceEntity(name = name, latitude = latitude, longitude = longitude, radiusMeters = radiusMeters, message = message)
        val id = geofenceDao.insert(entity)
        if (hasPermission()) registerOne(entity.copy(id = id))
        return Result.success(id)
    }

    suspend fun removeGeofence(id: Long) {
        geofencingClient.removeGeofences(listOf(id.toString()))
        geofenceDao.deleteById(id)
    }

    /** Re-registers every saved geofence — call after permission is granted or on app start. */
    suspend fun registerAll() {
        if (!hasPermission()) return
        geofenceDao.getAll().forEach { registerOne(it) }
    }

    private fun registerOne(entity: GeofenceEntity) {
        if (!hasPermission()) return
        val geofence = Geofence.Builder()
            .setRequestId(entity.id.toString())
            .setCircularRegion(entity.latitude, entity.longitude, entity.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        @Suppress("MissingPermission") // hasPermission() guards this above
        geofencingClient.addGeofences(request, geofencePendingIntent())
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, NuaGeofenceBroadcastReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
