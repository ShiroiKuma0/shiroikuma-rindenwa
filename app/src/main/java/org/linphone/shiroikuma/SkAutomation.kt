package org.linphone.shiroikuma

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * shiroikuma-rindenwa fork — the automation gate for [SkStateExportReceiver].
 *
 * The family model (renrakusaki's Config, 自由作業盤's AutomationAuth): a master switch that is
 * OFF until 白い熊 turns it on, plus a shared secret every automation broadcast must carry.
 *
 * Device-local by design — these live in their own preference file, not in the default
 * SharedPreferences that [SkEximport] dumps, so the token can never travel inside an export ZIP.
 */
object SkAutomation {
    private const val PREFS_FILE = "sk_automation" // never exported
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** The shared secret; generated on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateToken(context)

    fun regenerateToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    /**
     * True when the caller's token matches the stored secret (constant-time). The switch is
     * checked separately so "disabled" and "bad token" stay distinct failures — they debug
     * differently.
     */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — the settings row never shows the whole secret. */
    fun abbreviate(token: String): String =
        if (token.length <= 20) token else token.take(8) + "…" + token.takeLast(8)
}
