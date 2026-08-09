package com.nua.assistant.wear

import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"

/**
 * Minimal status tile — no live data from the phone app yet (that needs the Wearable
 * Data Layer API, not built here), just a static reminder of how to talk to NUA. A
 * later version would push the next calendar event or notification summary down via
 * DataClient and render it here instead of this fixed text. Not verified against a
 * real Wear OS device or emulator — see README's "Known gaps".
 */
class NuaTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(tileLayout())
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun tileLayout(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .addContent(LayoutElementBuilders.Text.Builder().setText("NUA").build())
            .addContent(LayoutElementBuilders.Text.Builder().setText("Say a wake word to talk").build())
            .build()
}
