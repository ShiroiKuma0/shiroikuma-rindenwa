package org.linphone.shiroikuma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import org.linphone.BuildConfig

/**
 * shiroikuma-rindenwa fork — the keep-alive door for 自由作業盤's 生存監視 (asked for by 白い熊 via
 * the 自由作業盤 chat, 2026-11-07). The same door 白い熊 Simplex carries, with one difference that
 * matters here: **alive means registered**, not merely "the process is up" — see [SkKeepAlive].
 *
 * 生存監視 sends `shiroikuma.rindenwa.action.KEEP_ALIVE` every 15 minutes and on every unlock,
 * ordered and with [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so it reaches a force-stopped app. The
 * services of this app are — correctly — not exported, so an exported receiver is the only part a
 * sister app can reach; and being woken is the point, since EMUI's kills take the app's own restart
 * job with them.
 *
 * **No permission and no token.** The door starts nothing the app's own settings do not already
 * ask for, and reports state 白い熊 can read in the app anyway. (The data door next to it,
 * `automation.AutomationProvider`, is the one that checks who is calling, because it moves data.)
 *
 * Extras in: `reply_action`, `reply_package`, `reply_id`. The reply goes back both ways, exactly
 * once, inside 15 s — [setResultData] for the ordered send, plus a fresh broadcast to
 * `reply_action` in `reply_package` carrying `reply_id` and `result`. The broadcast is the one that
 * actually arrives on 白い熊's EMUI (verified 2026-07-23 for the state-export door): the ordered
 * result is severed between third-party apps there.
 *
 * Every call is written to the fork's own trace ([SkTrace], `rindenwa-keepalive_<day>.txt`) with
 * the caller, the process age, every account's registration state and the environment around it.
 */
class SkKeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEEP_ALIVE) return

        val app = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        // Uptime, not wall clock: a process this young was created by this very broadcast.
        val processAgeMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()

        // The answer may have to wait for the Core to come up, or for a re-REGISTER to land, so
        // the broadcast is held open. The guard keeps the single-reply promise even if the work
        // thread and an error path both finish.
        val pending = goAsync()
        val finished = AtomicBoolean(false)

        fun finishWith(outcome: SkKeepAlive.Outcome) {
            if (!finished.compareAndSet(false, true)) return
            SkTrace.log(
                app,
                SkTrace.KIND_KEEP_ALIVE,
                "[${replyId.ifEmpty { "-" }}] from ${replyPackage.ifEmpty { "?" }} " +
                    "${outcome.detail} -> ${outcome.result}",
            )
            Log.i(TAG, "[$replyId] from ${replyPackage.ifEmpty { "?" }} → ${outcome.result}")
            // replyPackage is NOT optional: since API 26 an implicit broadcast never reaches a
            // manifest-declared receiver, so a reply without setPackage is not a weak reply, it
            // is none at all.
            if (replyAction.isNotEmpty() && replyPackage.isNotEmpty()) {
                try {
                    app.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage)
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_RESULT, outcome.result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            runCatching { pending.setResultData(outcome.result) }
            runCatching { pending.finish() }
        }

        Thread({
            val outcome = try {
                SkKeepAlive.check(app, processAgeMs)
            } catch (e: Throwable) {
                SkKeepAlive.Outcome("ERROR:${SkKeepAlive.reason(e)}", "unhandled failure")
            }
            finishWith(outcome)
        }, THREAD_NAME).start()
    }

    companion object {
        private const val TAG = "RindenwaKeepAlive"
        private const val THREAD_NAME = "sk-keepalive"

        /** Must stay in step with the manifest's `${applicationId}.action.KEEP_ALIVE` filter. */
        const val ACTION_KEEP_ALIVE = BuildConfig.APPLICATION_ID + ".action.KEEP_ALIVE"

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_RESULT = "result"
    }
}
