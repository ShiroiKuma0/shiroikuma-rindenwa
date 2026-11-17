package org.linphone.shiroikuma

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.util.concurrent.Executors
import org.json.JSONArray
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactLoader
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.GlobalState
import org.linphone.core.SubscribePolicy
import org.linphone.core.tools.Log
import org.linphone.utils.PhoneNumberUtils

/**
 * shiroikuma-rindenwa fork — the one place this app's contacts come from: the sister address book
 * (shiroikuma-renrakusaki), through its private-contacts provider.
 *
 * ## Why not the system contact store any more
 *
 * 白い熊's contacts are private to 連絡先 since 2026-11: the phone's own contact store is empty on
 * purpose. Upstream reads that store ([ContactLoader]) and keeps a copy of it in the Linphone
 * friends database, so this app was left showing bare numbers — and, worse, holding a stale copy
 * of contacts that no longer exist anywhere it could re-read them from, next to a second list of
 * contacts created in its own editor that existed nowhere else at all.
 *
 * So both of those lists are deleted once ([purgeOnce]), the system store is no longer read, and
 * the names and pictures this app shows — in the call history, on the call screens, in
 * notifications — are resolved against one list built here.
 *
 * ## Nothing is kept
 *
 * The list is **not** stored in the friends database. It is rebuilt from the provider at core
 * start, again whenever the main screen comes back to the front, and when 連絡先 says something
 * changed. A copy kept here would be exactly the stale store this replaces.
 *
 * It is loaded at core start and not from the activity, as upstream's loader was: an incoming call
 * on a cold start has no activity, and must still find its caller's name.
 *
 * ## The contract (連絡先 1.6.0+122, read from its `MyContactsContentProvider`)
 *
 * `content://<authority>/contacts`, `selectionArgs[0]` = "1" for favourites only, `[1]` = "1" for
 * only contacts with a number — which is the **default when absent**, hence the explicit "0".
 * Columns: `raw_id`, `name`, `photo_uri`, `phone_numbers` (a JSON array of
 * `{value, type, label, normalizedNumber}`). There is no starred column, so the favourites are a
 * second query. A **null** cursor means not allowed, switched off or not installed; an empty one
 * means allowed and no contacts. Access is 連絡先's own allow-list (UI settings → Private
 * contacts), and needs no permission here — only the `<queries>` entry in the manifest.
 */
object SkRenrakusaki {
    private const val TAG = "[SK Renrakusaki]"

    /** The friend list this builds. Not one of upstream's names, so nothing else writes to it. */
    const val FRIEND_LIST = "Renrakusaki"

    /** `refKey` prefix: never a number, so no upstream code mistakes it for a system contact id. */
    const val REF_KEY_PREFIX = "rs-"

    private val AUTHORITIES = listOf(
        "shiroikuma.renrakusaki.contactsprovider",
        "shiroikuma.renrakusaki.debug.contactsprovider"
    )

    private const val PREF_PURGED = "sk_contacts_purged_v1"

    private const val CHANGE_DEBOUNCE_MS = 500L

    private val executor = Executors.newSingleThreadExecutor()

    /** What the last reload applied — an unchanged address book is not applied twice. */
    @Volatile
    private var lastApplied: Int? = null

    @Volatile
    private var observerRegistered = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val debouncedReload = Runnable { reload() }

