package org.linphone.shiroikuma

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.CoreKeepAliveThirdPartyAccountsService
import org.linphone.core.GlobalState
import org.linphone.core.RegistrationState

/**
 * shiroikuma-rindenwa fork — what 自由作業盤's 生存監視 asks about, and the repair it may trigger.
 * [SkKeepAliveReceiver] is the door; this is everything behind it.
 *
 * **"Alive" here means REGISTERED, not "the process is up".** A SIP phone whose process is running
 * but whose accounts are not registered receives exactly as many calls as one that is not running
 * at all, and this fork has no push to fall back on (Firebase is compiled in only when
 * `google-services.json` exists, and it does not here). So the answer is built from the Core's
 * account list, not from a process check.
 *
 * The answer is one of:
 *  - `OFF` — nothing to keep alive: the app's own 「keep service alive」 setting is off, or no
 *    account has registration enabled. 生存監視 does nothing.
 *  - `ALIVE` — the Core is started, the keep-alive foreground service is up, and every account
 *    with registration enabled is [RegistrationState.Ok].
 *  - `STARTED` — the Core or the service was dead and has been started. Answered straight away:
 *    registration takes a few seconds and the next check confirms it.
 *  - `RESTORED` — the Core was up but an account was not Ok; it was kicked ([repair]) and came
 *    back inside the budget.
 *  - `OFFLINE` — the device has no usable network, so no registration can be expected. Nothing is
 *    done and nothing is wrong.
 *  - `ERROR:<reason>` — still wrong after the repair, or something in the system is stopping
 *    incoming calls that the app cannot fix itself. The reason names the account and its state;
 *    it never carries a password or a token.
 *
 * Every call is traced to [SkTrace] by the door, including the environment, so a missed call at
 * 03:00 can be explained the next morning.
 */
object SkKeepAlive {

    /** The result for 生存監視, and the line for the trace behind it. */
    class Outcome(val result: String, val detail: String)

    /**
     * Whether [CoreKeepAliveThirdPartyAccountsService] is running, recorded by the service itself.
     * Process-local on purpose: if the process died the service died with it, and false is then the
     * truth. Upstream's own `CoreContext.keepAliveServiceStarted` is private and is set even when
     * the start fails.
     */
    @Volatile
    private var serviceRunning = false

    @AnyThread
    fun onServiceStarted() {
        serviceRunning = true
    }

    @AnyThread
    fun onServiceStopped() {
        serviceRunning = false
    }

