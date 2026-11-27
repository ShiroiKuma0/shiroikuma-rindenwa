package org.linphone.shiroikuma

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
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
 * **Silencing without looking.** Besides the Silence button on the call screen and on the
 * notification, two gestures do the same thing for as long as a call rings unsilenced:
 *
 * - **Any volume button.** The key press itself is taken ([onVolumeKey], from
 *   `CallActivity.onKeyDown`) and swallowed, so the press that silences does not also move a
 *   volume. Once the ringer is silent the buttons are plain volume buttons again. This works with
 *   the call screen in front, which is where every incoming call puts 白い熊. With another app in
 *   front the key never reaches us, and there is no honest stand-in for it: the system's
 *   volume-changed broadcast was tried for that (+030 to +032), and in those builds every call
 *   went silent a split second into its ring — most likely because a volume moves when the ringer
 *   starts, which a broadcast cannot tell from a finger. Do not bring it back untested.
 *   **And on 白い熊's phone the key does not reach us that way at all.** Two things stand in
 *   front of it. 自由作業盤's 物理鍵 grabber takes the volume keys below the framework; and when it
 *   hands a tap back, Android's own telecom layer takes it: our calls are registered with Telecom
 *   (self-managed), so a volume key during a ring is "silence the ringer" to the system, which
 *   swallows it, stops a ringer it does not have, and tells the connection — inside androidx's
 *   core-telecom, which passes nothing on (`dumpsys telecom` showed `SILENCE` at the very second
 *   of the press, 2026-11-19, and the call rang on). So the grabber, which holds the key anyway,
 *   asks us directly: [silenceFromOutside], through the call door. [announceRinging] is how it
 *   knows a call rings here.
 * - **Turning the phone face down.** Read off the accelerometer: the screen has to have been seen
 *   facing some other way first, and then lie face down for two readings in a row. So a phone that
 *   was already lying on its screen when the call came in rings as usual — only the act of turning
 *   it over silences.
 *
 * Both are armed by the first incoming state of a call and dropped the moment nothing rings
 * unsilenced any more, so the sensor never outlives the ringing.
 *
 * **A call that comes in during a call in another app is announced quietly** (白い熊,
 * 2026-11-27: one rang loudly into the ear in the middle of a WhatsApp call — with the audio in
 * communication mode the ringtone comes out of the earpiece). [isQuiet] decides it once per call,
 * the first time anything asks: the device's audio mode says that a call is up (a messenger's
 * call or the phone's own), and that call is not ours. Such a call
 *
 * - does not ring — it is silenced from its first state, and the stop is repeated a few times
 *   over the first half second, because the SDK may start its ringer just after the listeners
 *   ran;
 * - vibrates **once**, briefly ([vibrateOnce], behind the last of those stops), instead of for
 *   as long as it rings — and not at all where 白い熊 switched the vibration off or the phone is on silent;
 * - does not raise the call screen, and its notification carries no full-screen intent: the
 *   small panel at the top of the display ([SkIncomingPanel]) announces it instead.
 *
 * The audio mode is asked, not Telecom: Telecom would count this very call. A second call of our
 * own is none of this — the SDK already plays its waiting tone into the call for that.
 * [inCallAudio] alone (our own call included) is what makes a message's or a missed call's
 * notification silent while a call is up.
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

    /** Whether a call is announced quietly, decided once per call. See [isQuiet]. */
    private val quiet = ConcurrentHashMap<String, Boolean>()

    /** The moments after the first state at which a quiet call's ringer is stopped again. */
    private val QUIET_STOPS_MS = longArrayOf(60L, 250L, 600L)

    /** When a quiet call's one vibration is given: behind the last of those stops. */
    private const val QUIET_VIBRATION_AT_MS = 700L

    /** Two short pulses. */
    private val QUIET_VIBRATION = longArrayOf(0L, 220L, 140L, 220L)

    /** Told, on the core thread, about every call the moment its ringer is silenced. */
    private val silencedListeners = CopyOnWriteArraySet<(Call) -> Unit>()

    @AnyThread
    fun addSilencedListener(listener: (Call) -> Unit) {
        silencedListeners.add(listener)
    }

    @AnyThread
    fun removeSilencedListener(listener: (Call) -> Unit) {
        silencedListeners.remove(listener)
    }

    /** True while a call rings unsilenced, i.e. while the gestures below have something to do. */
    @Volatile
    private var watching = false

    const val ACTION_RINGING = "shiroikuma.rindenwa.RINGING"
    const val EXTRA_RINGING = "ringing"
    private const val JIYUSAGYOBAN = "shiroikuma.jiyusagyoban"

    /** Gravity along the screen's normal, in m/s²: about +9.8 face up, about −9.8 face down. */
    private const val FACE_DOWN_Z = -8f
    private const val NOT_FACE_DOWN_Z = -3f
    private const val FACE_DOWN_READINGS = 2

    private val faceDownListener = object : SensorEventListener {
        /** Set once the screen has been seen facing anywhere but down. */
        var armed = false
        var faceDownReadings = 0

        override fun onSensorChanged(event: SensorEvent) {
            val z = event.values[2]
            if (z > NOT_FACE_DOWN_Z) {
                armed = true
                faceDownReadings = 0
            } else if (z < FACE_DOWN_Z) {
                if (armed && ++faceDownReadings >= FACE_DOWN_READINGS) {
                    silenceByGesture("the phone was turned face down")
                }
            } else {
                faceDownReadings = 0
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
    }

    /**
     * A volume button went down in the call screen. Returns true when the press was taken to
     * silence the ringer — the caller then swallows it — and false when nothing rings unsilenced,
     * in which case the button keeps its ordinary meaning.
     */
    @UiThread
    fun onVolumeKey(): Boolean {
        if (!watching) {
            return false
        }
        silenceByGesture("a volume button was pressed")
        return true
    }

    /**
     * A sister app asks for the ringer to be silenced — the `silence_ringer` method of the call
     * door ([SkCallsProvider]). False when nothing rings unsilenced.
     */
    @AnyThread
    fun silenceFromOutside(who: String): Boolean {
        if (!watching) {
            return false
        }
        silenceByGesture("$who asked for it")
        return true
    }

    /** Silence every call that is still ringing, and say in the log and the diagnostics why. */
    @AnyThread
    private fun silenceByGesture(why: String) {
        if (!watching) {
            return
        }
        coreContext.postOnCoreThread { core ->
            for (call in core.calls) {
                if (LinphoneUtils.isCallIncoming(call.state) && silence(core, call)) {
                    Log.i("$TAG Ringer silenced because $why")
                    SkDiag.log(
                        coreContext.context,
                        SkDiagKind.CALLS,
                        "incoming from ${call.remoteAddress.asStringUriOnly()} silenced: $why",
                    )
                    coreContext.notificationsManager.refreshCallNotification(call)
                }
            }
        }
    }

    /**
     * Tell 自由作業盤 that a call rings here unsilenced, or no longer does.
     *
     * Its 物理鍵 key grabber takes the volume keys below the framework and, with the screen on,
     * keeps a single tap for itself — so the tap never reaches the call screen and [onVolumeKey]
     * never runs (白い熊, 2026-11-19: "they have no effect"). It hands the tap back while the
     * *phone* rings, which it learns from the system's phone-state broadcast; a SIP call raises
     * none. This is that signal for our calls: an explicit broadcast, [ACTION_RINGING] with the
     * boolean extra [EXTRA_RINGING], sent to that one package. Nobody listening is not an error.
     */
    private fun announceRinging(context: Context, ringing: Boolean) {
        try {
            context.sendBroadcast(
                Intent(ACTION_RINGING).setPackage(JIYUSAGYOBAN).putExtra(EXTRA_RINGING, ringing)
            )
        } catch (e: Exception) {
            Log.w("$TAG Couldn't announce ringing [$ringing]: $e")
        }
    }

    /** Arm or drop the two gestures to match whether anything still rings unsilenced. */
    @WorkerThread
    private fun updateWatch(core: Core) {
        val ringing = core.calls.any {
            LinphoneUtils.isCallIncoming(it.state) && !silenced.contains(key(it))
        }
        if (ringing == watching) {
            return
        }

        val context = coreContext.context
        announceRinging(context, ringing)
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (ringing) {
            Log.i("$TAG A call is ringing, watching for a volume button and for the phone being turned face down")
            faceDownListener.armed = false
            faceDownListener.faceDownReadings = 0
            val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                sensorManager.registerListener(
                    faceDownListener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    Handler(Looper.getMainLooper()),
                )
            } else {
                Log.w("$TAG No accelerometer, turning the phone face down won't silence the ringer")
            }
            watching = true
        } else {
            watching = false
            Log.i("$TAG Nothing rings unsilenced any more, no longer watching for the silencing gestures")
            sensorManager?.unregisterListener(faceDownListener)
        }
    }

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

    /**
     * Whether the device's audio is in a call right now — any app's, ours included. The mode is
     * set by whoever holds a call (a messenger: communication; the phone: in call) and falls
     * back to normal when that call ends or its app dies.
     */
    @AnyThread
    fun inCallAudio(context: Context): Boolean {
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode
            ?: return false
        return mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_CALL_SCREENING
    }

    /**
     * Whether this incoming call is announced quietly: it came in while a call in another app
     * was up. Decided the first time it is asked for a call and kept until that call is over —
     * the notification and the ringer must agree, whichever of their listeners runs first.
     */
    @WorkerThread
    fun isQuiet(call: Call): Boolean {
        if (call.dir != Call.Dir.Incoming) return false
        return quiet.getOrPut(key(call)) {
            val context = coreContext.context
            val busy = call.core.callsNb <= 1 && inCallAudio(context)
            if (busy) {
                val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode
                Log.i("$TAG Another app holds a call (audio mode [$mode]), announcing this one quietly")
                SkDiag.log(
                    context,
                    SkDiagKind.CALLS,
                    "incoming from ${call.remoteAddress.asStringUriOnly()} announced quietly: " +
                        "a call in another app is up (audio mode $mode)",
                )
            }
            busy
        }
    }

    /**
     * One short vibration for a call announced quietly — where the SDK would vibrate for as long
     * as it rings. Given as a ringtone's, which the system lets through from the background.
     */
    @AnyThread
    private fun vibrateOnce(context: Context) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                VibrationEffect.createWaveform(QUIET_VIBRATION, -1),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        } catch (e: Exception) {
            Log.w("$TAG Couldn't vibrate for a quiet call: $e")
        }
    }

    /** The first incoming state of a call that is announced quietly. */
    @WorkerThread
    private fun announceQuietly(core: Core, call: Call, id: String) {
        val vibrate = core.isVibrationOnIncomingCallEnabled
        silenced.add(id)
        core.stopRinging()
        for (delay in QUIET_STOPS_MS) {
            coreContext.postOnCoreThreadDelayed({
                if (silenced.contains(id) && LinphoneUtils.isCallIncoming(call.state)) {
                    it.stopRinging()
                }
            }, delay)
        }
        silencedListeners.forEach { it(call) }
        if (vibrate) {
            // Behind the last stop: the SDK's vibrator is the application's, the same one as
            // ours, and stopping its vibration would take ours with it.
            coreContext.postOnCoreThreadDelayed({
                if (LinphoneUtils.isCallIncoming(call.state)) vibrateOnce(coreContext.context)
            }, QUIET_VIBRATION_AT_MS)
        }
        SkIncomingPanel.show(call)
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
        updateWatch(core)
        silencedListeners.forEach { it(call) }
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
                val quietly = isQuiet(call)
                SkDiag.log(
                    coreContext.context,
                    SkDiagKind.CALLS,
                    "incoming from ${call.remoteAddress.asStringUriOnly()} state=$state " +
                        "callScreen=${
                            when {
                                quietly -> "withheld, a call in another app is up"
                                canRaiseCallScreen(coreContext.context) -> "raised"
                                else -> "not allowed"
                            }
                        }",
                )
                if (quietly) {
                    announceQuietly(core, call, id)
                } else {
                    raiseCallScreen()
                }
            }
            updateWatch(core)
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
            quiet.remove(id)
            if (core.callsNb == 0) {
                // Nothing is left to remember; a stale key can never outlive the last call.
                raised.clear()
                silenced.clear()
                quiet.clear()
            }
        }

        // Answered, ended or failed: the panel of a quietly announced call has nothing to say.
        SkIncomingPanel.dismiss(call)

        // Answered, ended or failed: whatever it was, it may have been the last thing ringing.
        updateWatch(core)
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
