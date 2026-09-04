package org.linphone.shiroikuma

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * shiroikuma-rindenwa fork — the automation gate for [SkStateExportReceiver] and for the data door
 * in `org.linphone.shiroikuma.automation`.
 *
 * ## What changed in contract v2 (白い熊, 2026-09-04)
 *
 * v1 shipped this app **closed**: [enabled] defaulted to false and a caller also had to present a
 * 48-character secret 白い熊 had pasted from this page into the caller's settings. That is the wrong
 * shape for where the family is going — **a pasted secret cannot survive a wipe**, and the case the
 * whole automation family now exists to serve is 応用管理 restoring apps *and their data* onto a
 * clean phone, where nothing has been configured and nobody has pasted anything.
 *
 * So [enabled] defaults to **true**, and [requireToken] is a new, separate switch defaulting to
 * **false**. The token still exists, still regenerates, still never leaves the phone — it is simply
 * opt-in now.
 *
 * ## Idempotent about the token — required, not a nicety
 *
 * **A token handed to an app that does not require one is IGNORED, never an error.** Tokens live in
 * task arguments and workspace variables that outlive the setting they were pasted for; a caller
 * still sending one — because it was configured last year, or because another app on the batch does
 * want one — must be served. Refusing it would turn "白い熊 turned a switch off" into "half the
 * batch mysteriously fails", which is precisely the friction the switch exists to remove.
 *
 * ## Device-local by design
 *
 * These live in their own preference file, not in the default SharedPreferences that [SkEximport]
 * dumps, so no automation setting — least of all the token — can travel inside an export ZIP.
 */
object SkAutomation {
    private const val PREFS_FILE = "sk_automation" // never exported
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /**
     * Whether this app answers automation at all. **Default true** since contract v2.
     *
     * Kept as a switch rather than removed: it is the only way to close this one app off, and a
     * feature that can be turned on but never off is one 白い熊 cannot retreat from.
     */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /**
     * `commit()`, never `apply()` — and this is load-bearing since v2 flipped the default.
     *
     * Under v1 a write lost to a kill fell back to `false`, which merely meant "still closed" and
     * 白い熊 toggled it again. Now the fallback is **true**: an `apply()` that never reaches disk
     * before 応用管理 `SIGKILL`s this process leaves the app ANSWERING automation that 白い熊 just
     * turned off. The switch must be on disk before the toggle returns.
     */
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).commit()
    }

    /** Whether a caller must present [token]. **Default false** — the token is opt-in now. */
    fun requireToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    /** `commit()` for the same reason as [setEnabled]: the permissive value is the default. */
    fun setRequireToken(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, value).commit()
    }

    /** The shared secret; generated on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateToken(context)

    fun regenerateToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        // Also commit(): a token handed to 白い熊 to paste, then lost to a kill, would refuse
        // every caller using the copy he is holding.
        prefs(context).edit().putString(KEY_TOKEN, token).commit()
        return token
    }

    /**
     * True when the caller's token matches the stored secret (constant-time). Only consulted when
     * [requireToken] is on; the switch is checked separately so "disabled" and "bad token" stay
     * distinct failures — they debug differently.
     */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /**
     * The whole gate, in the one place every entry point must ask.
     *
     * Returns null to proceed, or the exact `ERROR:` string to answer with. Written as one function
     * so no receiver, provider or service can implement the two checks in a subtly different order —
     * which is how "disabled" and "bad token" drift apart across forty-two apps.
     *
     * **A token supplied while [requireToken] is off is IGNORED, never an error.**
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** `80922d8c…4c49a87c` — the settings row never shows the whole secret. */
    fun abbreviate(token: String): String =
        if (token.length <= 20) token else token.take(8) + "…" + token.takeLast(8)
}
