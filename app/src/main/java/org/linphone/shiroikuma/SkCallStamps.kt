package org.linphone.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — what a call was made through, frozen when it was made.
 *
 * The SDK's call log keeps who, when, how long and whether it connected. Everything else about a
 * call used to be worked out afresh on every read from today's settings: give 臨1 to another
 * account, or repaint its badge, and last year's calls changed with it. A stamp is the answer
 * (白い熊, 2026-11-18, as the sister dialer does for its SIMs): the badge as it looked, the account
 * and its server, and what only the live call knows and the SDK throws away afterwards — how long
 * it rang, how it ended and with which SIP answer, the codec, whether the media was encrypted, the
 * network the phone was on, the audio route, the quality, the other end's software. A row with a
 * stamp is drawn from it, and [SkCallStampDialog] shows the rest.
 *
 * An absent JSON key means "not known"; nothing is ever stored empty.
 */
data class SkCallStamp(
    val source: String = SOURCE_LOG,
    /** The N of 臨N, or -1 when the account had no number — the stamp then carries no badge. */
    val number: Int = -1,
    val ground: Int = 0,
    val text: Int = 0,
    /** `sip:user@domain` of the account the call went through. */
    val identity: String = "",
    /** The account's registrar or proxy, `host` or `host:port`. */
    val server: String = "",
    /** UDP, TCP, TLS or DTLS. */
    val transport: String = "",
    /** One of the END_* codes. */
    val end: String = "",
    /** The SIP answer that ended a call that never came about, 0 when there was none. */
    val sipCode: Int = 0,
    val sipPhrase: String = "",
    /** How long it rang before it was answered or given up, -1 unknown. */
    val ringMs: Long = -1,
    /** Audio codec as negotiated, e.g. "opus", and its clock rate in Hz. */
    val codec: String = "",
    val codecRate: Int = 0,
    /** One of the ENC_* codes; "" when the media never ran. */
    val encryption: String = "",
    /** One of the NET_* codes. */
    val network: String = "",
    /** The Wi-Fi network's or the mobile operator's name, where the system gives one. */
    val networkName: String = "",
    val vpn: Boolean = false,
    /** One of the ROUTE_* codes: where the sound went when the call ended. */
    val route: String = "",
    /** The SDK's own rating of the call, 0–5; -1 unknown. */
    val quality: Float = -1f,
    /** What the other end said it was running. */
    val agent: String = "",
) {
    val hasBadge get() = number >= 0

    val isFromCall get() = source == SOURCE_CALL

    fun toJson(): JSONObject = JSONObject().apply {
        put("src", source)
        if (number >= 0) {
            put("n", number)
            put("color", ground)
            put("text", text)
        }
        if (identity.isNotEmpty()) put("id", identity)
        if (server.isNotEmpty()) put("srv", server)
        if (transport.isNotEmpty()) put("tr", transport)
        if (end.isNotEmpty()) put("end", end)
        if (sipCode > 0) put("sip", sipCode)
        if (sipPhrase.isNotEmpty()) put("phrase", sipPhrase)
        if (ringMs >= 0) put("ring", ringMs)
        if (codec.isNotEmpty()) put("codec", codec)
        if (codecRate > 0) put("rate", codecRate)
        if (encryption.isNotEmpty()) put("enc", encryption)
        if (network.isNotEmpty()) put("net", network)
        if (networkName.isNotEmpty()) put("net_name", networkName)
        if (vpn) put("vpn", true)
        if (route.isNotEmpty()) put("route", route)
        if (quality >= 0f) put("q", quality.toDouble())
        if (agent.isNotEmpty()) put("ua", agent)
    }

    companion object {
        /** Taken while the call was live. Nothing weaker ever replaces it. */
        const val SOURCE_CALL = "call"

        /** Worked out afterwards from the call log and the badge of that moment. */
        const val SOURCE_LOG = "log"

        const val END_LOCAL = "local"
        const val END_REMOTE = "remote"
        const val END_CANCELLED = "cancelled"
        const val END_BUSY = "busy"
        const val END_DECLINED = "declined"
        const val END_NO_ANSWER = "no_answer"
        const val END_FAILED = "failed"
        const val END_MISSED = "missed"
        const val END_DECLINED_HERE = "declined_here"
        const val END_ELSEWHERE = "elsewhere"

        /** Turned away unannounced because the caller is in the blocked numbers ([SkBlocked]). */
        const val END_BLOCKED = "blocked"

        const val ENC_NONE = "none"
        const val ENC_SRTP = "srtp"
        const val ENC_ZRTP = "zrtp"
        const val ENC_DTLS = "dtls"

        const val NET_WIFI = "wifi"
        const val NET_MOBILE = "mobile"
        const val NET_ETHERNET = "ethernet"
        const val NET_OTHER = "other"

        const val ROUTE_EARPIECE = "earpiece"
        const val ROUTE_SPEAKER = "speaker"
        const val ROUTE_BLUETOOTH = "bluetooth"
        const val ROUTE_HEADSET = "headset"
        const val ROUTE_HEARING_AID = "hearing_aid"
        const val ROUTE_OTHER = "other"

        fun fromJson(json: JSONObject) = SkCallStamp(
            source = json.optString("src", SOURCE_LOG),
            number = json.optInt("n", -1),
            ground = json.optInt("color", 0),
            text = json.optInt("text", 0),
            identity = json.optString("id"),
            server = json.optString("srv"),
            transport = json.optString("tr"),
            end = json.optString("end"),
            sipCode = json.optInt("sip", 0),
            sipPhrase = json.optString("phrase"),
            ringMs = json.optLong("ring", -1),
            codec = json.optString("codec"),
            codecRate = json.optInt("rate", 0),
            encryption = json.optString("enc"),
            network = json.optString("net"),
            networkName = json.optString("net_name"),
            vpn = json.optBoolean("vpn", false),
            route = json.optString("route"),
            quality = json.optDouble("q", -1.0).toFloat(),
            agent = json.optString("ua"),
        )
    }
}

