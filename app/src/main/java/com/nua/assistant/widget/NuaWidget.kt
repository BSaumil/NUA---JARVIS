package com.nua.assistant.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.weather.WeatherRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * GlanceAppWidget isn't an Android component Hilt can inject directly (unlike
 * Activities/Services/ViewModels) — this is the standard EntryPoint escape hatch for
 * reaching the same singletons from a plain class.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NuaWidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun calendarReader(): CalendarReader
    fun notificationRepository(): NotificationRepository
}

/** Next calendar event, current weather, and the notification summary — refreshed on Android's own widget update cycle (see nua_widget_info.xml). */
class NuaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, NuaWidgetEntryPoint::class.java)

        val weather = entryPoint.weatherRepository().currentSnapshot().getOrNull()
        val nextEvent = entryPoint.calendarReader()
            .eventsBetween(System.currentTimeMillis(), System.currentTimeMillis() + ONE_DAY_MILLIS)
            .firstOrNull()
        val notificationSpokenSummary = entryPoint.notificationRepository().summary().spokenSummary

        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("NUA", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Text(
                    weather?.let { "${it.condition}, ${it.currentTempC.toInt()}°C" } ?: "Weather unavailable",
                    style = TextStyle(fontSize = 13.sp),
                )
                Text(
                    nextEvent?.let { "Next: ${it.title}" } ?: "Nothing on the calendar today",
                    style = TextStyle(fontSize = 13.sp),
                )
                Text(notificationSpokenSummary, style = TextStyle(fontSize = 13.sp))
            }
        }
    }

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
