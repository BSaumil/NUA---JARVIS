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

private val PHONE_NUMBER_REGEX = Regex("^[+0-9][0-9 ()-]{5,}$")
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

internal fun looksLikePhoneNumber(query: String): Boolean = PHONE_NUMBER_REGEX.matches(query.trim())

internal fun looksLikeEmail(query: String): Boolean = EMAIL_REGEX.matches(query.trim())
