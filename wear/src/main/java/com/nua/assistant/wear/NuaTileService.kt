package com.nua.assistant.wear

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

private const val RESOURCES_VERSION = "1"
private const val PRESENCE_PATH = "/nua/presence/v1"
private const val PRESENCE_KEY_LAST_VERIFIED_AT = "lastVerifiedAtMillis"

/**
 * Wear OS Presence surface.
 *
 * Reads the phone's read-only Presence v1 heartbeat through the Wear Data Layer and
 * displays recency. It does not pair devices, grant scopes or execute actions.
 */
class NuaTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        Wearable.getDataClient(this).dataItems.addOnCompleteListener { task ->
            val status = runCatching {
                if (task.isSuccessful) presenceStatusFrom(task.result) else null
            }.getOrNull() ?: "Open NUA on your phone"
            future.set(buildTile(status))
        }
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SettableFuture.create<ResourceBuilders.Resources>().also { future ->
            future.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        }

    private fun presenceStatusFrom(dataItems: DataItemBuffer): String? {
        try {
            val lastActiveAt = dataItems.firstOrNull { it.uri.path == PRESENCE_PATH }
                ?.let { DataMapItem.fromDataItem(it).dataMap }
                ?.let(::lastVerifiedAtOrNull)
                ?: return null
            return "Phone active ${relativeAge(lastActiveAt)}"
        } finally {
            dataItems.release()
        }
    }

    private fun lastVerifiedAtOrNull(dataMap: DataMap): Long? =
        if (dataMap.containsKey(PRESENCE_KEY_LAST_VERIFIED_AT)) {
            dataMap.getLong(PRESENCE_KEY_LAST_VERIFIED_AT)
        } else {
            null
        }

    private fun relativeAge(lastActiveAt: Long): String {
        val elapsedMillis = (System.currentTimeMillis() - lastActiveAt).coerceAtLeast(0L)
        val seconds = elapsedMillis / 1_000
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3_600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3_600}h ago"
        }
    }

    private fun buildTile(status: String): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(tileLayout(status))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun tileLayout(status: String): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .addContent(LayoutElementBuilders.Text.Builder().setText("NUA").build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(status).build())
            .build()
}
