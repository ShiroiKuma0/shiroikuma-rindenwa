package org.linphone.shiroikuma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.linphone.BuildConfig
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the 保存復元 state-export contract, for 白い熊 自由作業盤's one-run
 * backup of every sister app.
 *
 * Three exported, token-gated actions ([SkAutomation] is the gate — no `android:permission`, since
 * the caller cannot hold one):
 *  - [ACTION_LIST_CATEGORIES] — instant; replies `OK:` plus one
 *    `id<TAB>label<TAB>parent<TAB>on|off` line per exportable category. The third field is the
 *    parent id on sub-options (`history.calls`, `history.chat`, `appearance.fonts`) and empty
 *    otherwise; the fourth says whether the item starts ticked. The ids are exactly the ones
 *    `items` accepts.
 *  - [ACTION_EXPORT_STATE] — runs the very same category ZIP as the Export/Import panel
 *    ([SkEximport.export]), headlessly: no Activity, no interaction, ONE zip. Extras: `token`,
 *    optional `path` (an absolute directory that OVERRIDES the configured export directory),
 *    optional `items` (comma-separated category ids; absent = everything), optional
 *    `progress_action`, plus `reply_action` / `reply_package` / `reply_id`.
 *  - [ACTION_CANCEL_EXPORT] — stops a running export and deletes its half-written file. Extras:
 *    `token`, optional `reply_id` (absent = whatever is running). It answers nothing at all, and is
 *    a silent no-op if nothing is running or the run has already finished. Routed through this
 *    receiver rather than a service on purpose: an exported receiver is the only part of the app a
 *    third party can reach, since the services are — correctly — `android:exported="false"`.
 *
 * Directory precedence: the `path` extra → the configured export directory → `ERROR:no-directory`.
 *
 * The reply is a plain broadcast carrying `reply_id` + `result` — the only channel that works on
 * 白い熊's EMUI (verified 2026-07-23): the ordered-broadcast result is severed between third-party
 * apps, and a Binder-bearing extra (ResultReceiver / PendingIntent / Messenger) may be dropped
 * outright. Setting the ordered result too is harmless AOSP correctness, never the only reply.
 * [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so a stopped caller still hears us. Exactly one terminal
 * reply per request, guarded by an [AtomicBoolean] so an async success and a synchronous error can
 * never both fire.
 *
 * Progress is real counts, never a percentage — `項目 123/456 — Accounts` — throttled to one
 * broadcast per [PROGRESS_THROTTLE_MS] plus an unthrottled final one at completion.
 */
class SkStateExportReceiver : BroadcastReceiver() {

    /** What a parsed request turned out to be: already answerable, or an export to run. */
    private sealed class Request {
        class Done(val result: String) : Request()

        class Export(val cats: Set<SkEximport.Cat>, val path: String) : Request()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_CANCEL_EXPORT) {
            // Fire-and-forget: no goAsync, no reply, not even an error. See [cancel].
            cancel(context.applicationContext, intent)
            return
        }
        if (action != ACTION_EXPORT_STATE && action != ACTION_LIST_CATEGORIES) {
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent,
        // so the async success path and any synchronous error path cannot double-finish (and a
        // dropped path cannot leave the caller waiting forever).
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val app = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()

        fun finishWith(result: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(TAG, "result → $result")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    app.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            pending.setResultData(result)
            pending.finish()
        }

        val request = try {
            parse(app, intent, action)
        } catch (e: Exception) {
            Request.Done("ERROR:${reason(e)}")
        }

        when (request) {
            is Request.Done -> finishWith(request.result)
            is Request.Export -> {
                val progress = throttledProgress(app, progressAction, replyPackage, replyId)
                val run = Running(replyId)
                running = run
                val worker = Thread {
                    try {
                        finishWith(runExport(app, request.cats, request.path, progress, run))
                    } finally {
                        // Only if still ours — a later export may already have taken the slot
                        if (running === run) running = null
                    }
                }
                run.thread = worker
                worker.start()
            }
        }
    }

    /**
     * Decide the request without doing any work: the gate first (the switch and the token report
     * distinctly, since they debug differently), then the instant category list, then the export's
     * own validation — so a malformed request is answered before anything is written.
     */
    private fun parse(context: Context, intent: Intent, action: String?): Request {
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val itemsRaw = intent.getStringExtra(EXTRA_ITEMS)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
        val cats = parseItems(itemsRaw)
        Log.i(
            TAG,
            "received $action: enabled=${SkAutomation.enabled(context)}, " +
                "tokenLen=${token?.length ?: 0}, items=$itemsRaw, path=$path",
        )

        return when {
            !SkAutomation.enabled(context) -> Request.Done("ERROR:automation disabled")
            !SkAutomation.isTokenValid(context, token) -> Request.Done("ERROR:bad token")
            action == ACTION_LIST_CATEGORIES -> Request.Done(categoryList(context))
            cats == null -> Request.Done("ERROR:unknown category in items: $itemsRaw")
            path.isNotEmpty() && !path.startsWith("/") ->
                Request.Done("ERROR:$EXTRA_PATH must be an absolute directory")

            else -> Request.Export(cats, path)
        }
    }

    /**
     * `OK:` plus one `id<TAB>label<TAB>parent<TAB>on|off` line per category. Parents are emitted
     * before their children, as the contract requires.
     *
     * The fourth field says whether the item starts ticked in a picker drawn from this reply; it is
     * positional, so a top-level item — which has no parent — still needs the empty third field.
     */
    private fun categoryList(context: Context): String {
        fun line(cat: SkEximport.Cat, parent: String) =
            "${cat.id}\t${context.getString(cat.labelRes)}\t$parent\t${if (cat.default) "on" else "off"}"

        val lines = mutableListOf<String>()
        for (cat in SkEximport.Cat.topLevel()) {
            lines += line(cat, "")
            for (child in SkEximport.Cat.childrenOf(cat)) {
                lines += line(child, cat.id)
            }
        }
        return lines.joinToString(separator = "\n", prefix = "OK:")
    }

    /** The requested categories, or null when [itemsRaw] names an id we do not export. */
    private fun parseItems(itemsRaw: String): Set<SkEximport.Cat>? {
        val ids = itemsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SkEximport.Cat.entries.toSet()
        val cats = ids.mapNotNull { SkEximport.Cat.byId(it) }.toSet()
        return cats.takeIf { it.size == ids.distinct().size }
    }

    /**
     * Stop the export in flight, if the one named is the one running. Never replies — not `OK:`,
     * not an error — and is a silent no-op when nothing is running, when the run already finished,
     * or when `reply_id` names a different one. It has to be safe to send at any time.
     *
     * The stop itself is the panel's Cancel button's path: interrupt the worker, which
     * [SkEximport]'s `checkCancelled` picks up at the next entry boundary and unwinds. The plain
     * `java.io` streams underneath are not interruptible, so a `write()` in flight completes rather
     * than tearing — the flag is simply seen at the next check. Unwinding runs the same `catch` as
     * any other failure, which discards the half-written file, so a cancelled export leaves the
     * backup directory exactly as it found it.
     */
    private fun cancel(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (!SkAutomation.enabled(context) || !SkAutomation.isTokenValid(context, token)) {
            Log.i(TAG, "cancel refused: automation disabled or bad token")
            return
        }

        val wanted = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val run = running
        when {
            run == null -> Log.i(TAG, "cancel: nothing is running, no-op")
            wanted.isNotEmpty() && wanted != run.replyId ->
                Log.i(TAG, "cancel: [$wanted] is not the running export [${run.replyId}], no-op")

            else -> {
                Log.i(TAG, "cancel: stopping export [${run.replyId}]")
                run.cancelled = true
                run.thread?.interrupt()
            }
        }
    }

    /**
     * The export in flight. A [BroadcastReceiver] instance dies with its broadcast, so a later
     * `CANCEL_EXPORT` arrives at a different object entirely — this has to be class-level state.
     */
    private class Running(val replyId: String) {
        @Volatile
        var thread: Thread? = null

        @Volatile
        var cancelled = false
    }

    /** Runs on a background thread; returns the single result line and never throws. */
    private fun runExport(
        context: Context,
        cats: Set<SkEximport.Cat>,
        path: String,
        progress: ThrottledProgress,
        run: Running,
    ): String {
        val target = try {
            SkEximport.headlessTarget(context, path) ?: return "ERROR:no-directory"
        } catch (e: Exception) {
            return storageError(path, e)
        }

        return try {
            // The counted length is the fallback for a destination we cannot stat; it is final once
            // export() returns, which is after the ZIP's central directory has been flushed.
            val counting = CountingOutputStream(target.open())
            counting.use { SkEximport.export(context, cats, it, progress.listener) }
            val bytes = target.size().takeIf { it > 0 } ?: counting.count
            progress.final()
            "OK:${target.displayPath}|$bytes|${humanSize(bytes)}|${cats.size} categories"
        } catch (e: Exception) {
            target.discard() // a half-written ZIP is garbage — never leave it as "the last export"
            // Whatever the interrupt surfaced as, a run we asked to stop reports as cancelled
            if (run.cancelled) "ERROR:cancelled" else storageError(path, e)
        }
    }

    /**
     * An absolute path we were told to write but cannot needs All-files access; name that
     * specifically, since it is the one failure 白い熊 fixes with a toggle rather than a code change.
     */
    private fun storageError(path: String, e: Exception): String {
        val noAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        return if (path.isNotEmpty() && noAllFiles) "ERROR:no-storage-access" else "ERROR:${reason(e)}"
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Display size for the reply line — the caller cannot stat the file, so we compute both forms. */
    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private fun throttledProgress(
        context: Context,
        progressAction: String,
        replyPackage: String,
        replyId: String,
    ): ThrottledProgress {
        val appLabel = context.getString(R.string.app_name)
        val unit = context.getString(R.string.sk_state_progress_unit)

        fun send(current: Long, total: Long, text: String) {
            try {
                context.sendBroadcast(
                    Intent(progressAction)
                        .setPackage(replyPackage.ifEmpty { null })
                        .putExtra(EXTRA_REPLY_ID, replyId)
                        .putExtra(EXTRA_PROGRESS_APP, appLabel)
                        .putExtra(EXTRA_PROGRESS_TEXT, text)
                        .putExtra(EXTRA_PROGRESS_CURRENT, current)
                        .putExtra(EXTRA_PROGRESS_TOTAL, total)
                        .putExtra(EXTRA_PROGRESS_UNIT, unit)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
                )
            } catch (e: Exception) {
                Log.w(TAG, "progress broadcast failed: $e")
            }
        }

        var lastSent = 0L
        var lastTotal = 0L
        return ThrottledProgress(
            listener = { done, total, stage ->
                lastTotal = total.toLong()
                val now = System.currentTimeMillis()
                if (progressAction.isNotEmpty() && now - lastSent >= PROGRESS_THROTTLE_MS) {
                    lastSent = now
                    send(done.toLong(), total.toLong(), "$unit $done/$total — $stage")
                }
            },
            final = {
                if (progressAction.isNotEmpty()) {
                    send(lastTotal, lastTotal, "$unit $lastTotal/$lastTotal")
                }
            },
        )
    }

    /** The throttled progress channel plus the unthrottled completion broadcast. */
    private class ThrottledProgress(
        val listener: SkEximport.ProgressListener,
        val final: () -> Unit,
    )

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }

    companion object {
        private const val TAG = "RindenwaStateExport"
        private const val KILO = 1024.0
        private const val PROGRESS_THROTTLE_MS = 500L

        // Must stay in step with the manifest's ${applicationId}.action.* intent filter.
        const val ACTION_EXPORT_STATE = BuildConfig.APPLICATION_ID + ".action.EXPORT_STATE"
        const val ACTION_LIST_CATEGORIES = BuildConfig.APPLICATION_ID + ".action.LIST_CATEGORIES"
        const val ACTION_CANCEL_EXPORT = BuildConfig.APPLICATION_ID + ".action.CANCEL_EXPORT"

        /** The export in flight, if any. Written from the worker's `finally` and from [cancel]. */
        @Volatile
        private var running: Running? = null

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_PROGRESS_ACTION = "progress_action"
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_PROGRESS_APP = "app"
        private const val EXTRA_PROGRESS_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "current"
        private const val EXTRA_PROGRESS_TOTAL = "total"
        private const val EXTRA_PROGRESS_UNIT = "unit"
    }
}
