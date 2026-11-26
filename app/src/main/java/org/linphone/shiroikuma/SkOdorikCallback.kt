package org.linphone.shiroikuma

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.tools.Log

/**
 * shiroikuma-rindenwa fork — Odorik's callback, ordered from this app.
 *
 * A callback is a call the provider sets up from its side: its exchange rings a first number, and
 * once that is answered it rings the second and joins the two. Ordered with the line itself as the
 * first number, the first leg arrives **here** as an ordinary incoming SIP call, and the person
 * called sees the CLIP set for the line — the same as on a call placed the usual way. Both legs are
 * billed from the Odorik credit.
 *
 * ## The order
 *
 * One request, `POST https://www.odorik.cz/api/v1/callback`, with `user` = the line number,
 * `password` = the line's SIP password, `caller` and `recipient`. The answer is one line of text:
 * `callback_ordered`, or `error <why>`.
 *
 * Two things were learnt by ordering one (白い熊, 2026-11-17), and are not in Odorik's wiki:
 *  - the line is reached as `*<line>` — a bare line number is accepted and then never rings;
 *  - the first leg then shows `*<line>` as its caller, which is how it is recognised here.
 *
 * The order is accepted blindly: a number the exchange cannot route still answers
 * `callback_ordered`, and nothing rings. So the person's number goes out in the `00<country>…`
 * form, the only one the wiki shows.
 *
 * ## The first leg, when it comes in
 *
 * For [PENDING_MS] after an order, an incoming call on that line from `*<line>` is that order's
 * first leg. Its call-log record is pointed at the person called before anything has drawn it, so
 * the call screen, the notification and the history all show who the call is to — and not the
 * line's own number, which says nothing. The record's call id is kept ([isCallback]) so the
 * history can go on showing it as a call that was placed here.
 *
 * With the first leg sent to another phone ([callerOf]) nothing arrives here, and there is nothing
 * to relabel.
 */
object SkOdorikCallback {
    private const val TAG = "[SK Odorik Callback]"

    private const val URL_CALLBACK = "https://www.odorik.cz/api/v1/callback"
    private const val DOMAIN = "odorik.cz"

    /** Odorik is a Czech provider: a national number with no prefix set on the account is Czech. */
    private const val DEFAULT_PREFIX = "420"
    private const val NATIONAL_DIGITS = 9

    private const val PENDING_MS = 90_000L
    private const val TIMEOUT_MS = 10_000
    private const val AUTO_ANSWER_DELAY_MS = 400L

    /**
     * Apart from the theme's preferences, because it can hold a line's password: an export of the
     * appearance must not carry that. It travels with the accounts instead ([isAccountKey]).
     */
    private const val PREFS_FILE = "sk_odorik"
    private const val KEY_CALLER = "caller|"
    private const val KEY_PASSWORD = "password|"
    private const val KEY_ANSWER = "answer|"
    private const val KEY_CALL_IDS = "callback_call_ids"
    private const val KEPT_CALL_IDS = 300

    private val executor = Executors.newSingleThreadExecutor()

    /** How an order ended. [code] is `OK` or `ERROR:<why>`, as the call door answers it. */
    data class Outcome(val ok: Boolean, val code: String, val message: String)

    private class Prepared(
        val identity: String,
        val line: String,
        val user: String,
        val password: String,
        val caller: String,
        val recipient: String,
        val badge: String
    )

    private class Pending(val identity: String, val line: String, val recipient: String, val at: Long)

    private val pending = ArrayList<Pending>()

    @Volatile
    private var callIds: Map<String, String>? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ---- which accounts ----

    @WorkerThread
    fun isOdorik(account: Account?): Boolean {
        val domain = account?.params?.identityAddress?.domain?.lowercase() ?: return false
        return domain == DOMAIN || domain.endsWith(".$DOMAIN")
    }

    /** By the identity alone, for a screen that has no core at hand. */
    @AnyThread
    fun isOdorik(identity: String): Boolean {
        val domain = identity.substringAfter('@', "").substringBefore(';').substringBefore(':').lowercase()
        return domain == DOMAIN || domain.endsWith(".$DOMAIN")
    }

