package org.linphone.shiroikuma

import android.content.Context
import androidx.lifecycle.MutableLiveData

/**
 * shiroikuma-rindenwa fork — the number 白い熊 gives each SIP account: 臨0, 臨1, 臨2 …
 *
 * A call made through this app is marked 臨N wherever it shows — on the row in the call history
 * here, on the badge that dials it in the address book, in the swipe that calls through it. So
 * the number has to mean the same account tomorrow as it does today, which is why it is **set**,
 * per account, and not taken from the account's position: a position changes when an account is
 * added, removed or dragged, and every swipe and contact pointed at 臨1 would quietly start
 * calling from another line.
 *
 * Stored like [SkAccountOrder]: in the fork's own preferences, keyed by the identity address
 * (`sip:user@domain`), one `identity<TAB>number` per line. An account without a number shows a
 * bare 臨.
 */
object SkAccountNumbers {
    private const val KEY = "sk_account_numbers"

    const val MARK = "臨"

    /** Bumped on every change, so a screen showing the old numbers knows to redraw. */
    val version = MutableLiveData(0)

    /** Read on every row of the call history, so the parsed map is kept until it changes. */
    @Volatile
    private var cache: Map<String, Int>? = null

    private fun all(context: Context): Map<String, Int> {
        cache?.let { return it }
        val read = (SkTheme.prefs(context).getString(KEY, "") ?: "")
            .split('\n')
            .mapNotNull { line ->
                val parts = line.split('\t')
                val number = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                parts[0] to number
            }
            .toMap()
        cache = read
        return read
    }

    // ---- colours: each number's badge has its own ground and text colour ----

    private const val UNSET = 1 // not a colour anyone can pick: alpha 0, but not transparent black

    private fun groundKey(number: Int) = "sk_rin_ground_$number"

    private fun textKey(number: Int) = "sk_rin_text_$number"

    /** The colour 白い熊 set for 臨[number]'s ground, or null when it follows the default. */
    fun ownGround(context: Context, number: Int?): Int? = number?.let {
        SkTheme.prefs(context).getInt(groundKey(it), UNSET).takeIf { c -> c != UNSET }
    }

    fun ownText(context: Context, number: Int?): Int? = number?.let {
        SkTheme.prefs(context).getInt(textKey(it), UNSET).takeIf { c -> c != UNSET }
    }

    /**
     * Ground and text colour of the badge for [number] (null = an account without a number):
     * its own where 白い熊 set one, else what the sister dialer's theme says for 臨 badges, else
     * yellow with blue text.
     */
    fun colors(context: Context, number: Int?): Pair<Int, Int> = Pair(
        ownGround(context, number) ?: SkDenwaStyle.color("color.rin_badge") ?: SkRoles.color(context, "rin_badge_ground"),
        ownText(context, number) ?: SkDenwaStyle.color("color.rin_badge_text") ?: SkRoles.color(context, "rin_badge_text"),
    )

    fun setGround(context: Context, number: Int, color: Int?) = setColor(context, groundKey(number), color)

    fun setText(context: Context, number: Int, color: Int?) = setColor(context, textKey(number), color)

    private fun setColor(context: Context, key: String, color: Int?) {
        val edit = SkTheme.prefs(context).edit()
        if (color == null) edit.remove(key) else edit.putInt(key, color)
        edit.apply()
        version.postValue((version.value ?: 0) + 1)
    }

    /** The number set for [identity], or null when it has none. */
    fun numberOf(context: Context, identity: String?): Int? =
        if (identity.isNullOrEmpty()) null else all(context)[identity]

    /** The identity carrying [number], or null. */
    fun identityOf(context: Context, number: Int): String? =
        all(context).entries.firstOrNull { it.value == number }?.key

    /** 臨N for [identity], or a bare 臨 when it has no number. */
    fun badgeOf(context: Context, identity: String?): String =
        numberOf(context, identity)?.let { "$MARK$it" } ?: MARK

    /**
     * Give [identity] the number [number] (null clears it). Refused — false — when another
     * account already carries it: two accounts sharing a number would make "dial through 臨1"
     * mean whichever happened to be found first.
     */
    fun set(context: Context, identity: String, number: Int?): Boolean {
        val map = all(context).toMutableMap()
        if (number != null && map.any { it.value == number && it.key != identity }) return false
        if (number == null) map.remove(identity) else map[identity] = number
        SkTheme.prefs(context).edit()
            .putString(KEY, map.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            .apply()
        cache = map
        version.postValue((version.value ?: 0) + 1)
        return true
    }
}
