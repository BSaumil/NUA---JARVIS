package com.nua.assistant.presence

/** Which kind of device reported a [PresenceSnapshot] — see `docs/PRESENCE_MESH_RFC.md`. */
enum class DeviceType { PHONE, WATCH }

/** One device's self-reported activity, as of [lastActiveAt]. [deviceId] is a stable,
 *  per-install identifier (see [com.nua.assistant.presence.PresenceRepository]) — never
 *  a hardware identifier. */
data class PresenceSnapshot(
    val deviceId: String,
    val deviceType: DeviceType,
    val lastActiveAt: Long,
)

/**
 * Pure: the simplest honest presence merge rule — whichever device most recently
 * reported activity. Null for an empty list, never a fabricated default. No staleness/
 * expiry model yet — see the RFC's §3 for why that's a real next slice, not invented
 * speculatively now.
 */
fun mostRecentlyActive(snapshots: List<PresenceSnapshot>): PresenceSnapshot? =
    snapshots.maxByOrNull { it.lastActiveAt }
