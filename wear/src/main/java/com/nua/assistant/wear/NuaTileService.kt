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
private const val PRESENCE_PATH = "/nua/presence"

// Mirrors app/src/main/java/com/nua/assistant/presence/PresenceSnapshot.kt's key name --
// duplicated rather than shared, since :wear pulling in :app's whole Hilt/Compose
// dependency graph for one constant would be a far worse cost than this small, honest
// duplication. See docs/PRESENCE_MESH_RFC.md.
private const val PRESENCE_KEY_LAST_ACTIVE_AT = "lastActiveAt"

/**
 * Reads the phone's last published presence via the real Wearable Data Layer API
 * (Presence Mesh, directive item 15 -- see docs/PRESENCE_MESH_RFC.md) and renders it,
 * replacing the fixed text this tile previously always showed. Falls back to that same
 * static text whenever no presence has ever been published, the read fails, or the
 * payload is malformed -- a tile must never crash or show nothing just because the phone
 * side hasn't published yet (first install) or the real DataClient round trip doesn't
 * work in a given environment, which this one is honest about not having verified (the
 * RFC's §4). Entirely callback-driven, never blocking the calling thread -- the Tile
 * system's own threading guarantees for [onTileRequest] aren't something this module
 * should gamble an ANR on.
 */
class NuaTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        Wearable.getDataClient(this).dataItems.addOnCompleteListener { task ->
            val status = runCatching {
                if (task.isSuccessful) presenceStatusFrom(task.result) else null
            }.getOrNull() ?: "Say a wake word to talk"
            future.set(buildTile(status))
        }
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = SettableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        return future
    }

    private fun presenceStatusFrom(dataItems: DataItemBuffer): String? {
        try {
            val lastActiveAt = dataItems.firstOrNull { it.uri.path == PRESENCE_PATH }
                ?.let { DataMapItem.fromDataItem(it).dataMap }
                ?.let(::lastActiveAtOrNull)
                ?: return null
            return "Phone active ${secondsAgoText(lastActiveAt)}"
        } finally {
            dataItems.release()
        }
    }

    private fun lastActiveAtOrNull(dataMap: DataMap): Long? =
        if (dataMap.containsKey(PRESENCE_KEY_LAST_ACTIVE_AT)) dataMap.getLong(PRESENCE_KEY_LAST_ACTIVE_AT) else null

    private fun secondsAgoText(lastActiveAt: Long): String {
        val seconds = (System.currentTimeMillis() - lastActiveAt) / 1000
        return if (seconds < 60) "${seconds}s ago" else "${seconds / 60}m ago"
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