/**
 * The store of [SkCallStamp]s and the recorder that fills it.
 *
 * **Key: the call's SIP Call-ID**, which the SDK's own call log carries and which therefore
 * survives an export and import of the history database; a record without one is keyed by its
 * start time plus the other party. Kept in a preferences file of its own, one JSON string per
 * call, so it travels with the call history in the export exactly as the callback marks do.
 *
 * **Where a stamp comes from.** [onCallStateChanged], driven from `CoreContext`'s call-state
 * listener, follows every call from its first state: account, badge and network are frozen there,
 * codec, encryption and audio route are re-read whenever the media runs, and the stamp is written
 * when the call ends. A call nobody watched — everything from before this existed — is stamped
 * from its log record with the badge of that moment ([ensure], and once for the whole log in
 * [stampWholeLog]), so that at least the badge stops following later changes.
 */
object SkCallStamps {
    private const val TAG = "[Sk Call Stamps]"
    private const val PREFS_FILE = "sk_call_stamps"

    /** Not a call: set once the whole log has been stamped. Never exported. */
    private const val KEY_WHOLE_LOG_DONE = "_whole_log_stamped"

    /** Not a call: set once [repairIncomingAccounts] has run. Never exported. */
    private const val KEY_INCOMING_REPAIRED = "_incoming_accounts_repaired"

    private const val UNKNOWN_SSID = "<unknown ssid>"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** For the export and the import, which copy every call's entry. */
    fun backupPrefs(context: Context) = prefs(context)

    fun isStampKey(key: String): Boolean = !key.startsWith("_")

    @Volatile
    private var cache: ConcurrentHashMap<String, SkCallStamp>? = null