    /**
     * The account a callback to a row of the history goes through: the row's own when that is an
     * Odorik line, else the selected account when that is one, else none.
     */
    @WorkerThread
    fun accountFor(core: Core, identity: String?): Account? {
        val own = core.accountList.find {
            it.params.identityAddress?.asStringUriOnly() == identity
        }
        return own?.takeIf { isOdorik(it) } ?: core.defaultAccount?.takeIf { isOdorik(it) }
    }

    // ---- what 白い熊 sets per account ----

    /** The number the first leg rings, or empty for the line itself. */
    fun callerOf(context: Context, identity: String): String =
        prefs(context).getString(KEY_CALLER + identity, "").orEmpty()

    fun setCaller(context: Context, identity: String, value: String) =
        prefs(context).edit().putString(KEY_CALLER + identity, value.trim()).apply()

    /** The line's password, for when the SIP stack kept only its hash. */
    fun passwordOf(context: Context, identity: String): String =
        prefs(context).getString(KEY_PASSWORD + identity, "").orEmpty()

    fun setPassword(context: Context, identity: String, value: String) =
        prefs(context).edit().putString(KEY_PASSWORD + identity, value).apply()

    /**
     * Keep [password] for [identity] when that is an Odorik line, at the one moment it is known:
     * while 白い熊 types it to set the account up. Afterwards the SIP stack holds only its hash.
     */
    @AnyThread
    fun capturePassword(context: Context, identity: String, password: String) {
        if (password.isNotEmpty() && isOdorik(identity)) setPassword(context, identity, password)
    }

    // ---- backup: what goes with the accounts, and what goes with the call history ----

    /** The preferences an export reads and an import writes; see the two key tests below. */
    fun backupPrefs(context: Context) = prefs(context)

    /** Per-account settings, the password among them: restored with the accounts. */
    fun isAccountKey(key: String): Boolean =
        key.startsWith(KEY_CALLER) || key.startsWith(KEY_PASSWORD) || key.startsWith(KEY_ANSWER)

    /** Which calls were callbacks: restored with the call history they mark. */
    fun isHistoryKey(key: String): Boolean = key == KEY_CALL_IDS

    fun autoAnswer(context: Context, identity: String): Boolean =
        prefs(context).getBoolean(KEY_ANSWER + identity, false)

    fun setAutoAnswer(context: Context, identity: String, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ANSWER + identity, value).apply()

    // ---- the order ----

    /**
     * [raw] as Odorik's API wants it: `00<country><number>`. A `*…` number (another Odorik line)
     * and anything short (a speed dial) pass as they are. Null when it is not a number at all.
     */
    fun apiNumber(raw: String, prefix: String?): String? {
        val kept = raw.trim().filter { it.isDigit() || it == '+' || it == '*' }
        val digits = kept.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        if (raw.trim().any { it.isLetter() }) return null
        return when {
            kept.startsWith("*") -> "*$digits"
            kept.startsWith("+") -> "00$digits"
            digits.startsWith("00") -> digits
            digits.length == NATIONAL_DIGITS -> "00${prefix?.takeIf { it.isNotEmpty() } ?: DEFAULT_PREFIX}$digits"
            else -> digits
        }
    }

    @WorkerThread
    private fun prepare(context: Context, account: Account?, to: String): Any {
        if (account == null || !isOdorik(account)) {
            return fail("not an Odorik account", context.getString(R.string.sk_callback_error_account))
        }
        val address = account.params.identityAddress
        val identity = address?.asStringUriOnly().orEmpty()
        val line = address?.username.orEmpty()
        if (line.isEmpty()) {
            return fail("not an Odorik account", context.getString(R.string.sk_callback_error_account))
        }
        val recipient = apiNumber(to, account.params.internationalPrefix)
            ?: return fail("not a number", context.getString(R.string.sk_callback_error_number))

        val info = account.findAuthInfo()
        val password = passwordOf(context, identity).ifEmpty { info?.password.orEmpty() }
        if (password.isEmpty()) {
            return fail("no password", context.getString(R.string.sk_callback_error_password))
        }
        val caller = callerOf(context, identity).let { set ->
            if (set.isEmpty()) "*$line" else apiNumber(set, account.params.internationalPrefix) ?: "*$line"
        }
        return Prepared(
            identity,
            line,
            info?.userid?.takeIf { it.isNotEmpty() } ?: line,
            password,
            caller,
            recipient,
            SkAccountNumbers.badgeOf(context, identity)
        )
    }

