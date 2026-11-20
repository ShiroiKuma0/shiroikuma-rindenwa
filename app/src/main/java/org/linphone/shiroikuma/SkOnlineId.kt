package org.linphone.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.text.HtmlCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.tools.Log

/**
 * shiroikuma-rindenwa fork — who an unknown caller is, looked up online while the call rings.
 * Asked for by 白い熊 on 2026-11-20.
 *
 * A caller who is in no address book is only a number. For a Czech number the public page
 * `https://www.tellows.cz/num/<national number>` often says who it is (“Jméno volajícího:
 * T-Mobile”), and that name is then shown where the number would stand — on the incoming-call
 * screen and on the first line of the call's rows in the history — with an **ID** mark beside it,
 * so that it is never taken for a name out of the address book.
 *
 * ## When
 *
 * Only when a call **rings in**, only for a number 連絡先 does not hold, only for a Czech number
 * (`+420` and nine digits; other countries get a source of their own later), and only with the
 * switch on the 臨電話 UI page on ([enabled]). The history never asks by itself: a row carries a
 * name because that number rang here once and was looked up then.
 *
 * The call is not held up for it. It rings at once under its number; the name takes the number's
 * place when the answer comes ([addListener]), and an answer slower than [TIMEOUT_MS] is given up.
 *
 * ## The store
 *
 * The preferences file `sk_online_ids`, one JSON string per number, keyed `tel:<international
 * digits>` as the blocked numbers are. The value holds `name` (absent when the source knew
 * nothing), `src` (which source said so) and `at` (when, in milliseconds). **Renaming or
 * re-meaning a key breaks old exports** — the file travels with the call history as
 * `online_ids.json` (`SkEximport`).
 *
 * **Every ring asks again** (白い熊, 2026-11-20): a number nobody had reported yesterday may well
 * be reported today, and a name can change. The store is what the history draws from, and what
 * the call screen shows for the second or two the fresh answer takes — a name found before stands
 * there at once and is replaced if the answer differs. A fresh “nothing known” takes the name
 * away; a failed request changes nothing.
 *
 * ## The page
 *
 * The name is the text of `<span class="callerId">` — the hook is the markup, not the Czech
 * wording around it. A page without that span is a number tellows knows nothing about. This is a
 * page meant for people, read by a program: when its markup changes, names simply stop arriving,
 * and the diagnostic log (kind “calls”) says `online id … no name` for every number.
 */
object SkOnlineId {
    private const val TAG = "[Sk Online Id]"

    private const val PREFS_FILE = "sk_online_ids"
    private const val KEY_ENABLED = "sk_online_id_enabled"

    private const val TEL = "tel:"
    private const val NAME = "name"
    private const val SOURCE = "src"
    private const val AT = "at"

    private const val SOURCE_TELLOWS = "tellows"
    private const val URL_TELLOWS = "https://www.tellows.cz/num/"

    /** The one country the one source covers. */
    private const val CZECH_PREFIX = "420"
    private const val CZECH_NATIONAL_DIGITS = 9

    private const val TIMEOUT_MS = 4000

    /** A page is some 230 kB and the name stands in its first tenth; never read without end. */
    private const val MAX_PAGE_CHARS = 400_000

    /** A name is a few words. Anything longer is the page having changed under us. */
    private const val MAX_NAME_CHARS = 60

    private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"

