package org.linphone.shiroikuma

import android.content.Context
import androidx.lifecycle.MutableLiveData

/**
 * shiroikuma-rindenwa fork — 白い熊's own order for the accounts.
 *
 * The Core hands the account list back in whatever order it was configured; the fork lets that
 * order be dragged into shape, and the same order drives **both** places accounts are listed: the
 * tab strip at the top of the main screens and the list in the drawer menu. Whichever one is
 * dragged writes here, and [version] tells the other to re-read.
 *
 * Stored as newline-separated identity addresses (`sip:user@domain`) — those can't contain a
 * newline, and they survive the account being re-registered or re-created.
 */
object SkAccountOrder {
    private const val KEY = "sk_account_order"

    /** Bumped on every reorder, so a screen showing the old order knows to rebuild. */
    val version = MutableLiveData(0)

    fun stored(context: Context): List<String> =
        (SkTheme.prefs(context).getString(KEY, "") ?: "")
            .split('\n')
            .filter { it.isNotEmpty() }

    fun save(context: Context, identities: List<String>) {
        SkTheme.prefs(context).edit()
            .putString(KEY, identities.joinToString("\n"))
            .apply()
        version.postValue((version.value ?: 0) + 1)
    }

    /**
     * [items] in 白い熊's order. Anything not in the stored order — a freshly added account — keeps
     * its position relative to the other unknowns and lands at the end, which is where an account
     * the Core has just handed us belongs until it is dragged somewhere.
     */
    fun <T> sorted(context: Context, items: List<T>, identity: (T) -> String): List<T> {
        val order = stored(context)
        if (order.isEmpty()) return items
        return items.sortedBy {
            val index = order.indexOf(identity(it))
            if (index < 0) Int.MAX_VALUE else index
        }
    }
}
