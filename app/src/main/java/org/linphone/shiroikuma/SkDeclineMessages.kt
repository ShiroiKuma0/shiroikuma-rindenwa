package org.linphone.shiroikuma

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.CallLog
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — refusing a call and still saying something.
 *
 * The third control on the incoming-call panel is neither an answer nor a bare decline: it hangs
 * the call up and writes back. The same thing is offered on a missed call in the history, where
 * there is nothing left to decline and only the writing back remains.
 *
 * **The reply does not leave through rindenwa.** 白い熊's lines are SIP DIDs that forward from
 * real SIMs sitting in a bridge phone elsewhere; a SIP MESSAGE back down the DID would go to the
 * gateway, not to the caller's handset. So the text is handed to the sister messenger
 * (shiroikuma-messeji, package `shiroikuma.messeji`) through its automation provider, and Messēji
 * sends it as a real SMS from the SIM behind that DID, over the 自由作業盤 bridge.
 *
 * **Which SIM is none of this app's business.** The DID → SIM table lives in 携帯 (白い熊,
 * 2026-11-02), where one list is edited instead of two. Rindenwa sends [FROM_DID] — the DID the
 * call arrived on, which is the user part of the account identity — and Messēji resolves it. A DID
 * missing from that table comes back as a synchronous refusal naming it, which is the only way a
 * gap in the table can ever show up here, so the refusal is put in front of 白い熊 rather than
 * logged.
 *
 * **What a success means.** [SendResult.Accepted] is "queued in Messēji", not "sent": the bridge
 * may be asleep and the relay phone may be out of signal. Messēji never drops an accepted message
 * and its own thread carries the ticks, so that thread is the record and nothing here polls.
 *
 * **Where the texts come from.** Two lists, both in the app's own preferences beside the rest of
 * the fork's settings ([SkTheme.prefs]): the general one, seeded once with [defaults], and a
 * per-caller one keyed by the caller's bare SIP address. A caller's own list is how a recurring
 * call gets its own answer without that line cluttering every other call. Both are edited on the
 * 臨電話 UI page; the picker on the call screen is for using them, not for keeping them in order.
 */
object SkDeclineMessages {
    private const val TAG = "[Sk Decline Messages]"

    /** Messēji's automation provider — the same one 自由作業盤 drives. */
    private const val AUTHORITY = "content://shiroikuma.messeji.automation"
    private const val METHOD = "send_message"
    private const val FROM_DID = "from_did"
    private const val TO = "to"
    private const val BODY = "body"
    private const val STATUS = "status"
    private const val STATUS_OK = "OK"
    private const val ERROR = "error"
    private const val ID = "id"

    private const val GENERAL_KEY = "sk_decline_messages"
    private const val PER_CALLER_PREFIX = "sk_decline_messages_"

    /** Entries are one per line; a message is a single line by construction (see [store]). */
    private const val SEPARATOR = "\n"

    /** What came back from Messēji. Accepted means queued there, never "it reached the caller". */
    sealed class SendResult {
        data class Accepted(val id: String?) : SendResult()

        data class Refused(val reason: String) : SendResult()
    }

    /**
     * Everything needed to write back, read off the call *before* it is declined — declining
     * releases the call and with it the addresses this is taken from.
     */
    data class ReplyTarget(val did: String, val number: String, val callerKey: String, val name: String)

    // --- the lists ---

    /**
     * What a fresh install offers. They are the four 白い熊 settled on in the sister app, and they
     * are only a seed: the stored list replaces them the first time one is kept or removed.
     */
    @AnyThread
    fun defaults(context: Context): List<String> = listOf(
        context.getString(R.string.sk_call_decline_default_1),
        context.getString(R.string.sk_call_decline_default_2),
        context.getString(R.string.sk_call_decline_default_3),
        context.getString(R.string.sk_call_decline_default_4),
    )

    /** The lines offered for every caller. */
    @AnyThread
    fun general(context: Context): List<String> {
        val stored = SkTheme.prefs(context).getString(GENERAL_KEY, null) ?: return defaults(context)
        return read(stored)
    }

    /** The lines offered only when [address] is the one calling. */
    @AnyThread
    fun forCaller(context: Context, address: String): List<String> {
        if (address.isEmpty()) return emptyList()
        val stored = SkTheme.prefs(context).getString(key(address), null) ?: return emptyList()
        return read(stored)
    }

    /** Every caller that has a list of their own, for the editor on the UI page. */
    @AnyThread
    fun callersWithOwnMessages(context: Context): List<String> =
        SkTheme.prefs(context).all.keys
            .filter { it.startsWith(PER_CALLER_PREFIX) }
            .map { it.removePrefix(PER_CALLER_PREFIX) }
            .filter { forCaller(context, it).isNotEmpty() }
            .sorted()

