package org.linphone.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.util.Collections
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.Reason
import org.linphone.core.tools.Log

/**
 * shiroikuma-rindenwa fork — the blocked numbers: the register, and the door a blocked caller is
 * turned away at. Asked for by 白い熊 on 2026-11-19, after 電話's block on the call screen and in
 * the recents.
 *
 * 電話 keeps its list in Android's own `BlockedNumberContract`, which only the default dialer may
 * read or write. 臨電話 is not that, and a SIP caller is not always a number, so the register is
 * ours.
 *
 * ## The register
 *
 * The preferences file `sk_blocked_numbers`, one JSON string per blocked caller. The key is what a
 * caller is recognised by ([keyOf]):
 *
 * - `tel:<digits>` for a number, in its international digits — so `+420-601-123-456`,
 *   `00420601123456` and a bare `601123456` are one entry (a bare nine-digit number is read as
 *   Czech, as everywhere else: [SkNumbers]);
 * - `sip:<user>@<domain>`, lower-cased, for a caller whose user part is not a number.
 *
 * A caller who withholds the number has neither, and cannot be blocked.
 *
 * The value holds `raw` (the number or address as it was blocked, for showing), `name` (what the
 * caller was called at that moment, if anything) and `at` (when, in milliseconds). **Renaming or
 * re-meaning a key breaks old exports** — the file travels in the export/import as
 * `blocked_numbers.json` (`SkEximport`, category `blocked`).
 *
 * ## Turning a blocked caller away
 *
 * [rejectIfBlocked] runs first in `CoreContext`'s call-state listener. A blocked call is marked
 * ([isRejecting]) so that nothing announces it — no call screen ([SkRinger]), no notification, no
 * Telecom registration, no ringer — and is declined the moment the listeners have all been heard.
 * The decline is posted rather than made on the spot: declining inside the callback would hand the
 * listeners behind this one an `End` before the `IncomingReceived` it belongs to.
 *
 * The call stays in the history, as a call would in 電話, and its stamp says “Blocked”.
 */
object SkBlocked {
    private const val TAG = "[Sk Blocked]"

    private const val PREFS_FILE = "sk_blocked_numbers"

    private const val TEL = "tel:"
    private const val SIP = "sip:"

    private const val RAW = "raw"
    private const val NAME = "name"
    private const val AT = "at"

    /** User parts that stand for “the caller did not say”. */
    private val WITHHELD = setOf("anonymous", "unknown", "restricted", "unavailable", "private")

    /** One line of the register. */
    data class Entry(val key: String, val raw: String, val name: String, val at: Long) {
        /** The number in the house reading, or the SIP address as it is. */
        val shown: String
            get() = if (key.startsWith(TEL)) SkNumbers.format("+" + key.removePrefix(TEL)) else key.removePrefix(SIP)
    }

    @Volatile
    private var keys: Set<String>? = null

    /** The calls being turned away right now, by [callKey]. */
    private val rejecting: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** For [SkEximport]: the whole file is the register. */
    fun backupPrefs(context: Context): SharedPreferences = prefs(context)

    fun isEntryKey(key: String): Boolean = key.startsWith(TEL) || key.startsWith(SIP)

    /** After an import wrote the file behind our back. */
    @AnyThread
    fun reload() {
        keys = null
    }

    private fun keys(context: Context): Set<String> =
        keys ?: prefs(context).all.keys.filter(::isEntryKey).toSet().also { keys = it }

    // ------------------------------------------------------------- who is who

    /**
     * What something typed or stored is recognised by: a number, `user@domain`, or a whole SIP
     * URI. Null when it is none of those.
     */
    @AnyThread
    fun keyOf(typed: String): String? {
        val text = typed.trim().removePrefix("sip:").removePrefix("sips:").removePrefix("tel:")
        if (text.isEmpty()) return null
        val user = text.substringBefore('@').substringBefore(';')
        SkNumbers.international(user)?.let { return TEL + it }
        val domain = text.substringAfter('@', "").substringBefore(';').substringBefore(':')
        if (domain.isEmpty() || user.isEmpty() || user.lowercase() in WITHHELD) return null
        return SIP + user.lowercase() + "@" + domain.lowercase()
    }