    /**
     * Answer the watchdog, repairing what can be repaired on the way. Never throws; never takes
     * longer than [TOTAL_BUDGET_MS].
     *
     * [processAgeMs] is how long this process has been alive — a process younger than
     * [FRESH_PROCESS_MS] was most likely created by the very broadcast being answered, which means
     * the app was dead until a moment ago.
     */
    @AnyThread
    fun check(context: Context, processAgeMs: Long): Outcome {
        val deadline = SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS
        val fresh = processAgeMs < FRESH_PROCESS_MS
        val age = "age=${processAgeMs / 1000}s"

        // 1. Does this app want to be reachable in the background at all? Nothing below matters
        //    if it does not: the door only does what the app's own settings already want.
        val prefs = runCatching { corePreferences }.getOrNull()
            ?: return Outcome(RESULT_OFF, "the app has not finished starting, $age")
        val keepAlive = runCatching { prefs.keepServiceAlive }.getOrNull()
        if (keepAlive != true) {
            return Outcome(RESULT_OFF, "keep_service_alive=${keepAlive ?: "unreadable"}, $age")
        }

        // 2. The Core. In a fresh process it is on its way up — LinphoneApplication.onCreate
        //    starts it, and this broadcast is what created the process — so wait for it instead
        //    of calling a starting app dead.
        var snapshot = awaitStarted(minOf(deadline, SystemClock.elapsedRealtime() + CORE_WAIT_MS))
        if (snapshot == null || !snapshot.started) {
            val state = snapshot?.globalState?.toString() ?: "no answer from the Core thread"
            return if (fresh) {
                Outcome(RESULT_STARTED, "core still starting ($state), $age")
            } else {
                Outcome("$RESULT_ERROR:the Core is not running ($state)", age)
            }
        }
        if (snapshot.accounts.isEmpty()) {
            return Outcome(RESULT_OFF, "no account has registration enabled, $age")
        }

        val hard = hardBlockers(context)
        val soft = softBlockers(context)
        val environment = (hard + soft).ifEmpty { listOf("ok") }.joinToString("; ")

        fun detail(what: Snapshot) =
            "accounts [${what.describe()}] service=${if (serviceRunning) "up" else "down"} " +
                "$age env=[$environment]"

        // 3. The foreground service that keeps the Core reachable while the screen is off. This is
        //    the one thing a watchdog call can really repair.
        if (!serviceRunning) {
            val failure = startKeepAliveService(context)
            return if (failure == null) {
                Outcome(RESULT_STARTED, detail(snapshot))
            } else {
                Outcome("$RESULT_ERROR:$failure", detail(snapshot))
            }
        }
        if (fresh) {
            // The process was dead until this broadcast; the service is up again, registration is
            // seconds away. The next check is the one that confirms it.
            return Outcome(RESULT_STARTED, detail(snapshot))
        }

        // 4. No network at all — registration cannot be expected and nothing is wrong here.
        if (!hasUsableNetwork(context)) {
            return Outcome(RESULT_OFFLINE, detail(snapshot))
        }

        // 5. Registration — what this door actually promises.
        if (snapshot.allOk) {
            return Outcome(verdict(context, RESULT_ALIVE, hard, soft), detail(snapshot))
        }
        if (snapshot.pendingOnly) {
            // Mid-REGISTER, nothing failed: a momentary Refreshing is not something to repair.
            val settled = awaitAllOk(minOf(deadline, SystemClock.elapsedRealtime() + SETTLE_MS))
            if (settled != null && settled.allOk) {
                return Outcome(verdict(context, RESULT_ALIVE, hard, soft), detail(settled))
            }
            snapshot = settled ?: snapshot
        }

        repair()
        val repaired = awaitAllOk(deadline)
        if (repaired != null && repaired.allOk) {
            return Outcome(verdict(context, RESULT_RESTORED, hard, soft), detail(repaired))
        }
        val after = repaired ?: snapshot
        val bad = after.firstBad()
        val reason = if (bad == null) {
            "not registered"
        } else {
            "not registered: ${bad.identity} — ${bad.state}${bad.error}"
        }
        return Outcome("$RESULT_ERROR:$reason", detail(after))
    }

    // ------------------------------------------------------------------ the Core's own answer

    private class AccountState(
        val identity: String,
        val state: RegistrationState,
        /** `" (403 Forbidden)"` for a failure, empty otherwise. Never a credential. */
        val error: String,
    ) {
        val ok: Boolean
            get() = state == RegistrationState.Ok

        /** On its way somewhere: worth waiting for, not worth repairing. */
        val pending: Boolean
            get() = state == RegistrationState.Progress ||
                state == RegistrationState.Refreshing ||
                state == RegistrationState.None

        override fun toString(): String = "$identity=$state$error"
    }

    private class Snapshot(
        val globalState: GlobalState,
        val networkReachable: Boolean,
        /** Only the accounts with registration enabled — the others are nobody's phone line. */
        val accounts: List<AccountState>,
    ) {
        val started: Boolean
            get() = globalState == GlobalState.On

        val allOk: Boolean
            get() = accounts.isNotEmpty() && accounts.all { it.ok }

        val pendingOnly: Boolean
            get() = accounts.none { !it.ok && !it.pending }

        fun firstBad(): AccountState? = accounts.firstOrNull { !it.ok }

        fun describe(): String = accounts.joinToString(", ") +
            if (networkReachable) "" else " (core says the network is unreachable)"
    }

    /**
     * Read the Core from the Core's own thread and hand the result back. The SDK is not thread
     * safe, and this runs on a broadcast worker, so everything goes through
     * [org.linphone.core.CoreContext.postOnCoreThread] — which silently does nothing before the
     * Core thread exists, hence the timeout and the nullable result.
     */
    private fun snapshot(timeoutMs: Long): Snapshot? {
        val context = runCatching { coreContext }.getOrNull() ?: return null
        if (!context.isReady()) return null
        var taken: Snapshot? = null
        val latch = CountDownLatch(1)
        context.postOnCoreThread { core ->
            taken = runCatching {
                Snapshot(
                    core.globalState,
                    core.isNetworkReachable,
                    core.accountList
                        .filter { it.params.isRegisterEnabled }
                        .map {
                            AccountState(
                                it.params.identityAddress?.asStringUriOnly() ?: "?",
                                it.state,
                                errorSuffix(it),
                            )
                        },
                )
            }.getOrNull()
            latch.countDown()
        }
        if (!latch.await(timeoutMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)) return null
        return taken
    }

