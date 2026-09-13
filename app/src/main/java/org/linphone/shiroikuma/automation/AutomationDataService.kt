package org.linphone.shiroikuma.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.linphone.R
import org.linphone.shiroikuma.SkEximport

/**
 * Where a data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and the
 * caller cannot checksum or encrypt a file that is still open.
 *
 * ## Why a plain thread and not a coroutine
 *
 * [SkEximport] cancels by **thread interrupt** — the same mechanism the Export/Import panel's own
 * Cancel button uses, checked at each ZIP entry boundary so a stopped export unwinds between
 * entries rather than tearing mid-write. A dedicated thread registered with [AutomationJobs] is
 * what makes `cancel` reach it.
 */
class AutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIRST, and on every path. A service started with startForegroundService() must reach
        // startForeground() within ~5 s or the system kills the process
        // (ForegroundServiceDidNotStartInTimeException) — including on the paths that turn out to
        // have nothing to do, which is exactly where it is easiest to forget.
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        if (runCatching { startForeground(NOTIFICATION_ID, notification(importing)) }.isFailure) {
            Log.w(TAG, "could not enter the foreground")
            intent?.getStringExtra(EXTRA_JOB)?.let { stale ->
                // The descriptor must not be left open: it holds the caller's file, and a caller
                // cannot checksum or encrypt a file that is still open.
                HANDOVER.remove(stale)?.let { runCatching { it.close() } }
                AutomationJobs.finish(stale)
            }
            return stop(startId)
        }

        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        // A stale or duplicated id stops SILENTLY. The request it belonged to has already had its
        // one terminal reply, so answering "ERROR:unknown job" here would be a second one and
        // would break the single-reply rule the whole family depends on.
        val fd = HANDOVER.remove(jobId) ?: run {
            Log.i(TAG, "[$jobId] no descriptor pending — already answered, stopping silently")
            return stop(startId)
        }
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)

        val replied = AtomicBoolean(false)

        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            Log.i(TAG, "[$jobId] result → $result")
            AutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            runCatching {
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    // Without this a caller that has been backgrounded never hears the answer,
                    // and on a clean phone it may not have been launched at all.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra("reply_id", jobId)
                    putExtra(AutomationProvider.KEY_RESULT, result)
                },
            )
            }.onFailure { Log.w(TAG, "[$jobId] reply broadcast failed: $it") }
        }

        val worker = Thread {
            try {
            val progress = progressListener(jobId, progressAction, replyPackage)
            if (importing) {
                reply(runImport(fd, items, progress))
            } else {
                reply(runExport(jobId, fd, items, progress))
            }
            } catch (t: Throwable) {
            reply(terminal(jobId, t))
            } finally {
            runCatching { fd.close() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            }
        }
        AutomationJobs.register(jobId, worker)
        worker.start()
        return START_NOT_STICKY
    }

    /**
     * Write the archive straight into the caller's descriptor.
     *
     * The bytes are counted as they go rather than stat'ed afterwards: the caller owns the file and
     * this app may not be able to see it at all — it can be an anonymous pipe, or a descriptor into
     * a directory this app cannot list.
     */
    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: SkEximport.ProgressListener,
    ): String {
        val cats = resolve(items) ?: return "ERROR:unknown category in items: $items"
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            val counting = object : OutputStream() {
            override fun write(b: Int) {
                out.write(b)
                written++
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len)
                written += len
            }

            override fun flush() = out.flush()
            }
            SkEximport.export(this, cats, counting, progress)
        }
        if (AutomationJobs.isCancelled(jobId)) return "ERROR:cancelled"
        return "OK:$written|${cats.size} categories"
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [SkEximport.import] wants the bytes, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would otherwise import half an archive, and a
     * half-restored app is worse than one that refused.
     */
    private fun runImport(
        fd: ParcelFileDescriptor,
        items: String?,
        progress: SkEximport.ProgressListener,
    ): String {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        if (bytes.isEmpty()) return "ERROR:empty archive"
        // Every category the archive actually carries, not every category we know about: asking for
        // one the archive lacks is how a restore ends up reporting success over nothing.
        val present = SkEximport.categoriesIn(bytes)
        if (present.isEmpty()) return "ERROR:archive carries no categories"
        // A caller may narrow the restore with `items`; absent, [resolve] answers the whole default
        // set, so the intersection is simply everything the archive holds.
        val requested = resolve(items) ?: return "ERROR:unknown category in items: $items"
        val cats = present intersect requested
        if (cats.isEmpty()) return "ERROR:archive carries none of the requested categories"
        val summary = SkEximport.import(this, bytes, cats, progress)
        // Settings and fonts are on disk when this returns (the preferences are committed, not
        // applied). Config sections and databases are only *staged* — the Core in this very
        // process has the live copies open — and are brought in by the next cold start. The
        // caller force-stops us straight after this reply, which is exactly what makes that next
        // start happen at the next tap on the icon (応用管理, 2026-09-13).
        return "OK:${summary.lines().size} restored"
    }

    /** The categories to export: the ids in [items], or this app's default set when absent. */
    private fun resolve(items: String?): Set<SkEximport.Cat>? {
        if (items.isNullOrBlank()) return SkEximport.Cat.entries.filter { it.default }.toSet()
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { SkEximport.Cat.byId(it) }
        return if (found.size == wanted.distinct().size) found.toSet() else null
    }

    /**
     * §3 progress: real counts, never a percentage, throttled to one broadcast per
     * [PROGRESS_THROTTLE_MS].
     *
     * `item` is the **category id** the panel highlights. [SkEximport]'s listener reports a
     * human label, so it is mapped back through the enum — an unrecognised stage simply omits the
     * extra rather than sending a wrong row.
     */
    private fun progressListener(
        jobId: String,
        progressAction: String?,
        replyPackage: String?,
    ): SkEximport.ProgressListener {
        // Both halves are required. Since API 26 an implicit broadcast reaches no manifest
        // receiver, so a progress line without setPackage is not weak progress, it is none — the
        // export runs and reports its terminal reply correctly while every progress line vanishes
        // in silence (contract correction, 2026-09-04).
        if (progressAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) {
            return SkEximport.ProgressListener { _, _, _ -> }
        }
        val appLabel = getString(R.string.app_name)
        val unit = getString(R.string.sk_state_progress_unit)
        val idOf = SkEximport.Cat.entries.associateBy({ getString(it.labelRes) }, { it.id })
        var lastSent = 0L
        return SkEximport.ProgressListener { done, total, stage ->
            val now = System.currentTimeMillis()
            if (now - lastSent >= PROGRESS_THROTTLE_MS) {
            lastSent = now
            sendProgress(progressAction, replyPackage, jobId, appLabel, unit, idOf, done, total, stage)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun sendProgress(
        progressAction: String,
        replyPackage: String,
        jobId: String,
        appLabel: String,
        unit: String,
        idOf: Map<String, String>,
        done: Int,
        total: Int,
        stage: String,
    ) {
        runCatching {
            sendBroadcast(
                Intent(progressAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    // The same id under both names: §3's panel keys progress on reply_id,
                    // §2a's data door hands the caller a job_id. They are one run.
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra("reply_id", jobId)
                    putExtra("app", appLabel)
                    idOf[stage]?.let { putExtra("item", it) }
                    putExtra("text", "$unit $done/$total — $stage")
                    putExtra("current", done.toLong())
                    putExtra("total", total.toLong())
                    putExtra("unit", unit)
                },
            )
        }.onFailure { Log.w(TAG, "[$jobId] progress broadcast failed: $it") }
    }

    /** A run we asked to stop reports as cancelled, whatever the interrupt surfaced as. */
    private fun terminal(jobId: String, t: Throwable): String =
        if (AutomationJobs.isCancelled(jobId)) {
            "ERROR:cancelled"
        } else {
            "ERROR:${t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName}"
        }

    private fun notification(importing: Boolean): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "自動化データ", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(if (importing) "データを戻しています" else "データを書き出しています")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    /** Every return path now runs after [startForeground], so each one must leave it. */
    private fun stop(startId: Int): Int {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "RindenwaAutomation"
        private const val CHANNEL = "sk_automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val PROGRESS_THROTTLE_MS = 500L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * Returns false when the service could not be started at all, so the provider can close the
         * descriptor it duplicated and answer an error rather than leave the caller waiting for a
         * reply that will never come.
         */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ): Boolean {
            HANDOVER[jobId] = fd
            val intent = Intent(context, AutomationDataService::class.java).apply {
            putExtra(EXTRA_JOB, jobId)
            putExtra(EXTRA_IMPORTING, importing)
            putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
            putExtra(
                AutomationProvider.KEY_REPLY_ACTION,
                extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
            )
            putExtra(
                AutomationProvider.KEY_REPLY_PACKAGE,
                extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
            )
            putExtra(
                AutomationProvider.KEY_PROGRESS_ACTION,
                extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
            )
            }
            return runCatching { context.startForegroundService(intent) }
            .onFailure {
                HANDOVER.remove(jobId)
                Log.w(TAG, "[$jobId] startForegroundService refused: $it")
            }
            .isSuccess
        }
    }
}