    private val CALLER_ID = Regex("<span[^>]*class=\"callerId\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sk-online-id").apply { isDaemon = true }
    }

    /** The numbers being asked about right now. */
    private val asking: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @Volatile
    private var names: Map<String, String>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** For [SkEximport]: the whole file is the store. */
    fun backupPrefs(context: Context): SharedPreferences = prefs(context)

    fun isEntryKey(key: String): Boolean = key.startsWith(TEL)

    // ------------------------------------------------------------- the switch

    fun enabled(context: Context): Boolean = SkTheme.prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        changed()
    }

    // ------------------------------------------------------------- listeners

    /**
     * Told whenever a name arrives or the store changes — from any thread, so a listener that
     * draws posts to its own.
     */
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() {
        for (listener in listeners) listener()
    }

    /** After an import wrote the file behind our back. */
    @AnyThread
    fun reload() {
        names = null
        changed()
    }

    // ------------------------------------------------------------- reading

    private fun names(context: Context): Map<String, String> =
        names ?: prefs(context).all.mapNotNull { (key, value) ->
            if (!isEntryKey(key) || value !is String) return@mapNotNull null
            val name = runCatching { JSONObject(value) }.getOrNull()?.optString(NAME).orEmpty()
            if (name.isEmpty()) null else key to name
        }.toMap().also { names = it }

    /** What a number is kept under, or null when [raw] is not a phone number. */
    @AnyThread
    fun keyOf(raw: String?): String? = SkNumbers.international(raw.orEmpty())?.let { TEL + it }

    /**
     * The name found online for [key], or empty: nothing was found, the number never rang here,
     * or the switch is off — which hides the names already found as well as stopping the asking.
     */
    @AnyThread
    fun nameOf(context: Context, key: String?): String {
        if (key.isNullOrEmpty() || !enabled(context)) return ""
        return names(context)[key].orEmpty()
    }

    // ------------------------------------------------------------- asking

    /**
     * Called for every call state, behind the blocked-number door and the callback's repointing.
     * A call that rings in from a Czech number nobody here knows is looked up, off this thread.
     */
    @WorkerThread
    fun onCallStateChanged(call: Call, state: Call.State) {
        if (state != Call.State.IncomingReceived || call.dir != Call.Dir.Incoming) return
        val context = coreContext.context
        if (!enabled(context)) return
        // The first leg of a callback ordered here comes from the provider, not from a caller.
        if (SkOdorikCallback.isCallback(call)) return

        val address = call.remoteAddress
        val key = keyOf(address.username) ?: return
        if (!isCovered(key)) return

        val friend = coreContext.core.findFriend(address)
        if (friend != null && coreContext.contactsManager.isContactAvailable(friend)) return

        lookUp(context, key) {}
    }

    /** How a lookup ended. */
    enum class Outcome { FOUND, NOTHING, FAILED }

    /** Whether there is a source to ask about [key] at all: today, a Czech number. */
    @AnyThread
    fun isCovered(key: String?): Boolean =
        key != null && key.startsWith(TEL + CZECH_PREFIX) &&
            key.length == TEL.length + CZECH_PREFIX.length + CZECH_NATIONAL_DIGITS

    /**
     * Asks about [key] now, whatever is stored — the history menu's “ID online”, and every ring.
     * [onDone] is told how it ended, on the lookup's own thread; the listeners have by then been
     * told of a name that came or went. A number already being asked about is not asked twice:
     * the second asker simply hears nothing, and sees the row change with the first one's answer.
     */
    @AnyThread
    fun lookUp(context: Context, key: String, onDone: (Outcome) -> Unit) {
        if (!isCovered(key) || !asking.add(key)) return
        val national = key.removePrefix(TEL + CZECH_PREFIX)
        val app = context.applicationContext
        executor.execute {
            val outcome = try {
                ask(app, key, national)
            } finally {
                asking.remove(key)
            }
            onDone(outcome)
        }
    }

    @WorkerThread
    private fun ask(context: Context, key: String, national: String): Outcome {
        val page = try {
            fetch(URL_TELLOWS + national)
        } catch (e: Exception) {
            Log.w("$TAG Asking about [$key] failed: $e")
            SkDiag.log(context, SkDiagKind.CALLS, "online id $key failed ${e.javaClass.simpleName}")
            return Outcome.FAILED
        }
        val name = nameIn(page)

        val value = JSONObject().put(SOURCE, SOURCE_TELLOWS).put(AT, System.currentTimeMillis())
        if (name.isNotEmpty()) value.put(NAME, name)
        prefs(context).edit().putString(key, value.toString()).apply()

        Log.i("$TAG [$key] is [${name.ifEmpty { "not known" }}]")
        SkDiag.log(context, SkDiagKind.CALLS, "online id $key " + if (name.isEmpty()) "no name" else "name=\"$name\"")

        val known = names(context)
        if (known[key].orEmpty() != name) {
            names = if (name.isEmpty()) known - key else known + (key to name)
            changed()
        }
        return if (name.isEmpty()) Outcome.NOTHING else Outcome.FOUND
    }

    /** The page's text; throws on anything but a page. */
    @WorkerThread
    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Language", "cs")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("http $code")
            return connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                val text = StringBuilder()
                while (text.length < MAX_PAGE_CHARS) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    text.append(buffer, 0, read)
                }
                text.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    /** The caller's name on a tellows page, or empty when the page gives none. */
    internal fun nameIn(page: String): String {
        val raw = CALLER_ID.find(page)?.groupValues?.get(1) ?: return ""
        val name = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()
        return if (name.length > MAX_NAME_CHARS) "" else name
    }
}