    private fun all(context: Context): ConcurrentHashMap<String, SkCallStamp> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val read = ConcurrentHashMap<String, SkCallStamp>()
            for ((key, value) in prefs(context).all) {
                if (!isStampKey(key) || value !is String) continue
                try {
                    read[key] = SkCallStamp.fromJson(JSONObject(value))
                } catch (e: Exception) {
                    Log.w("$TAG Dropping an unreadable stamp for [$key]: $e")
                }
            }
            cache = read
            return read
        }
    }

    /** An import wrote stamps underneath us: read the file again on the next use. */
    @AnyThread
    fun reload() {
        cache = null
    }

    // ------------------------------------------------------------------ keys

    @WorkerThread
    fun keyOf(log: CallLog): String {
        val callId = log.callId
        return if (callId.isNullOrEmpty()) {
            "${log.startDate}|${log.remoteAddress.asStringUriOnly()}"
        } else {
            callId
        }
    }

    @AnyThread
    fun get(context: Context, key: String): SkCallStamp? = all(context)[key]

    @AnyThread
    fun remove(context: Context, key: String) {
        if (all(context).remove(key) != null) {
            prefs(context).edit().remove(key).apply()
        }
    }

    /** Store [stamp] unless a stamp taken from the live call is already there. */
    private fun put(context: Context, key: String, stamp: SkCallStamp, editor: SharedPreferences.Editor? = null) {
        val map = all(context)
        if (!stamp.isFromCall && map[key]?.isFromCall == true) return
        map[key] = stamp
        val json = stamp.toJson().toString()
        if (editor != null) editor.putString(key, json) else prefs(context).edit().putString(key, json).apply()
    }

    // ------------------------------------------------------------------ calls nobody watched

    /**
     * The stamp of [log], made from the record itself when it has none: the account it names and
     * the badge that account carries right now.
     */
    @WorkerThread
    fun ensure(context: Context, log: CallLog, editor: SharedPreferences.Editor? = null): SkCallStamp {
        val key = keyOf(log)
        get(context, key)?.let { return it }
        // Its call is still being followed: that stamp is on its way, and knows the line.
        liveStampOf(log)?.let { return it }
        val stamp = withBadge(context, SkCallStamp(source = SkCallStamp.SOURCE_LOG), identityOf(log.localAddress))
        put(context, key, stamp, editor)
        return stamp
    }

    /**
     * Once, the first time the history is read: stamp every record there is, not only the ones
     * that happen to be listed, so the badges of that day are frozen for all of them before a
     * colour or a number changes.
     */
    @WorkerThread
    fun stampWholeLog(context: Context, core: Core) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_WHOLE_LOG_DONE, false)) return
        val editor = prefs.edit()
        var count = 0
        for (log in core.callLogs) {
            if (get(context, keyOf(log)) == null) {
                ensure(context, log, editor)
                count++
            }
        }
        editor.putBoolean(KEY_WHOLE_LOG_DONE, true).apply()
        Log.i("$TAG Stamped the whole call log once: [$count] record(s)")
    }

    // ------------------------------------------------------------------ the repair of 2026-11-26

    private val ODORIK_TAIL = Regex("^[+0-9 ()-]+[>›]([0-9]{2,})$")

    /**
     * Once: give the incoming calls stamped before +063 the account they really rang on.
     *
     * Until then an incoming call was stamped with the account the SDK's record names, which is
     * the account that was selected at that moment whenever the SDK could not place the `To` line
     * ([accountOf]). The record still holds one piece of evidence of the line: Odorik sends the
     * caller as `"<number>›<tail>"`, the tail being the label that line carries at Odorik — on
     * 白い熊's lines the last digits of one of the numbers the account is named after
     * (`601-123-456 (910-123-456 Odorik)`). Two lines may share a tail, and then it says nothing.
     * A stamp is moved only when **exactly one** account's name carries a number ending in that
     * tail and it is another account than the stamp's; the badge is then that account's of today,
     * the best that is known. A call without a tail — a callback's first leg, another provider —
     * is left as it is.
     */
    @WorkerThread
    fun repairIncomingAccounts(context: Context, core: Core) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_INCOMING_REPAIRED, false)) return
        val editor = prefs.edit()
        var seen = 0
        var moved = 0
        try {
            // The numbers each account's name carries, as bare digit runs.
            val numbers = core.accountList.mapNotNull { account ->
                val address = account.params.identityAddress ?: return@mapNotNull null
                val runs = Regex("[0-9][0-9 -]*[0-9]").findAll(address.displayName.orEmpty())
                    .map { run -> run.value.filter(Char::isDigit) }
                    .filter { it.length >= 6 }
                    .toList()
                if (runs.isEmpty()) null else account to runs
            }
            for (log in core.callLogs) {
                if (log.dir != Call.Dir.Incoming) continue
                val tail = ODORIK_TAIL.find(log.remoteAddress.displayName.orEmpty().trim())
                    ?.groupValues?.get(1) ?: continue
                seen++
                val matching = numbers.filter { (_, runs) -> runs.any { it.endsWith(tail) } }
                if (matching.size != 1) continue
                val account = matching[0].first
                val identity = account.params.identityAddress?.asStringUriOnly() ?: continue
                val key = keyOf(log)
                val stamp = get(context, key) ?: SkCallStamp(source = SkCallStamp.SOURCE_LOG)
                if (stamp.identity == identity) continue

                val serverAddress = account.params.serverAddress
                val host = serverAddress?.domain.orEmpty()
                val port = serverAddress?.port ?: 0
                var fixed = withBadge(context, stamp, identity)
                if (stamp.isFromCall) {
                    fixed = fixed.copy(
                        server = if (host.isNotEmpty() && port > 0) "$host:$port" else host,
                        transport = serverAddress?.transport?.name?.uppercase().orEmpty(),
                    )
                }
                put(context, key, fixed, editor)
                moved++
            }
        } catch (e: Exception) {
            Log.e("$TAG Repairing the incoming calls' accounts failed: $e")
        }
        editor.putBoolean(KEY_INCOMING_REPAIRED, true).apply()
        Log.i("$TAG Repaired incoming accounts once: [$moved] of [$seen] call(s) with a line tail moved")
        SkDiag.log(context, SkDiagKind.CALLS, "repair of incoming accounts: $moved of $seen calls with a line tail moved")
    }

    // ------------------------------------------------------------------ the account side

    /** The account [local] names: by the whole address, else by the user part alone. */
    @WorkerThread
    private fun accountOf(local: Address?): Account? {
        local ?: return null
        return LinphoneUtils.getAccountForAddress(local)
            ?: coreContext.core.accountList.find {
                !local.username.isNullOrEmpty() && it.params.identityAddress?.username == local.username
            }
    }

    @WorkerThread
    private fun identityOf(local: Address?): String =
        accountOf(local)?.params?.identityAddress?.asStringUriOnly() ?: local?.asStringUriOnly().orEmpty()

    /** The `To` line of the INVITE that brought [call] in, exactly as it was sent. */
    @WorkerThread
    private fun toHeaderOf(call: Call): Address? = try {
        call.getToHeader("To")?.takeIf { it.isNotBlank() }?.let { Factory.instance().createAddress(it) }
    } catch (e: Exception) {
        null
    }

    @WorkerThread
    private fun requestAddressOf(call: Call): Address? = try {
        call.requestAddress
    } catch (e: Exception) {
        null
    }

    /**
     * The account a live [call] goes through.
     *
     * **An incoming call is not asked of its log record** (白い熊, 2026-11-26). The SDK looks the
     * `To` line up among the accounts by the whole address; Odorik sends
     * `To: <sip:123456@sip.odorik.cz:6688>`, the port fits no identity, and the SDK then falls
     * back on the **selected** account and writes that one into the record as the local side —
     * every missed call was filed under whichever account's tab stood open. So the line is read
     * here from what came over the wire: the `To` line, then the request line (the contact that
     * account registered), each by the whole address and then by the user part; the record's
     * local side only when neither names an account.
     */
    @WorkerThread
    fun accountOf(call: Call): Account? {
        if (call.dir == Call.Dir.Incoming) {
            accountOf(toHeaderOf(call))?.let { return it }
            accountOf(requestAddressOf(call))?.let { return it }
        }
        return accountOf(call.callLog.localAddress)
    }

    /** The identity of the account a live [call] goes through — what its stamp will carry. */
    @WorkerThread
    fun identityOf(call: Call): String =
        accountOf(call)?.params?.identityAddress?.asStringUriOnly()
            ?: call.callLog.localAddress?.asStringUriOnly().orEmpty()

    /** The local side of a live [call]: its account's own address, else what the record says. */
    @WorkerThread
    fun localAddressOf(call: Call): Address? =
        accountOf(call)?.params?.identityAddress ?: call.callLog.localAddress

    /**
     * The identity of the account the call of [log] went through: what its stamp says, and only
     * for a call without one what the record's local side says — which for an incoming call may
     * be the account that was merely selected then (see [accountOf]).
     */
    @WorkerThread
    fun identityOf(context: Context, log: CallLog): String =
        (liveStampOf(log) ?: get(context, keyOf(log)))?.identity?.takeIf { it.isNotEmpty() }
            ?: identityOf(log.localAddress)

    /** The account of [identityOf], when it still exists. */
    @WorkerThread
    fun accountOf(context: Context, log: CallLog): Account? {
        val identity = identityOf(context, log)
        return coreContext.core.accountList.find { it.params.identityAddress?.asStringUriOnly() == identity }
    }

    /** The local side of [log]: its account's own address, else what the record says. */
    @WorkerThread
    fun localAddressOf(context: Context, log: CallLog): Address? =
        accountOf(context, log)?.params?.identityAddress ?: log.localAddress

    /**
     * What the account a live [call] goes through is called: the name it was given in its
     * settings, and only for an account without one its user part.
     */
    @WorkerThread
    fun accountNameOf(call: Call): String {
        val identity = localAddressOf(call)
        return identity?.displayName?.takeIf { it.isNotBlank() } ?: identity?.username.orEmpty()
    }

    private fun withBadge(context: Context, stamp: SkCallStamp, identity: String): SkCallStamp {
        val number = SkAccountNumbers.numberOf(context, identity)
        val (ground, text) = SkAccountNumbers.colors(context, number)
        return stamp.copy(identity = identity, number = number ?: -1, ground = ground, text = text)
    }

    // ------------------------------------------------------------------ the live call

    private class Live(var stamp: SkCallStamp) {
        val startedAt = SystemClock.elapsedRealtime()
        var connected = false
        var hungUpHere = false
        var blocked = false
        var written = false
    }

    /** Core thread only. */
    private val live = HashMap<Call, Live>()

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    /**
     * Told on the core thread whenever a live call's stamp was written: a list that sorts calls
     * by their account has to be made again then.
     */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() {
        for (listener in listeners) listener()
    }

    /**
     * The stamp in the making of the call [log] belongs to, while that call is still followed:
     * the record is announced before the stamp is written, and a row built in between would
     * otherwise take the record's word for the account.
     */
    @WorkerThread
    private fun liveStampOf(log: CallLog): SkCallStamp? {
        if (live.isEmpty()) return null
        val key = keyOf(log)
        return live.entries.firstOrNull {
            try {
                keyOf(it.key.callLog) == key
            } catch (e: Exception) {
                false
            }
        }?.value?.stamp
    }

    /**
     * The hang-up, the cancel or the decline was asked for on this phone. Called from
     * `CoreContext.terminateCall`, the one door all of them go through — the SDK itself does not
     * say afterwards which side ended a call.
     */
    @WorkerThread
    fun onTerminateAsked(call: Call) {
        live[call]?.hungUpHere = true
    }

    /** [call] is from a blocked caller and is about to be declined unannounced ([SkBlocked]). */
    @WorkerThread
    fun onBlocked(call: Call) {
        val record = live.getOrPut(call) { Live(firstSight(coreContext.context, call)) }
        record.hungUpHere = true
        record.blocked = true
    }

    @WorkerThread
    fun onCallStateChanged(core: Core, call: Call, state: Call.State) {
        val context = coreContext.context
        try {
            val first = call !in live
            val record = live.getOrPut(call) { Live(firstSight(context, call)) }
            if (first) {
                // Stored at once, not only when the call ends: the SDK announces the record of a
                // call the moment it starts ringing, and a history that lists it then must find
                // the line it rings on, not the record's guess (白い熊, 2026-11-26).
                put(context, keyOf(call.callLog), record.stamp)
                changed()
            }

            when (state) {
                Call.State.Connected, Call.State.StreamsRunning, Call.State.UpdatedByRemote, Call.State.Updating -> {
                    if (!record.connected) {
                        record.connected = true
                        record.stamp = record.stamp.copy(ringMs = SystemClock.elapsedRealtime() - record.startedAt)
                    }
                    record.stamp = withMedia(record.stamp, call)
                }
                Call.State.End, Call.State.Error -> {
                    if (!record.connected) {
                        record.stamp = record.stamp.copy(ringMs = SystemClock.elapsedRealtime() - record.startedAt)
                    } else {
                        record.stamp = withMedia(record.stamp, call)
                    }
                    record.stamp = withEnd(record, call, state)
                    put(context, keyOf(call.callLog), record.stamp)
                    record.written = true
                    if (record.stamp.end == SkCallStamp.END_MISSED) {
                        SkMissedCalls.note(context, keyOf(call.callLog), record.stamp.identity)
                    }
                    changed()
                }
                Call.State.Released -> {
                    // The log's final status is only certain here: a call taken on another device
                    // looks like any other missed one until then.
                    if (record.written && !record.connected && call.dir == Call.Dir.Incoming && !record.hungUpHere) {
                        val end = when (call.callLog.status) {
                            Call.Status.AcceptedElsewhere, Call.Status.DeclinedElsewhere -> SkCallStamp.END_ELSEWHERE
                            else -> record.stamp.end
                        }
                        if (end != record.stamp.end) {
                            record.stamp = record.stamp.copy(end = end)
                            put(context, keyOf(call.callLog), record.stamp)
                            SkMissedCalls.forget(context, keyOf(call.callLog))
                            changed()
                        }
                    }
                    live.remove(call)
                    if (core.callsNb == 0) live.clear()
                }
                else -> { }
            }
        } catch (e: Exception) {
            // A stamp is a courtesy to the history; it never gets to disturb a call.
            Log.e("$TAG Failed to record state [$state]: $e")
        }
    }

    /** What is frozen the moment a call is first seen: the account, its badge, the network. */
    @WorkerThread
    private fun firstSight(context: Context, call: Call): SkCallStamp {
        val account = accountOf(call)
        val params = account?.params
        val serverAddress = params?.serverAddress
        val server = serverAddress?.let { address ->
            val host = address.domain.orEmpty()
            val port = address.port
            if (host.isNotEmpty() && port > 0) "$host:$port" else host
        }.orEmpty()
        val transport = serverAddress?.transport?.name?.uppercase().orEmpty()

        val stamp = SkCallStamp(
            source = SkCallStamp.SOURCE_CALL,
            server = server,
            transport = transport,
        )
        val identity = identityOf(call)
        if (call.dir == Call.Dir.Incoming) {
            // The one record of which line a call rang on, should a row ever be doubted again.
            SkDiag.log(
                context,
                SkDiagKind.CALLS,
                "incoming local side: to=${toHeaderOf(call)?.asStringUriOnly()}" +
                    " request=${requestAddressOf(call)?.asStringUriOnly()}" +
                    " record=${call.callLog.localAddress?.asStringUriOnly()} account=$identity"
            )
        }
        return withNetwork(context, withBadge(context, stamp, identity))
    }

    private fun withNetwork(context: Context, stamp: SkCallStamp): SkCallStamp {
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return stamp
            val vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val ssid = (capabilities.transportInfo as? WifiInfo)?.ssid
                        ?.takeIf { it != UNKNOWN_SSID }?.trim('"').orEmpty()
                    stamp.copy(network = SkCallStamp.NET_WIFI, networkName = ssid, vpn = vpn)
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val operator = (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                        ?.networkOperatorName.orEmpty()
                    stamp.copy(network = SkCallStamp.NET_MOBILE, networkName = operator, vpn = vpn)
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    stamp.copy(network = SkCallStamp.NET_ETHERNET, vpn = vpn)
                else -> stamp.copy(network = SkCallStamp.NET_OTHER, vpn = vpn)
            }
        } catch (e: Exception) {
            Log.w("$TAG Can't tell which network the phone is on: $e")
            return stamp
        }
    }

    /** Codec, encryption, audio route and the other end's software, as they stand right now. */
    @WorkerThread
    private fun withMedia(stamp: SkCallStamp, call: Call): SkCallStamp {
        val params = call.currentParams
        val payload = params.usedAudioPayloadType
        val encryption = when (params.mediaEncryption) {
            MediaEncryption.SRTP -> SkCallStamp.ENC_SRTP
            MediaEncryption.ZRTP -> SkCallStamp.ENC_ZRTP
            MediaEncryption.DTLS -> SkCallStamp.ENC_DTLS
            else -> SkCallStamp.ENC_NONE
        }
        val route = when (call.outputAudioDevice?.type) {
            null -> stamp.route
            AudioDevice.Type.Earpiece -> SkCallStamp.ROUTE_EARPIECE
            AudioDevice.Type.Speaker -> SkCallStamp.ROUTE_SPEAKER
            AudioDevice.Type.Bluetooth, AudioDevice.Type.BluetoothA2DP -> SkCallStamp.ROUTE_BLUETOOTH
            AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> SkCallStamp.ROUTE_HEADSET
            AudioDevice.Type.HearingAid -> SkCallStamp.ROUTE_HEARING_AID
            else -> SkCallStamp.ROUTE_OTHER
        }
        return stamp.copy(
            codec = payload?.mimeType?.takeIf { it.isNotEmpty() } ?: stamp.codec,
            codecRate = payload?.clockRate?.takeIf { it > 0 } ?: stamp.codecRate,
            encryption = encryption,
            route = route,
            agent = call.remoteUserAgent?.takeIf { it.isNotEmpty() } ?: stamp.agent,
        )
    }

    @WorkerThread
    private fun withEnd(record: Live, call: Call, state: Call.State): SkCallStamp {
        val error = call.errorInfo
        val reason = error.reason
        val end = when {
            record.connected -> if (record.hungUpHere) SkCallStamp.END_LOCAL else SkCallStamp.END_REMOTE
            call.dir == Call.Dir.Outgoing -> when {
                record.hungUpHere -> SkCallStamp.END_CANCELLED
                reason == Reason.Busy -> SkCallStamp.END_BUSY
                reason == Reason.Declined -> SkCallStamp.END_DECLINED
                reason == Reason.NotAnswered || reason == Reason.TemporarilyUnavailable -> SkCallStamp.END_NO_ANSWER
                else -> SkCallStamp.END_FAILED
            }
            record.blocked -> SkCallStamp.END_BLOCKED
            record.hungUpHere -> SkCallStamp.END_DECLINED_HERE
            else -> SkCallStamp.END_MISSED
        }
        // The SIP answer is worth keeping only where it explains a call that never came about.
        val failed = !record.connected && call.dir == Call.Dir.Outgoing && !record.hungUpHere
        val quality = if (record.connected) call.averageQuality else -1f
        Log.i("$TAG Call ended in state [$state]: $end")
        return record.stamp.copy(
            end = end,
            sipCode = if (failed) error.protocolCode else 0,
            sipPhrase = if (failed) error.phrase.orEmpty() else "",
            quality = if (quality >= 0f) quality else -1f,
            agent = call.remoteUserAgent?.takeIf { it.isNotEmpty() } ?: record.stamp.agent,
        )
    }
}
