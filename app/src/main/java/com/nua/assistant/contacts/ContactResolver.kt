package com.nua.assistant.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a spoken name ("Sam", "my sister") to a phone number or email address via the
 * on-device contacts provider — the official Android API, no OAuth. If [query] already
 * looks like a phone number or email, it's used directly rather than queried, so "text
 * 555-0100" and "invite priya@example.com" both work without a contacts match.
 */
@Singleton
class ContactResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun phoneNumberFor(query: String): String? {
        if (looksLikePhoneNumber(query)) return query.trim()
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf("%${query.trim()}%"),
                null,
            )?.use { cursor ->
                val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberCol >= 0 && cursor.moveToFirst()) cursor.getString(numberCol) else null
            }
        }
    }

    suspend fun emailFor(query: String): String? {
        if (looksLikeEmail(query)) return query.trim()
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val selection = "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?"
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                projection,
                selection,
                arrayOf("%${query.trim()}%"),
                null,
            )?.use { cursor ->
                val addressCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                if (addressCol >= 0 && cursor.moveToFirst()) cursor.getString(addressCol) else null
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private val PHONE_NUMBER_CHARSET_REGEX = Regex("^[+()\\- 0-9]{6,}$")
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private const val MIN_PHONE_DIGITS = 6

/** Matches only if every character is a phone-number character and there are enough actual digits — "(555) 123-4567" qualifies, "Sam" or "------" don't. */
internal fun looksLikePhoneNumber(query: String): Boolean {
    val trimmed = query.trim()
    if (!PHONE_NUMBER_CHARSET_REGEX.matches(trimmed)) return false
    return trimmed.count { it.isDigit() } >= MIN_PHONE_DIGITS
}

internal fun looksLikeEmail(query: String): Boolean = EMAIL_REGEX.matches(query.trim())
