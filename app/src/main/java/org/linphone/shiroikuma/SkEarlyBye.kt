package org.linphone.shiroikuma

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.coreContext

/**
 * shiroikuma-rindenwa fork — the second way to end a call that is still ringing at the other end.
 *
 * Hanging up an outgoing call before it is answered sends a SIP `CANCEL`. Odorik's front proxy
 * answers that CANCEL with `481 Transaction Does Not Exist` (白い熊, 2026-11-14: a well-formed one,
 * same branch, Call-ID, From tag and Request-URI as the INVITE, on the same TCP connection), the
 * app shows the call as ended, and the called phone goes on ringing until somebody refuses it
 * there.
 *
 * The `183`/`180` that made the phone ring also opened an **early dialog** with the server behind
 * the proxy, and RFC 3261 §15 lets the caller end an early dialog with a `BYE`. So when a CANCEL
 * comes back `481`, this sends that BYE.
 *
 * The Linphone SDK has no call for it, so both halves are done here:
 *
 * - **Tracking** — the SDK logs every SIP message whole, and `CoreContext`'s logging listener hands
 *   each line to [onCoreLog]. The provisional answers to our own INVITEs are read there and the
 *   dialog they describe (tags, route set, remote target) is kept until the INVITE gets its final
 *   answer.
 * - **Sending** — the BYE goes out on a connection of its own to the very proxy the refusal came
 *   from, on a thread of its own, and answers a `401`/`407` challenge once with the account's
 *   credentials.
 *
 * A provider that honours CANCEL never answers it `481`, so nothing here ever fires for one.
 * Every step is written to the Outgoing calls diagnostic.
 */
object SkEarlyBye {
    private const val TAG = "[Sk Early Bye]"

    /** What a provisional answer told us about the dialog it opened. */
    private class EarlyDialog(
        val callId: String,
        val from: String,
        val to: String,
        val cseq: Int,
        /** Already in the order a caller uses them: the reverse of the Record-Route headers. */
        val routes: List<String>,
        val remoteTarget: String,
    )

    private class Peer(val transport: String, val host: String, val port: Int)

    private val dialogs = ConcurrentHashMap<String, EarlyDialog>()

    private val RECEIVED = Regex("""received \[\d+] new bytes from \[(\w+)://(.+):(\d+)]:\r?\n""")

    /**
     * Fed every line the SIP stack logs. Only received SIP answers matter, and those are told
     * apart from the hundreds of other lines a minute by two plain substring tests.
     */
    @AnyThread
    fun onCoreLog(context: Context, message: String) {
        if (!message.startsWith("channel [") || !message.contains("new bytes from [")) return
        try {
            val prefix = RECEIVED.find(message) ?: return
            val sip = message.substring(prefix.range.last + 1)
            if (!sip.startsWith("SIP/2.0 ")) return
            val status = sip.substring(8, 11).toIntOrNull() ?: return

            val head = sip.substringBefore("\r\n\r\n").substringBefore("\n\n")
            val headers = head.lines().drop(1).map { it.trimEnd('\r') }
            val cseq = header(headers, "CSeq", null) ?: return
            val callId = header(headers, "Call-ID", "i") ?: return
            val cseqNumber = cseq.substringBefore(' ').trim().toIntOrNull() ?: return
            val method = cseq.substringAfter(' ').trim()

            if (method == "INVITE") {
                if (status in 101..199) {
                    val to = header(headers, "To", "t") ?: return
                    if (!to.contains(";tag=")) return
                    val from = header(headers, "From", "f") ?: return
                    val contact = header(headers, "Contact", "m") ?: return
                    val routes = headers
                        .filter { it.startsWith("Record-Route:", ignoreCase = true) }
                        .flatMap { splitList(it.substringAfter(':')) }
                        .reversed()
                    if (dialogs.size > 16) dialogs.clear() // never more than a handful of calls
                    dialogs[callId] = EarlyDialog(callId, from, to, cseqNumber, routes, uriOf(contact))
                } else if (status >= 200) {
                    // Answered, refused or cancelled properly: the early dialog is over.
                    dialogs.remove(callId)
                }
            } else if (method == "CANCEL" && status == 481) {
                val dialog = dialogs.remove(callId)
                val (transport, host, port) = prefix.destructured
                if (dialog == null) {
                    note(context, "CANCEL refused with 481 for callId=$callId, no early dialog to end by BYE")
                    return
                }
                note(context, "CANCEL refused with 481 for callId=$callId, ending the early dialog by BYE")
                val peer = Peer(transport.uppercase(), host.trim('[', ']'), port.toInt())
                thread(name = "SkEarlyBye") { send(context, peer, dialog, dialog.cseq + 1, null) }
            }
        } catch (e: Throwable) {
            // Reading a log line must never take the SIP stack that wrote it down. Not the SDK's
            // own logger either: this runs inside its callback.
            android.util.Log.e(TAG, "Failed to read a SIP answer: $e")
        }
    }

