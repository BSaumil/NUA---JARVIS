package com.nua.assistant.documents

import com.nua.assistant.memory.DocumentDao
import com.nua.assistant.memory.DocumentEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
) {
    suspend fun save(fileName: String, type: DocumentType, extractedText: String, summary: String?, expiryDate: Long?): Long =
        documentDao.insert(
            DocumentEntity(
                fileName = fileName,
                type = type,
                extractedText = extractedText,
                summary = summary,
                expiryDate = expiryDate,
            ),
        )

    suspend fun delete(id: Long) = documentDao.delete(id)

    suspend fun documentsPendingExpiryReminder() = documentDao.getWithPendingExpiry()

    suspend fun markExpiryReminded(id: Long) = documentDao.markExpiryReminded(id)

    fun observeAll() = documentDao.observeAll()
}
