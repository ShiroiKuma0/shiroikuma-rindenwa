package org.linphone.shiroikuma

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — a call screen that actually appears, and a ringer 白い熊 can silence.
 *
 * **Why the call screen needs starting at all.** Upstream announces an incoming call with a single
 * notification carrying a full-screen intent (see `NotificationsManager.createCallNotification`).
 * Android honours that intent only while the device is locked or its screen is off; on an unlocked
 * phone it is downgraded to a heads-up banner, which is all 白い熊 ever got when a call arrived
 * while he was using the phone. No notification flag fixes that — it is documented behaviour. The
 * cure is to start [org.linphone.ui.call.CallActivity] ourselves the moment a call comes in, and to
 * leave the notification exactly as upstream built it so it still serves the locked screen, the
 * shade and Android Auto.
 *
 * That start happens with none of our Activities in front, which makes it a *background activity
 * start* — refused since Android 10 unless the app holds `SYSTEM_ALERT_WINDOW` ("Display over other
 * apps"). Nothing here ever draws an overlay window; the permission is used purely as the licence
 * to start an Activity. Without the grant [canRaiseCallScreen] is false and the app behaves exactly
 * as upstream does, heads-up and all — so the app asks for it on start ([ensureCallScreenPermission],
 * driven from `MainActivity.onResume`) and also reports it as a row in the 臨電話 UI page, under
 * Calls. It can be withdrawn at any time, which is why it is re-checked before every call rather
 * than trusted once at startup.
 *
 * **Once per call, never twice.** [onCallStateChanged] fires again for `IncomingEarlyMedia` and for
 * every state after it. Raising the screen each time would drag 白い熊 back into it every time he
 * pressed Home while the phone rang, so a call is remembered the first time it is raised and left
 * alone afterwards.
 *
 * **Silencing.** The ring is the SDK's, not the notification's: upstream creates the incoming-call
 * channel without a ringtone (`createIncomingCallNotificationChannelWithoutRingtone`) and without
 * vibration, and the Core plays `ring` on the ringer device and drives the vibrator itself. One
 * [Core.stopRinging] therefore takes both away — and, unlike declining, it leaves the call itself
 * untouched: the caller goes on hearing it ring, and 白い熊 can still answer. Ringing can restart
 * when a call moves on to `IncomingEarlyMedia`, so a silenced call is re-silenced on every incoming
 * state it reaches until it ends. Silencing is deliberately one-way; there is nothing to un-mute.
 *
 * Logging goes through the SDK's own [Log] rather than the fork's usual `android.util.Log`, so that
 * why-didn't-the-screen-come-up reads in the same stream as the call states it is about.
 */
object SkRinger {
    private const val TAG = "[Sk Ringer]"

    /** Calls whose screen has been raised once already. */
    private val raised = ConcurrentHashMap.newKeySet<String>()

    /** Calls 白い熊 has silenced. */
    private val silenced = ConcurrentHashMap.newKeySet<String>()

    /**
     * A call's identity for the two sets above: its SIP Call-ID, falling back to the remote address
     * for the window in which the id is still empty. Both are stable for as long as the call lives,
     * which is all these sets need.
     */
    @AnyThread
    private fun key(call: Call): String {
        val callId = call.callLog.callId
        return if (callId.isNullOrEmpty()) call.remoteAddress.asStringUriOnly() else callId
    }

    /** Whether a background activity start would be allowed, i.e. "Display over other apps". */
    @AnyThread
    fun canRaiseCallScreen(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @AnyThread
    fun isSilenced(call: Call): Boolean {
        return silenced.contains(key(call))
    }

    /**
     * Silence this call's ringtone and vibration without touching the call. Returns false if it was
     * already silenced, so a second press — on the screen and on the notification both — is a no-op
     * rather than a second round of work.
     */
    @WorkerThread
    fun silence(core: Core, call: Call): Boolean {
        if (!silenced.add(key(call))) {
            return false
        }

        Log.i("$TAG Silencing the ringer for call [${call.remoteAddress.asStringUriOnly()}]")
        core.stopRinging()
        return true
    }

    /**
     * Driven from `CoreContext`'s call-state listener. Raises the call screen for a new incoming
     * call, keeps a silenced call silent across a re-ring, and forgets a call once it is over.
     */
    @WorkerThread
    fun onCallStateChanged(core: Core, call: Call, state: Call.State) {
        val id = key(call)

        if (LinphoneUtils.isCallIncoming(state)) {
            if (silenced.contains(id)) {
                // Moving to IncomingEarlyMedia can start the ringer up again.
                Log.i("$TAG Call [$id] is silenced, stopping the ringer again for state [$state]")
                core.stopRinging()
            }
            if (raised.add(id)) {
                // The first incoming state for this call — the one worth a line.
                SkDiag.log(
                    coreContext.context,
                    SkDiagKind.CALLS,
                    "incoming from ${call.remoteAddress.asStringUriOnly()} state=$state " +
                        "callScreen=${if (canRaiseCallScreen(coreContext.context)) "raised" else "not allowed"}",
                )
                raiseCallScreen()
            }
            return
        }

        if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
            if (state != Call.State.Released && call.callLog.dir == Call.Dir.Incoming) {
                SkDiag.log(
                    coreContext.context,
                    SkDiagKind.CALLS,
                    "incoming from ${call.remoteAddress.asStringUriOnly()} ended: $state " +
                        "status=${call.callLog.status} duration=${call.callLog.duration}s",
                )
            }
            raised.remove(id)
            silenced.remove(id)
            if (core.callsNb == 0) {
                // Nothing is left to remember; a stale key can never outlive the last call.
                raised.clear()
                silenced.clear()
            }
        }
    }

    /**
     * Set once 白い熊 has been asked for the grant, and cleared again the moment the grant is seen.
     * So: asked once per app start, never twice over, and asked afresh if the grant is ever
     * withdrawn while the app runs. Declining is respected for the rest of the session — the 臨電話
     * UI page still carries the row, and the next start asks again.
     */
    private var permissionAsked = false

    /**
     * Check the grant on start and ask for it when it is missing. Android offers no in-app
     * permission dialog for this one — only a settings screen — so the ask is a dialog of ours that
     * explains what is at stake and then opens that screen.
     */
    @UiThread
    fun ensureCallScreenPermission(activity: Activity) {
        if (canRaiseCallScreen(activity)) {
            permissionAsked = false
            return
        }

        if (permissionAsked) {
            return
        }
        permissionAsked = true

        Log.w(
            "$TAG 'Display over other apps' isn't granted, asking for it — without it an incoming call can only raise a notification"
        )
        SkConfirmDialog(
            activity,
            activity.getString(R.string.sk_call_overlay_prompt_title),
            activity.getString(R.string.sk_call_overlay_prompt_message),
            activity.getString(R.string.sk_call_overlay_prompt_confirm),
        ) {
            openCallScreenPermissionSettings(activity)
        }
    }

    /** The system screen that holds the toggle. Shared with the 臨電話 UI page's own row. */
    @UiThread
    fun openCallScreenPermissionSettings(activity: Activity) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        } catch (e: Exception) {
            // Some builds only answer the list-wide screen, EMUI among them.
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: Exception) {
                Log.e("$TAG Can't open the 'Display over other apps' settings screen: $e2")
            }
        }
    }

    /**
     * Bring up the in-call UI. Reuses `CoreContext.showCallActivity`, which already wraps the start
     * in the PendingIntent + ActivityOptions dance the newer platforms want.
     */
    @WorkerThread
    private fun raiseCallScreen() {
        if (!canRaiseCallScreen(coreContext.context)) {
            Log.w(
                "$TAG Not raising the incoming call screen: 'Display over other apps' isn't granted, leaving the notification to do it"
            )
            return
        }

        Log.i("$TAG Raising the incoming call screen")
        coreContext.postOnMainThread {
            coreContext.showCallActivity()
        }
    }
}
