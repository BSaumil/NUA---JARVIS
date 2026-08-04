package com.nua.assistant.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class WeatherSnapshot(
    val currentTempC: Double,
    val condition: String,
    val precipitationChancePercent: Int,
    val highTempC: Double,
    val lowTempC: Double,
)

@Serializable
private data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperature2m: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0,
)

@Serializable
private data class OpenMeteoDaily(
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int> = emptyList(),
)

@Serializable
private data class OpenMeteoResponse(
    val current: OpenMeteoCurrent = OpenMeteoCurrent(),
    val daily: OpenMeteoDaily = OpenMeteoDaily(),
)

/** WMO weather codes (https://open-meteo.com/en/docs) collapsed to short human text. */
private fun describeWeatherCode(code: Int): String = when (code) {
    0 -> "clear"
    1, 2, 3 -> "partly cloudy"
    45, 48 -> "foggy"
    51, 53, 55, 56, 57 -> "drizzly"
    61, 63, 65, 66, 67 -> "rainy"
    71, 73, 75, 77 -> "snowy"
    80, 81, 82 -> "showery"
    85, 86 -> "snow showers"
    95, 96, 99 -> "thunderstorms"
    else -> "unsettled"
}

/**
 * Weather via Open-Meteo — no API key required. Location comes from the last known
 * fix on-device (coarse permission only); this never calls into Google Play Services.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    suspend fun currentSnapshot(): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        val location = lastKnownLocation()
            ?: return@withContext Result.failure(IllegalStateException("No location fix available yet."))

        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=1&timezone=auto"

        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Weather lookup failed: HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(OpenMeteoResponse.serializer(), body)
                Result.success(
                    WeatherSnapshot(
                        currentTempC = parsed.current.temperature2m,
                        condition = describeWeatherCode(parsed.current.weatherCode),
                        precipitationChancePercent = parsed.daily.precipitationProbabilityMax.firstOrNull() ?: 0,
                        highTempC = parsed.daily.temperature2mMax.firstOrNull() ?: parsed.current.temperature2m,
                        lowTempC = parsed.daily.temperature2mMin.firstOrNull() ?: parsed.current.temperature2m,
                    ),
                )
            }
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    private fun lastKnownLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
