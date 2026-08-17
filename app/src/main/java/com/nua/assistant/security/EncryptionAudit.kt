package com.nua.assistant.security

data class EncryptionAuditEntry(val label: String, val encrypted: Boolean, val detail: String)

/**
 * A plain accounting of what's actually encrypted at rest today, not a claim about what
 * "should" be. The two `EncryptedSharedPreferences` stores are real AES-256-GCM with
 * Android Keystore key material; everything else (messages, facts, documents, decisions,
 * goals — all Room/SQLite) relies only on Android's per-app storage sandbox, the same as
 * most local-first apps, and isn't additionally encrypted at the app level.
 */
fun encryptionAuditEntries(): List<EncryptionAuditEntry> = listOf(
    EncryptionAuditEntry(
        label = "Claude API key",
        encrypted = true,
        detail = "EncryptedSharedPreferences (AES-256-GCM), key material in the Android Keystore. See memory/SecureKeyRepository.kt.",
    ),
    EncryptionAuditEntry(
        label = "Owner voice profile",
        encrypted = true,
        detail = "EncryptedSharedPreferences (AES-256-GCM), key material in the Android Keystore. See voice/OwnerVoiceProfileStore.kt.",
    ),
    EncryptionAuditEntry(
        label = "Conversation history, facts, documents, decisions, goals",
        encrypted = false,
        detail = "Plain Room/SQLite database (memory/MemoryStore.kt), protected only by Android's per-app storage sandbox — not additionally encrypted at the app level.",
    ),
)