    // ------------------------------------------------------------------ sending

    /** One BYE, one answer. [authorization] is the ready header line for the second attempt. */
    @WorkerThread
    private fun send(context: Context, peer: Peer, dialog: EarlyDialog, cseq: Int, authorization: String?) {
        try {
            val socket = when (peer.transport) {
                "TCP" -> Socket()
                "TLS" -> SSLSocketFactory.getDefault().createSocket()
                else -> {
                    note(context, "BYE for callId=${dialog.callId} not sent: transport ${peer.transport} is not handled")
                    return
                }
            }
            socket.use {
                it.connect(InetSocketAddress(peer.host, peer.port), TIMEOUT_MS)
                it.soTimeout = TIMEOUT_MS

                val local = it.localAddress.hostAddress.orEmpty().substringBefore('%')
                val via = if (local.contains(':')) "[$local]" else local
                val request = buildString {
                    append("BYE ${dialog.remoteTarget} SIP/2.0\r\n")
                    append("Via: SIP/2.0/${peer.transport} $via:${it.localPort};branch=z9hG4bK.${token()};rport\r\n")
                    for (route in dialog.routes) append("Route: $route\r\n")
                    append("From: ${dialog.from}\r\n")
                    append("To: ${dialog.to}\r\n")
                    append("Call-ID: ${dialog.callId}\r\n")
                    append("CSeq: $cseq BYE\r\n")
                    append("Max-Forwards: 70\r\n")
                    if (authorization != null) append("$authorization\r\n")
                    append("Reason: SIP;cause=487;text=\"Cancelled by caller\"\r\n")
                    append("User-Agent: shiroikuma-rindenwa/${BuildConfig.VERSION_NAME}\r\n")
                    append("Content-Length: 0\r\n\r\n")
                }
                SkDiag.log(context, SkDiagKind.SIP, "early BYE sent to ${peer.transport}://${peer.host}:${peer.port}\n$request")
                it.getOutputStream().apply {
                    write(request.toByteArray(Charsets.UTF_8))
                    flush()
                }

                val answer = readHead(it.getInputStream())
                SkDiag.log(context, SkDiagKind.SIP, "early BYE answered\n$answer")
                val statusLine = answer.lineSequence().firstOrNull().orEmpty().trim()
                val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                note(
                    context,
                    "BYE for callId=${dialog.callId} to ${peer.transport}://${peer.host}:${peer.port} " +
                        "answered \"$statusLine\"",
                )

                if ((status == 401 || status == 407) && authorization == null) {
                    val challenge = answer.lines()
                        .firstOrNull {
                            it.startsWith("Proxy-Authenticate:", true) || it.startsWith("WWW-Authenticate:", true)
                        }
                        ?.substringAfter(':')?.trim()
                        ?: return
                    answerChallenge(context, peer, dialog, cseq + 1, status, challenge)
                }
            }
        } catch (e: Throwable) {
            note(context, "BYE for callId=${dialog.callId} failed: $e")
        }
    }

