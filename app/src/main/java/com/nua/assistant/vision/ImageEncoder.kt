package com.nua.assistant.vision

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EncodedImage(val base64: String, val mediaType: String)

/** Reads a captured photo into the base64 form Claude's vision API expects. */
@Singleton
class ImageEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun encode(uri: Uri): EncodedImage? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val mediaType = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            EncodedImage(base64 = Base64.encodeToString(bytes, Base64.NO_WRAP), mediaType = mediaType)
        } catch (t: Exception) {
            null
        }
    }
}