    /**
     * 連絡先 signals from its database's invalidation tracker (since 1.6.0+123), so one edit can
     * arrive a moment after the write and more than once. The reload waits for the burst to end.
     */
    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(debouncedReload)
            mainHandler.postDelayed(debouncedReload, CHANGE_DEBOUNCE_MS)
        }
    }

    private class Number(val value: String, val type: Int, val label: String)

    private class Entry(
        val id: Int,
        val name: String,
        val photo: String,
        val numbers: List<Number>,
        var starred: Boolean = false
    ) {
        fun signature(): Int {
            var h = id
            h = 31 * h + name.hashCode()
            h = 31 * h + photo.hashCode()
            h = 31 * h + starred.hashCode()
            for (n in numbers) {
                h = 31 * h + n.value.hashCode()
                h = 31 * h + n.type
                h = 31 * h + n.label.hashCode()
            }
            return h
        }
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        purgeOnce(core)
        lastApplied = null
        reload()
        registerObserver(coreContext.context)
    }

    /**
     * Delete the two contact stores this app used to keep, once.
     *
     * 白い熊, 2026-11-17: "All get purged." — including the contacts made in this app's own
     * editor, which is why this is a flag-guarded one-off and not something that runs every start.
     */
    @WorkerThread
    private fun purgeOnce(core: Core) {
        val prefs = SkTheme.prefs(coreContext.context)
        if (prefs.getBoolean(PREF_PURGED, false)) return

        val names = listOf(
            ContactLoader.NATIVE_ADDRESS_BOOK_FRIEND_LIST,
            ContactLoader.LINPHONE_ADDRESS_BOOK_FRIEND_LIST
        )
        for (name in names) {
            val list = core.getFriendListByName(name) ?: continue
            val friends = list.friends
            Log.i("$TAG Purging friend list [$name] with [${friends.size}] friends")
            for (friend in friends) {
                list.removeFriend(friend)
            }
            core.removeFriendList(list)
        }
        prefs.edit().putBoolean(PREF_PURGED, true).apply()
    }

    /** Read the address book again and apply it if it changed. Cheap enough to call on resume. */
    @AnyThread
    fun reload() {
        val context = coreContext.context
        executor.execute {
            val entries = try {
                read(context)
            } catch (e: Exception) {
                Log.e("$TAG Reading the address book failed: $e")
                null
            }
            if (entries == null) {
                Log.w("$TAG No answer from the address book (not allowed, switched off or not installed)")
            }
            val list = entries ?: emptyList()
            val signature = list.fold(list.size) { acc, e -> 31 * acc + e.signature() }
            if (signature == lastApplied) return@execute

            coreContext.postOnCoreThread { core ->
                val state = core.globalState
                if (state == GlobalState.Shutdown || state == GlobalState.Off) return@postOnCoreThread
                apply(core, list)
                lastApplied = signature
            }
        }
    }

    @WorkerThread
    private fun read(context: Context): List<Entry>? {
        for (authority in AUTHORITIES) {
            val uri = Uri.parse("content://$authority/contacts")
            val all = query(context, uri, favouritesOnly = false) ?: continue
            val starred = query(context, uri, favouritesOnly = true).orEmpty().map { it.id }.toSet()
            for (entry in all) {
                entry.starred = entry.id in starred
            }
            return all
        }
        return null
    }

    @WorkerThread
    private fun query(context: Context, uri: Uri, favouritesOnly: Boolean): List<Entry>? {
        val args = arrayOf(if (favouritesOnly) "1" else "0", "0")
        val cursor = context.contentResolver.query(uri, null, null, args, null) ?: return null
        cursor.use {
            val idColumn = it.getColumnIndex("raw_id")
            val nameColumn = it.getColumnIndex("name")
            val photoColumn = it.getColumnIndex("photo_uri")
            val numbersColumn = it.getColumnIndex("phone_numbers")
            if (idColumn < 0 || nameColumn < 0) return null

            val result = ArrayList<Entry>(it.count)
            while (it.moveToNext()) {
                val numbers = ArrayList<Number>()
                val json = if (numbersColumn >= 0) it.getString(numbersColumn) else null
                if (!json.isNullOrEmpty()) {
                    try {
                        val array = JSONArray(json)
                        for (i in 0 until array.length()) {
                            val o = array.optJSONObject(i) ?: continue
                            val value = o.optString("value").ifEmpty { o.optString("normalizedNumber") }
                            if (value.isEmpty()) continue
                            numbers += Number(value, o.optInt("type", 0), o.optString("label"))
                        }
                    } catch (e: Exception) {
                        Log.w("$TAG Unreadable phone numbers for a contact: $e")
                    }
                }
                result += Entry(
                    it.getInt(idColumn),
                    it.getString(nameColumn).orEmpty(),
                    if (photoColumn >= 0) it.getString(photoColumn).orEmpty() else "",
                    numbers
                )
            }
            return result
        }
    }

    @WorkerThread
    private fun apply(core: Core, entries: List<Entry>) {
        val friends = ArrayList<Friend>(entries.size)
        for (entry in entries) {
            if (entry.name.isEmpty() && entry.numbers.isEmpty()) continue
            val friend = core.createFriend()
            friend.refKey = "$REF_KEY_PREFIX${entry.id}"
            friend.name = entry.name
            if (entry.photo.isNotEmpty()) friend.photo = entry.photo
            friend.starred = entry.starred
            friend.isSubscribesEnabled = false
            friend.incSubscribePolicy = SubscribePolicy.SPDeny
            for (number in entry.numbers) {
                val label = PhoneNumberUtils.addressBookLabelTypeToVcardParamString(
                    number.type,
                    number.label
                )
                friend.addPhoneNumberWithLabel(
                    Factory.instance().createFriendPhoneNumber(number.value, label)
                )
            }
            friends += friend
        }

        val existing = core.getFriendListByName(FRIEND_LIST)
        if (existing == null) {
            val list = core.createFriendList()
            list.isDatabaseStorageEnabled = false // see the class doc: nothing is kept
            list.type = FriendList.Type.Default
            list.displayName = FRIEND_LIST
            core.addFriendList(list)
            for (friend in friends) {
                list.addLocalFriend(friend)
            }
        } else {
            existing.synchronizeFriendsWith(friends.toTypedArray())
        }
        Log.i("$TAG [${friends.size}] contacts applied")

        // Upstream's name for "the address book has been (re)read": drops the cached avatar
        // models so every screen resolves its names again.
        coreContext.contactsManager.onNativeContactsLoaded()
    }

    /** The private id 連絡先 knows this friend by, or null when the friend is not one of its. */
    fun contactIdOf(friend: Friend?): Int? =
        friend?.refKey?.takeIf { it.startsWith(REF_KEY_PREFIX) }
            ?.removePrefix(REF_KEY_PREFIX)?.toIntOrNull()

    private fun registerObserver(context: Context) {
        if (observerRegistered) return
        for (authority in AUTHORITIES) {
            try {
                context.contentResolver.registerContentObserver(
                    Uri.parse("content://$authority/contacts"),
                    false,
                    observer
                )
                observerRegistered = true
            } catch (e: Exception) {
                // Not installed, or a build that sends no change signal: reload-on-resume covers it.
            }
        }
    }
}