    /** The credentials live in the Core, which is only ever touched on its own thread. */
    @WorkerThread
    private fun answerChallenge(
        context: Context,
        peer: Peer,
        dialog: EarlyDialog,
        cseq: Int,
        status: Int,
        challenge: String,
    ) {
        val realm = parameter(challenge, "realm") ?: return
        val nonce = parameter(challenge, "nonce") ?: return
        val opaque = parameter(challenge, "opaque")
        val qop = parameter(challenge, "qop")?.split(',')?.map { it.trim() }?.contains("auth") == true
        val algorithm = parameter(challenge, "algorithm") ?: "MD5"
        val digest = when (algorithm.uppercase()) {
            "MD5" -> "MD5"
            "SHA-256" -> "SHA-256"
            else -> {
                note(context, "BYE for callId=${dialog.callId}: digest algorithm $algorithm is not handled")
                return
            }
        }
        val fromUri = uriOf(dialog.from)
        val username = fromUri.substringAfter(':').substringBefore('@')
        val domain = fromUri.substringAfter('@').substringBefore(';').substringBefore(':')

        coreContext.postOnCoreThread { core ->
            val info = core.findAuthInfo(realm, username, domain)
            if (info == null) {
                note(context, "BYE for callId=${dialog.callId}: no credentials for $username@$realm")
                return@postOnCoreThread
            }
            val user = info.userid?.takeIf { it.isNotEmpty() } ?: info.username ?: username
            val password = info.password
            val storedHa1 = info.ha1?.takeIf { it.isNotEmpty() && digest == "MD5" }
            val ha1 = when {
                !password.isNullOrEmpty() -> hash(digest, "$user:$realm:$password")
                storedHa1 != null -> storedHa1
                else -> {
                    note(context, "BYE for callId=${dialog.callId}: no usable password for $user@$realm")
                    return@postOnCoreThread
                }
            }
            val uri = dialog.remoteTarget
            val ha2 = hash(digest, "BYE:$uri")
            val line = buildString {
                append(if (status == 407) "Proxy-Authorization" else "Authorization")
                append(": Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", ")
                if (qop) {
                    val cnonce = token()
                    val response = hash(digest, "$ha1:$nonce:00000001:$cnonce:auth:$ha2")
                    append("response=\"$response\", qop=auth, nc=00000001, cnonce=\"$cnonce\"")
                } else {
                    append("response=\"${hash(digest, "$ha1:$nonce:$ha2")}\"")
                }
                append(", algorithm=$algorithm")
                if (opaque != null) append(", opaque=\"$opaque\"")
            }
            thread(name = "SkEarlyBye") { send(context, peer, dialog, cseq, line) }
        }
    }

    // ------------------------------------------------------------------ small parsing

    private fun header(headers: List<String>, name: String, compact: String?): String? {
        for (line in headers) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            if (key.equals(name, true) || (compact != null && key.equals(compact, true))) {
                return line.substring(colon + 1).trim()
            }
        }
        return null
    }

    /** A header that may carry several values: split on the commas that are outside `<…>`. */
    private fun splitList(value: String): List<String> {
        val parts = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((index, char) in value.withIndex()) {
            when (char) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    parts.add(value.substring(start, index).trim())
                    start = index + 1
                }
            }
        }
        parts.add(value.substring(start).trim())
        return parts.filter { it.isNotEmpty() }
    }

    /** The bare URI of a From/To/Contact value: what is inside `<…>`, or the value minus its parameters. */
    private fun uriOf(value: String): String =
        if (value.contains('<')) {
            value.substringAfter('<').substringBefore('>')
        } else {
            value.substringBefore(';').trim()
        }

    private fun parameter(challenge: String, name: String): String? {
        val match = Regex("""(?:^|[\s,])$name\s*=\s*(?:"([^"]*)"|([^\s,]+))""", RegexOption.IGNORE_CASE)
            .find(challenge) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    /** Read up to the blank line that ends a SIP message's headers; a BYE's answer has no body we need. */
    private fun readHead(input: InputStream): String {
        val bytes = StringBuilder()
        while (bytes.length < MAX_ANSWER_BYTES) {
            val byte = input.read()
            if (byte < 0) break
            bytes.append(byte.toChar())
            if (bytes.endsWith("\r\n\r\n")) {
                // A provisional answer is not the answer: keep reading for the final one.
                val status = bytes.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                if (status in 100..199) bytes.setLength(0) else break
            }
        }
        return bytes.toString()
    }

    private fun hash(algorithm: String, text: String): String =
        MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** Never through the SDK's logger: half of the callers are inside its callback. */
    private fun note(context: Context, line: String) {
        android.util.Log.i(TAG, line)
        SkDiag.log(context, SkDiagKind.OUTGOING, line)
    }

    private const val TIMEOUT_MS = 8000
    private const val MAX_ANSWER_BYTES = 16 * 1024
}
