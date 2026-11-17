package org.linphone.shiroikuma

import androidx.annotation.WorkerThread
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Reason
import org.linphone.utils.AppUtils

/**
 * shiroikuma-rindenwa fork — what a call is doing, in words.
 *
 * Upstream shows a rotating circle on the outgoing and incoming screens, which says only that
 * something is happening. The line this produces sits under the number and says what: dialing,
 * ringing, connecting, and — on the screen a finished call leaves behind — why it ended.
 *
 * "Ringing" covers `183 Session Progress` as well as `180 Ringing`: a provider that plays its own
 * ringback or an announcement (Odorik does, on every call) cannot be told apart from a phone that
 * is really ringing, and to the person holding this one it is the same wait.
 */
object SkCallStatus {

    @WorkerThread
    fun textFor(call: Call): String = when (call.state) {
        Call.State.OutgoingInit, Call.State.OutgoingProgress ->
            AppUtils.getString(R.string.sk_call_status_dialing)

        Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia ->
            AppUtils.getString(R.string.sk_call_status_ringing)

        Call.State.IncomingReceived, Call.State.PushIncomingReceived,
        Call.State.IncomingEarlyMedia ->
            AppUtils.getString(R.string.sk_call_status_incoming)

        Call.State.Connected -> AppUtils.getString(R.string.sk_call_status_connecting)

        Call.State.Error -> ended(call, failed = true)

        Call.State.End, Call.State.Released -> ended(call, failed = false)

        // A running, paused or updating call has the timer and its own controls to speak for it.
        else -> ""
    }

    private fun ended(call: Call, failed: Boolean): String {
        val info = call.errorInfo
        return when (info.reason) {
            Reason.Busy -> AppUtils.getString(R.string.sk_call_status_busy)

            Reason.Declined -> AppUtils.getString(R.string.sk_call_status_declined)

            Reason.NotAnswered, Reason.TemporarilyUnavailable ->
                AppUtils.getString(R.string.sk_call_status_no_answer)

            else -> if (failed) {
                val why = listOf(info.protocolCode.takeIf { it > 0 }?.toString(), info.phrase)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")
                val base = AppUtils.getString(R.string.sk_call_status_failed)
                if (why.isEmpty()) base else "$base ($why)"
            } else {
                AppUtils.getString(R.string.sk_call_status_ended)
            }
        }
    }
}
