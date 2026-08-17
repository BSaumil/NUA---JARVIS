package com.nua.assistant.documents

/** How a document was ingested — determines which extractor produced its text. */
enum class DocumentType { PDF, WORD, IMAGE }

private const val WORD_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Maps a picked file's MIME type to the extractor that can read it, or null if it's not one NUA supports. */
fun documentTypeForMime(mimeType: String?): DocumentType? = when {
    mimeType == "application/pdf" -> DocumentType.PDF
    mimeType == WORD_MIME_TYPE -> DocumentType.WORD
    mimeType?.startsWith("image/") == true -> DocumentType.IMAGE
    else -> null
}