    /** The SIP answer behind a failure — `403 Forbidden` — which is what makes an ERROR useful. */
    private fun errorSuffix(account: Account): String {
        if (account.state != RegistrationState.Failed) return ""
        val info = runCatching { account.errorInfo }.getOrNull()
        val code = info?.protocolCode ?: 0
        val phrase = info?.phrase?.takeIf { it.isNotBlank() }
        return when {
            code > 0 && phrase != null -> " ($code $phrase)"
            code > 0 -> " ($code)"
            phrase != null -> " ($phrase)"
            else -> " (${runCatching { account.error }.getOrNull()})"
        }
    }

    private fun awaitStarted(deadline: Long): Snapshot? = poll(deadline) { it.started }

    private fun awaitAllOk(deadline: Long): Snapshot? = poll(deadline) { it.allOk }

    /**
     * Snapshot the Core until [done] holds or the deadline passes, returning the last snapshot
     * taken either way: a failing one is what names the account in the ERROR reason.
     */
    private fun poll(deadline: Long, done: (Snapshot) -> Boolean): Snapshot? {
        var last: Snapshot? = null
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            val taken = snapshot(minOf(remaining, CORE_CALL_TIMEOUT_MS))
            if (taken != null) {
                last = taken
                if (done(taken)) return taken
            }
            if (SystemClock.elapsedRealtime() + POLL_INTERVAL_MS >= deadline) return last
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (ignored: InterruptedException) {
                return last
            }
        }
    }

    /**
     * Kick the registrations: tell the Core the network is there if it believes otherwise (which
     * is what actually sends the REGISTERs again after a doze or a network change the SDK slept
     * through), then refresh them.
     */
    private fun repair() {
        val context = runCatching { coreContext }.getOrNull() ?: return
        context.postOnCoreThread { core ->
            runCatching {
                if (!core.isNetworkReachable) {
                    core.setNetworkReachable(true)
                }
                core.refreshRegisters()
            }
        }
    }

    // ------------------------------------------------------------------ the service

    /**
     * Start the keep-alive foreground service from the background.
     *
     * Not through `CoreContext.startKeepAliveService`: that uses plain `startService`, which a
     * background app has not been allowed to call since Android 8. `startForegroundService` is the
     * one that may be used — but on Android 12+ only by an app the system exempts, here either
     * because 白い熊 granted 「Display over other apps」 (this fork already needs it to raise the
     * call screen itself) or because the app is exempt from battery optimisation. With neither,
     * the start would throw, so it is reported instead of attempted.
     *
     * The service notification channel is checked first: upstream's foreground path gives up when
     * that channel is disabled, and a service started with `startForegroundService` that never
     * reaches the foreground is killed by the system a few seconds later — a crash every 15
     * minutes instead of an answer.
     *
     * Returns null when the service was started, or the reason it could not be.
     */
    private fun startKeepAliveService(context: Context): String? {
        val manager = NotificationManagerCompat.from(context)
        if (channelBlocked(manager, context.getString(R.string.notification_channel_service_id))) {
            return "the keep-alive service notification channel is disabled, so the service cannot be started"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !mayStartFromBackground(context)) {
            return "the app was not running and may not be started from the background — grant it " +
                "\"Display over other apps\" or switch battery optimisation off"
        }
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(Intent.ACTION_MAIN)
                    .setClass(context, CoreKeepAliveThirdPartyAccountsService::class.java),
            )
            null
        } catch (e: Throwable) {
            reason(e)
        }
    }

    private fun mayStartFromBackground(context: Context): Boolean {
        if (SkRinger.canRaiseCallScreen(context)) return true
        val power = context.getSystemService(PowerManager::class.java)
        return power?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    // ------------------------------------------------------------------ the system around us

    /**
     * ALIVE or RESTORED, unless something in the system stops incoming calls anyway.
     *
     * The hard blockers report every time: they are breaking calls now, and 白い熊 has to go and
     * undo them. The battery-optimisation exemption reports only when it **changes**, so a standing
     * condition does not raise a 生存監視 notification every 15 minutes for ever; the trace carries
     * it on every single call either way.
     */
    private fun verdict(
        context: Context,
        alive: String,
        hard: List<String>,
        soft: List<String>,
    ): String {
        if (hard.isNotEmpty()) {
            return "$RESULT_ERROR:${alive.lowercase()}, but ${hard.joinToString("; ")}"
        }
        val signature = soft.joinToString("; ")
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (signature != prefs.getString(KEY_REPORTED, "").orEmpty()) {
            prefs.edit().putString(KEY_REPORTED, signature).apply()
            if (signature.isNotEmpty()) {
                return "$RESULT_ERROR:${alive.lowercase()}, but $signature"
            }
        }
        return alive
    }

    /** Conditions under which an incoming call is lost now, whatever the app does. */
    private fun hardBlockers(context: Context): List<String> {
        val blockers = mutableListOf<String>()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            blockers += "notifications are disabled in the system"
        } else {
            // A channel that does not exist yet is not a fault — the Core creates them on start.
            if (channelBlocked(manager, context.getString(R.string.notification_channel_service_id))) {
                blockers += "the keep-alive service notification channel is disabled, so the foreground service cannot start"
            }
            if (
                channelBlocked(
                    manager,
                    context.getString(R.string.notification_channel_without_ringtone_incoming_call_id),
                )
            ) {
                blockers += "the incoming-call notification channel is disabled"
            }
        }
        if (startForegroundDenied(context)) {
            blockers += "the START_FOREGROUND app-op is denied (EMUI PowerGenie), so the service cannot stay in the foreground"
        }
        val activityManager = context.getSystemService(ActivityManager::class.java)
        if (runCatching { activityManager?.isBackgroundRestricted }.getOrNull() == true) {
            blockers += "the app is background-restricted in the system settings"
        }
        return blockers
    }

    /** Conditions that will kill the app sooner or later, but are not stopping a call right now. */
    private fun softBlockers(context: Context): List<String> {
        val blockers = mutableListOf<String>()
        val power = context.getSystemService(PowerManager::class.java)
        if (
            power != null &&
            !runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
                .getOrDefault(true)
        ) {
            blockers += "battery optimisation is not switched off for this app, so the system may kill it"
        }
        return blockers
    }

    private fun channelBlocked(manager: NotificationManagerCompat, id: String): Boolean {
        val channel = runCatching { manager.getNotificationChannel(id) }.getOrNull() ?: return false
        return channel.importance == NotificationManagerCompat.IMPORTANCE_NONE
    }

    /**
     * EMUI's PowerGenie sets this app-op to "ignore" on an app it has decided against, and from
     * then on `startForeground()` quietly does nothing: the service never reaches the foreground,
     * the process is reclaimed, and incoming calls stop arriving with nothing in the app's own logs
     * to show why. On 白い熊's phone it is the usual cause of a dead 臨電話, and it costs one
     * app-op read to see. The op name is not in the public SDK, so the platform's own string is
     * used, and anything unexpected counts as "not denied" rather than a false alarm.
     */
    @Suppress("DEPRECATION") // unsafeCheckOpNoThrow is the only public way to read an app-op
    private fun startForegroundDenied(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                OP_START_FOREGROUND,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun hasUsableNetwork(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
                ?: return true
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (ignored: Throwable) {
            // Never claim OFFLINE on a guess — a wrong OFFLINE is a check 生存監視 skips.
            true
        }
    }

    fun reason(e: Throwable): String =
        "${e.javaClass.simpleName}${e.message?.let { ": " + it.take(MAX_REASON_CHARS) } ?: ""}"

    private const val RESULT_OFF = "OFF"
    private const val RESULT_ALIVE = "ALIVE"
    private const val RESULT_STARTED = "STARTED"
    private const val RESULT_RESTORED = "RESTORED"
    private const val RESULT_OFFLINE = "OFFLINE"
    private const val RESULT_ERROR = "ERROR"

    /** A process this young was most likely created by the very broadcast being answered. */
    private const val FRESH_PROCESS_MS = 10_000L

    /** 生存監視 waits 20 s and the contract asks for one reply inside 15 s. */
    private const val TOTAL_BUDGET_MS = 13_000L
    private const val CORE_WAIT_MS = 6_000L
    private const val SETTLE_MS = 3_000L
    private const val CORE_CALL_TIMEOUT_MS = 2_000L
    private const val POLL_INTERVAL_MS = 700L
    private const val MAX_REASON_CHARS = 120

    private const val OP_START_FOREGROUND = "android:start_foreground"

    /** Device-local, never exported: which standing warning was last reported to 生存監視. */
    private const val PREFS_FILE = "sk_keepalive"
    private const val KEY_REPORTED = "reported_blockers"
}
