package org.linphone.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AnyThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * shiroikuma-rindenwa fork — the missed calls not yet looked at, counted by the account they
 * really rang on.
 *
 * The SDK keeps such a count per account, but by the local side of its call record, and for an
 * incoming call that side may name the account that was merely selected then
 * ([SkCallStamps.accountOf]): the call was listed under one account's tab and counted on
 * another's chip (白い熊, 2026-11-26). So the count is ours: [SkCallStamps] notes every call that
 * ends missed with the identity of its stamp, and looking at an account's history forgets that
 * account's.
 *
 * One entry per call in the preferences file `sk_missed_calls` — the stamp key, and the identity
 * as its value. It is a state of this phone, not a record, and is not exported.
 */
object SkMissedCalls {
    private const val PREFS_FILE = "sk_missed_calls"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    @Volatile
    private var cache: ConcurrentHashMap<String, String>? = null

    private fun all(context: Context): ConcurrentHashMap<String, String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val read = ConcurrentHashMap<String, String>()
            for ((key, value) in prefs(context).all) {
                if (value is String) read[key] = value
            }
            cache = read
            return read
        }
    }

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** Called on whichever thread changed a count — the core thread, in practice. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() {
        for (listener in listeners) listener()
    }

    /** The call [key] ended missed on the account [identity]. */
    @AnyThread
    fun note(context: Context, key: String, identity: String) {
        if (key.isEmpty() || all(context).put(key, identity) == identity) return
        prefs(context).edit().putString(key, identity).apply()
        changed()
    }

    /** The call [key] was not a missed one after all — it was taken on another device. */
    @AnyThread
    fun forget(context: Context, key: String) {
        if (all(context).remove(key) == null) return
        prefs(context).edit().remove(key).apply()
        changed()
    }

    @AnyThread
    fun has(context: Context, key: String): Boolean = all(context).containsKey(key)

    @AnyThread
    fun count(context: Context, identity: String?): Int =
        if (identity.isNullOrEmpty()) 0 else all(context).values.count { it == identity }

    @AnyThread
    fun total(context: Context): Int = all(context).size

    /** The history of [identity] was looked at; of every account when [identity] is null. */
    @AnyThread
    fun reset(context: Context, identity: String?) {
        val map = all(context)
        val gone = map.entries.filter { identity == null || it.value == identity }.map { it.key }
        if (gone.isEmpty()) return
        val editor = prefs(context).edit()
        for (key in gone) {
            map.remove(key)
            editor.remove(key)
        }
        editor.apply()
        changed()
    }
}
