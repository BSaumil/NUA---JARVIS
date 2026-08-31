package com.nua.assistant.memory

/**
 * User-set sensitivity for a stored fact. This is a manual toggle the user applies from
 * Settings ("Mark as sensitive") — NUA does not attempt to auto-classify what's sensitive,
 * since a wrong automatic guess (in either direction) is worse than no guess at all.
 */
enum class MemoryPrivacyLevel {
    STANDARD,
    SENSITIVE,
}