    /** What a caller's address is recognised by; null for a withheld number. */
    @AnyThread
    fun keyOf(address: Address?): String? {
        address ?: return null
        val user = address.username.orEmpty()
        if (user.isEmpty() || user.lowercase() in WITHHELD) return null
        SkNumbers.international(user)?.let { return TEL + it }
        val domain = address.domain.orEmpty()
        if (domain.isEmpty()) return null
        return SIP + user.lowercase() + "@" + domain.lowercase()
    }

    // ------------------------------------------------------------- the register

    @AnyThread
    fun isBlocked(context: Context, key: String?): Boolean = !key.isNullOrEmpty() && key in keys(context)

    @AnyThread
    fun count(context: Context): Int = keys(context).size

    /** Newest first. */
    @AnyThread
    fun entries(context: Context): List<Entry> =
        prefs(context).all.mapNotNull { (key, value) ->
            if (!isEntryKey(key) || value !is String) return@mapNotNull null
            val json = runCatching { JSONObject(value) }.getOrNull() ?: JSONObject()
            Entry(key, json.optString(RAW), json.optString(NAME), json.optLong(AT))
        }.sortedByDescending { it.at }

    /** Block [key]. False when it was blocked already, in which case nothing is rewritten. */
    @AnyThread
    fun add(context: Context, key: String, raw: String = "", name: String = ""): Boolean {
        if (!isEntryKey(key) || isBlocked(context, key)) return false
        val value = JSONObject().put(AT, System.currentTimeMillis())
        if (raw.isNotEmpty()) value.put(RAW, raw)
        if (name.isNotEmpty()) value.put(NAME, name)
        prefs(context).edit().putString(key, value.toString()).apply()
        keys = keys(context) + key
        Log.i("$TAG Blocked [$key]")
        SkDiag.log(context, SkDiagKind.CALLS, "blocked $key")
        return true
    }

    @AnyThread
    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
        keys = keys(context) - key
        Log.i("$TAG Unblocked [$key]")
        SkDiag.log(context, SkDiagKind.CALLS, "unblocked $key")
    }

    // ------------------------------------------------------------- the door

    private fun callKey(call: Call): String {
        val callId = call.callLog.callId
        return if (callId.isNullOrEmpty()) call.remoteAddress.asStringUriOnly() else callId
    }

    /** Whether [call] is one being turned away — nothing is to announce it. */
    @AnyThread
    fun isRejecting(call: Call): Boolean = rejecting.isNotEmpty() && rejecting.contains(callKey(call))

    /**
     * Called first for every call state. True when the call is from a blocked caller: it has been
     * marked, its ringer stopped and its decline posted, and the caller of this skips the rest of
     * what it does for an incoming call.
     */
    @WorkerThread
    fun rejectIfBlocked(core: Core, call: Call, state: Call.State): Boolean {
        val id = callKey(call)
        if (state == Call.State.Released) {
            // Left marked until the very end, so a blocked call goes as quietly as it came.
            return rejecting.remove(id)
        }
        if (rejecting.contains(id)) return true
        if (state != Call.State.IncomingReceived || call.dir != Call.Dir.Incoming) return false

        val context = coreContext.context
        val key = keyOf(call.remoteAddress)
        if (!isBlocked(context, key)) return false

        rejecting.add(id)
        Log.i("$TAG Incoming call from blocked [$key], declining it unannounced")
        SkDiag.log(context, SkDiagKind.CALLS, "incoming from ${call.remoteAddress.asStringUriOnly()} is blocked, declined")
        if (core.callsNb == 1) {
            // With a call already up the SDK does not ring for this one, and stopping the ringer
            // would silence another call that is ringing honestly.
            core.stopRinging()
        }
        SkCallStamps.onBlocked(call)
        coreContext.postOnCoreThread {
            if (call.state == Call.State.IncomingReceived || call.state == Call.State.IncomingEarlyMedia) {
                call.decline(Reason.Declined)
            }
        }
        return true
    }
}