    private fun fail(why: String, message: String) = Outcome(false, "ERROR:$why", message)

    @WorkerThread
    private fun send(context: Context, order: Prepared): Outcome {
        val answer = try {
            val body = listOf(
                "user" to order.user,
                "password" to order.password,
                "caller" to order.caller,
                "recipient" to order.recipient
            ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
            val connection = URL(URL_CALLBACK).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body.toByteArray()) }
                val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
                    .ifEmpty { "error http_${connection.responseCode}" }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("$TAG Order failed: $e")
            "error network ${e.javaClass.simpleName}"
        }

        // The password never reaches the log; everything else is what explains a silent order.
        SkDiag.log(
            context,
            SkDiagKind.OUTGOING,
            "callback line=${order.line} caller=${order.caller} recipient=${order.recipient} answer=\"$answer\""
        )

        if (!answer.startsWith("error")) {
            synchronized(pending) {
                val now = System.currentTimeMillis()
                pending.removeAll { now - it.at > PENDING_MS }
                pending += Pending(order.identity, order.line, order.recipient, now)
            }
            return Outcome(true, "OK", context.getString(R.string.sk_callback_ordered, order.badge))
        }
        val why = answer.removePrefix("error").trim()
        val message = when {
            why.startsWith("authentication") -> context.getString(R.string.sk_callback_error_auth)
            why.startsWith("network") -> context.getString(R.string.sk_callback_error_network)
            else -> context.getString(R.string.sk_callback_error_other, why)
        }
        return Outcome(false, "ERROR:$why", message)
    }

    /**
     * Order a callback to [to] through the Odorik account [identity] stands for (see
     * [accountFor]). [done] is called on the main thread.
     */
    @AnyThread
    fun order(context: Context, identity: String?, to: String, done: (Outcome) -> Unit) {
        val app = context.applicationContext
        coreContext.postOnCoreThread { core ->
            val prepared = prepare(app, accountFor(core, identity), to)
            if (prepared is Outcome) {
                coreContext.postOnMainThread { done(prepared) }
                return@postOnCoreThread
            }
            executor.execute {
                val outcome = send(app, prepared as Prepared)
                coreContext.postOnMainThread { done(outcome) }
            }
        }
    }

    /**
     * The same for the call door, which has to answer its caller: [wanted] is a 臨 number or an
     * identity, empty for the selected account. Blocks for as long as the request takes.
     */
    @WorkerThread
    fun orderBlocking(context: Context, wanted: String, to: String): Outcome {
        val app = context.applicationContext
        var prepared: Any? = null
        val ready = CountDownLatch(1)
        coreContext.postOnCoreThread { core ->
            try {
                val account = if (wanted.isEmpty()) {
                    core.defaultAccount
                } else {
                    val identity = wanted.toIntOrNull()?.let { SkAccountNumbers.identityOf(app, it) } ?: wanted
                    core.accountList.find { it.params.identityAddress?.asStringUriOnly() == identity }
                }
                prepared = prepare(app, account, to)
            } finally {
                ready.countDown()
            }
        }
        if (!ready.await(3, TimeUnit.SECONDS)) return fail("not ready", "")
        return when (val p = prepared) {
            is Prepared -> send(app, p)
            is Outcome -> p
            else -> fail("not ready", "")
        }
    }

    // ---- the first leg ----

    /**
     * Called for every call state, before anything has drawn the call. An incoming call that is
     * the first leg of an order made here is pointed at the person that order was for.
     */
    @WorkerThread
    fun onCallStateChanged(context: Context, call: Call, state: Call.State) {
        if (state != Call.State.IncomingReceived || call.dir != Call.Dir.Incoming) return
        val from = call.remoteAddress.username.orEmpty()
        // The line it rang on, read from the INVITE: the record names the selected account instead
        // whenever the SDK could not place the To line (SkCallStamps.accountOf).
        val to = SkCallStamps.localAddressOf(call)?.username.orEmpty()
        val order = synchronized(pending) {
            val now = System.currentTimeMillis()
            pending.removeAll { now - it.at > PENDING_MS }
            // The newest first: two orders in a row to one line arrive one after the other, but
            // only the last is still worth waiting for.
            pending.lastOrNull { it.line == to && (from == "*${it.line}" || from == it.line) }
                ?.also { pending.remove(it) }
        } ?: return

        val log = call.callLog
        val person = call.remoteAddress.clone()
        person.displayName = null
        person.username = if (order.recipient.startsWith("00")) {
            "+${order.recipient.removePrefix("00")}"
        } else {
            order.recipient
        }
        log.remoteAddress = person
        log.callId?.takeIf { it.isNotEmpty() }?.let { remember(context, it, person.username.orEmpty()) }
        Log.i("$TAG Incoming call from [$from] is the callback to [${person.username}]")
        SkDiag.log(
            context,
            SkDiagKind.OUTGOING,
            "callback first leg from $from on line ${order.line}, shown as ${person.username} " +
                "callId=${log.callId.orEmpty()}"
        )

        if (autoAnswer(context, order.identity)) {
            coreContext.postOnCoreThreadDelayed({
                if (call.state == Call.State.IncomingReceived) {
                    Log.i("$TAG Answering the callback's first leg")
                    coreContext.answerCall(call, true)
                }
            }, AUTO_ANSWER_DELAY_MS)
        }
    }

    /**
     * Call id → the person that callback was to, one `id<TAB>number` per line, oldest first.
     *
     * The number is kept here because the SIP stack does not keep it: pointing a call's record at
     * someone else holds only while the app runs, and the stored record goes on naming the line
     * (seen on the phone, 2026-11-17 — after a restart the row read `*794586` again). So every
     * record is pointed again when it is read back ([restore]). A line with no number is a call
     * from before this was kept: known to be a callback, not to whom.
     */
    private fun ids(context: Context): Map<String, String> {
        callIds?.let { return it }
        val read = LinkedHashMap<String, String>()
        for (line in prefs(context).getString(KEY_CALL_IDS, "").orEmpty().split('\n')) {
            if (line.isEmpty()) continue
            read[line.substringBefore('\t')] = line.substringAfter('\t', "")
        }
        callIds = read
        return read
    }

    private fun remember(context: Context, callId: String, person: String) {
        val all = LinkedHashMap(ids(context))
        all.remove(callId)
        all[callId] = person
        val kept = all.entries.toList().takeLast(KEPT_CALL_IDS)
        callIds = kept.associate { it.key to it.value }
        prefs(context).edit()
            .putString(KEY_CALL_IDS, kept.joinToString("\n") { "${it.key}\t${it.value}" })
            .apply()
    }

    /**
     * Point a record read back from the call history at the person its callback was to. Called
     * before anything else reads the record; does nothing for any other call.
     */
    @WorkerThread
    fun restore(context: Context, log: org.linphone.core.CallLog) {
        val person = ids(context)[log.callId ?: return]?.takeIf { it.isNotEmpty() } ?: return
        val current = log.remoteAddress
        if (current.username == person) return
        val address = current.clone()
        address.displayName = null
        address.username = person
        log.remoteAddress = address
    }

    /** Whether the call with this id was the first leg of a callback ordered here. */
    @AnyThread
    fun isCallback(context: Context, callId: String?): Boolean =
        !callId.isNullOrEmpty() && ids(context).containsKey(callId)

    @AnyThread
    fun isCallback(call: Call): Boolean = isCallback(coreContext.context, call.callLog.callId)

    /** “Callback → Jana” for a call that is one, [name] untouched for any other. */
    @AnyThread
    fun label(call: Call, name: String?): String? =
        if (name != null && isCallback(call)) {
            coreContext.context.getString(R.string.sk_callback_label, name)
        } else {
            name
        }
}