    @AnyThread
    fun setGeneral(context: Context, messages: List<String>) = store(context, GENERAL_KEY, messages)

    @AnyThread
    fun setForCaller(context: Context, address: String, messages: List<String>) =
        store(context, key(address), messages)

    @AnyThread
    fun addToGeneral(context: Context, message: String) {
        val list = general(context)
        if (list.contains(message)) return
        store(context, GENERAL_KEY, list + message)
        Log.i("$TAG Kept a decline message for every caller")
    }

    @AnyThread
    fun addForCaller(context: Context, address: String, message: String) {
        if (address.isEmpty()) return
        val list = forCaller(context, address)
        if (list.contains(message)) return
        store(context, key(address), list + message)
        Log.i("$TAG Kept a decline message for one caller")
    }

    /** True when neither list already holds it, i.e. when it is worth asking about keeping. */
    @AnyThread
    fun isNew(context: Context, address: String, message: String): Boolean =
        !general(context).contains(message) && !forCaller(context, address).contains(message)

    // --- addressing ---

    /**
     * The DID a call came in on: the user part of the account identity it is bound to, kept in
     * E.164 with its `+`. Falls back to the call's own local address when no account matches, and
     * to an empty string when even that yields nothing — which Messēji then refuses by name.
     */
    @WorkerThread
    fun didOf(localAddress: Address?): String {
        localAddress ?: return ""
        val account = LinphoneUtils.getAccountForAddress(localAddress)
        val identity = account?.params?.identityAddress ?: localAddress
        return identity.username.orEmpty()
    }

    /**
     * The caller's number as Messēji wants it: the user part, nothing else. A number that arrives
     * without a `+` is passed on exactly as it came — guessing a country code here would send the
     * reply to a stranger.
     */
    @AnyThread
    fun numberOf(address: Address?): String = address?.username.orEmpty()

    /** The caller's address as the per-caller list keys it: no display name, no parameters. */
    @AnyThread
    fun callerKey(address: Address?): String {
        address ?: return ""
        val clone = address.clone()
        clone.clean()
        clone.displayName = null
        return clone.asStringUriOnly()
    }

    @WorkerThread
    fun targetOf(callLog: CallLog?, fallbackName: String = ""): ReplyTarget? {
        callLog ?: return null
        val remote = callLog.remoteAddress
        val number = numberOf(remote)
        if (number.isEmpty()) {
            Log.e("$TAG The caller has no number to write back to")
            return null
        }
        val name = fallbackName.ifEmpty { LinphoneUtils.getDisplayName(remote) }
        return ReplyTarget(didOf(callLog.localAddress), number, callerKey(remote), name)
    }

    // --- sending ---

    /**
     * Hand the text to Messēji. Synchronous and headless — the provider answers in milliseconds —
     * but a content-provider call still must not run on the main thread.
     */
    @WorkerThread
    fun send(context: Context, target: ReplyTarget, message: String): SendResult {
        if (target.did.isEmpty()) {
            return SendResult.Refused(context.getString(R.string.sk_call_decline_no_did))
        }

        val extras = Bundle().apply {
            putString(FROM_DID, target.did)
            putString(TO, target.number)
            putString(BODY, message)
        }

        val answer = try {
            context.contentResolver.call(Uri.parse(AUTHORITY), METHOD, null, extras)
        } catch (e: Exception) {
            Log.e("$TAG Messēji's automation provider refused the call: $e")
            return SendResult.Refused(
                context.getString(R.string.sk_call_decline_no_messeji),
            )
        }

        if (answer == null) {
            Log.e("$TAG Messēji's automation provider isn't there")
            return SendResult.Refused(context.getString(R.string.sk_call_decline_no_messeji))
        }

        val status = answer.getString(STATUS)
        if (status != STATUS_OK) {
            val reason = answer.getString(ERROR).orEmpty()
            Log.e("$TAG Messēji refused the message: [$reason]")
            return SendResult.Refused(
                reason.ifEmpty { context.getString(R.string.sk_call_decline_refused) },
            )
        }

        val id = answer.getString(ID)
        Log.i("$TAG Messēji queued the reply from [${target.did}] to [${target.number}] as [$id]")
        return SendResult.Accepted(id)
    }

    // --- storage plumbing ---

    @AnyThread
    private fun key(address: String): String = PER_CALLER_PREFIX + address

    @AnyThread
    private fun read(stored: String): List<String> =
        stored.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Writing an empty list stores an empty string rather than removing the key — that is what
     * makes "I deleted every general message" stick instead of falling back to [defaults].
     */
    @AnyThread
    private fun store(context: Context, key: String, messages: List<String>) {
        val flattened = messages
            .map { it.replace("\r", " ").replace("\n", " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(SEPARATOR)
        SkTheme.prefs(context).edit().putString(key, flattened).apply()
    }
}
