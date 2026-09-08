package com.nua.assistant.privacy

import com.nua.assistant.memory.NuaDatabase
import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.voice.OwnerVoiceProfileStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears every category of locally stored data this app's own encryption audit
 * (`security/EncryptionAudit.kt`) names: the Room database (facts, goals, decisions,
 * dreams, documents, messages, the trust ledger — everything), the saved Claude API key,
 * and the enrolled voice profile. Irreversible — the caller must already have the user's
 * explicit confirmation before calling this; nothing here asks again.
 */
@Singleton
class PrivacyRepository @Inject constructor(
    private val database: NuaDatabase,
    private val secureKeyRepository: SecureKeyRepository,
    private val ownerVoiceProfileStore: OwnerVoiceProfileStore,
) {
    suspend fun resetAllData() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        secureKeyRepository.clearApiKey()
        ownerVoiceProfileStore.clear()
    }
}
