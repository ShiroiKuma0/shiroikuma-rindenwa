package org.linphone.shiroikuma

import android.content.Context
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import org.linphone.core.tools.Log

/**
 * shiroikuma-rindenwa fork — the phonetic readings (フリガナ) of the native address book.
 *
 * Without these the Contacts list cannot have Japanese letter headings at all: a contact written
 * 白い熊 爲右衞門 sorts under its kanji, and kanji belong to no gojūon row, so every Japanese name
 * fell into ＃. The sister address book (shiroikuma-renrakusaki) buckets by the reading first,
 * which is what puts さ and ま rows on its screen — this reads the same field.
 *
 * The whole table is read in one pass and cached, rather than queried per contact: an address book
 * of several hundred names would otherwise mean several hundred content-provider round trips.
 */
object SkContactReadings {
    private const val TAG = "[SK Contact Readings]"

    @Volatile
    private var readings: Map<Long, String> = emptyMap()

    @Volatile
    private var loaded = false

    /** Drop the cache — the address book has changed. */
    fun invalidate() {
        loaded = false
    }

    /**
     * The reading stored for a native contact, or null when it has none (or is not native).
     * [refKey] is the native contact id, which is how the Linphone SDK keys imported friends.
     */
    @WorkerThread
    fun readingFor(context: Context, refKey: String?): String? {
        val id = refKey?.toLongOrNull() ?: return null
        ensureLoaded(context)
        return readings[id]
    }

    @WorkerThread
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            readings = try {
                query(context)
            } catch (e: Exception) {
                // No READ_CONTACTS, no provider, a vendor cursor that misbehaves — the list simply
                // buckets by name, exactly as it did before readings existed.
                Log.w("$TAG Could not read phonetic names: $e")
                emptyMap()
            }
            loaded = true
        }
    }

    @WorkerThread
    private fun query(context: Context): Map<Long, String> {
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME,
            ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME,
        )
        val result = HashMap<Long, String>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val familyIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME
            )
            val givenIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME
            )
            if (idIndex < 0) return@use
            while (cursor.moveToNext()) {
                // Family first: the list sorts the way a Japanese address book is read, and a
                // contact with only a given-name reading still buckets on what it has.
                val family = familyIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
                val given = givenIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
                val reading = (family + given).trim()
                if (reading.isNotEmpty()) {
                    result[cursor.getLong(idIndex)] = reading
                }
            }
        }
        Log.i("$TAG Loaded [${result.size}] phonetic readings from the address book")
        return result
    }
}
