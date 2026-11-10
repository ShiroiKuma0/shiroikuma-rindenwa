package org.linphone.shiroikuma

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.linphone.BuildConfig
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — what the app records about itself, and the Diagnostics section of the
 * 臨電話 UI page that drives it.
 *
 * Built to the house `diagnostics-ui-settings` rules: **every diagnostic behind its own switch and
 * off by default** — nothing is recorded until 白い熊 turns it on — files in the app's own diag
 * directory through [SkTrace], an inventory, a retention setting, export, share and a purge behind
 * a warning, plus an ongoing notification while anything is recording, carrying Mark and
 * 「Turn all off」.
 *
 * The one line that is written without a switch is the keep-alive door's own answer
 * ([SkKeepAliveReceiver]): it is the receipt of a contract 自由作業盤's 生存監視 depends on, one
 * short line every 15 minutes, and the whole point of it is to still be there after a night when
 * nothing rang.
 */
enum class SkDiagKind(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
) {
    /** Why the phone is not reachable: every REGISTER answer, including the SIP code. */
    REGISTRATION("registration", R.string.sk_diag_registration, R.string.sk_diag_registration_desc),

    /** Why it did not ring: a line per incoming call, how it was announced and how it ended. */
    CALLS("calls", R.string.sk_diag_calls, R.string.sk_diag_calls_desc),

    /** Why it was dead: process starts, the keep-alive service going up and down. */
    SERVICE("service", R.string.sk_diag_service, R.string.sk_diag_service_desc),
    ;

    val prefsKey: String
        get() = "on_$key"
}

object SkDiag {

    /** What is on the phone: counted off the UI thread, shown in the Recorded data group. */
    class Inventory(val files: Int, val bytes: Long, val firstDay: LocalDate?, val lastDay: LocalDate?) {
        val empty: Boolean
            get() = files == 0

        fun describe(context: Context): String {
            if (empty) return context.getString(R.string.sk_diag_stored_empty)
            val span = when {
                firstDay == null || lastDay == null -> ""
                firstDay == lastDay -> firstDay.toString()
                else -> context.getString(R.string.sk_diag_day_span, firstDay.toString(), lastDay.toString())
            }
            return context.getString(R.string.sk_diag_stored_value, files, formatBytes(bytes), span)
        }
    }

    // ------------------------------------------------------------------ the switches

    @AnyThread
    fun enabled(context: Context, kind: SkDiagKind): Boolean =
        prefs(context).getBoolean(kind.prefsKey, false)

    @AnyThread
    fun anyEnabled(context: Context): Boolean = SkDiagKind.entries.any { enabled(context, it) }

    /**
     * Turn one on or off. A log that starts says so in its own file, with the build that wrote it:
     * a file read weeks later has to be able to say what was recording and from which version.
     */
    @AnyThread
    fun setEnabled(context: Context, kind: SkDiagKind, value: Boolean) {
        prefs(context).edit().putBoolean(kind.prefsKey, value).apply()
        if (value) {
            SkTrace.log(context, kind.key, "diag ${kind.key} on, ${BuildConfig.VERSION_NAME}")
        } else {
            SkTrace.log(context, kind.key, "diag ${kind.key} off")
        }
        refreshNotification(context)
    }

    /** Record a line, if that diagnostic is on. The call site never has to ask first. */
    @AnyThread
    fun log(context: Context, kind: SkDiagKind, line: String) {
        if (!enabled(context, kind)) return
        SkTrace.log(context, kind.key, line)
    }

    // ------------------------------------------------------------------ retention

    /** Days to keep; 0 — the default — keeps everything. */
    @AnyThread
    fun keepDays(context: Context): Int = prefs(context).getInt(KEY_KEEP_DAYS, 0)

    @AnyThread
    fun setKeepDays(context: Context, days: Int) {
        prefs(context).edit().putInt(KEY_KEEP_DAYS, days.coerceAtLeast(0)).apply()
    }

    /** Drop whole days that have fallen out of the retention window. Runs at start and on leaving the page. */
    @AnyThread
    fun cleanUp(context: Context) {
        val days = keepDays(context)
        if (days <= 0) return
        val oldest = LocalDate.now().minusDays(days.toLong())
        for (file in SkTrace.files(context)) {
            val day = SkTrace.dayOf(file) ?: continue
            if (day.isBefore(oldest)) {
                file.delete()
            }
        }
    }

    /** Called once per process: the clean-up, the recording notification, and a line saying we live. */
    @AnyThread
    fun onProcessStart(context: Context) {
        runCatching { cleanUp(context) }
        runCatching { log(context, SkDiagKind.SERVICE, "process started, ${BuildConfig.VERSION_NAME}") }
        runCatching { refreshNotification(context) }
    }

    // ------------------------------------------------------------------ the recorded files

    @AnyThread
    fun inventory(context: Context): Inventory {
        val files = SkTrace.files(context)
        val days = files.mapNotNull { SkTrace.dayOf(it) }.sorted()
        return Inventory(files.size, files.sumOf { it.length() }, days.firstOrNull(), days.lastOrNull())
    }

    /**
     * Copy everything recorded into a new, stamped subdirectory of the tree 白い熊 picked, so two
     * exports on the same day never land on top of each other.
     */
    @AnyThread
    fun export(context: Context, tree: Uri): String {
        val files = SkTrace.files(context)
        if (files.isEmpty()) return context.getString(R.string.sk_diag_stored_empty)
        val parent = DocumentFile.fromTreeUri(context, tree)
            ?: return context.getString(R.string.sk_diag_export_failed, "no directory")
        val name = "${LocalDateTime.now().format(EXPORT_STAMP)}_rindenwa-diag"
        val directory = parent.createDirectory(name)
            ?: return context.getString(R.string.sk_diag_export_failed, name)
        var copied = 0
        for (file in files) {
            val target = directory.createFile(MIME, file.name) ?: continue
            val written = runCatching {
                context.contentResolver.openOutputStream(target.uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
            }.isSuccess
            if (written) copied++
        }
        return context.getString(R.string.sk_diag_export_done, copied, name)
    }

    /** Share the recorded files through the app's own FileProvider. Null when there is nothing. */
    @AnyThread
    fun shareIntent(context: Context): Intent? {
        val files = SkTrace.files(context)
        if (files.isEmpty()) return null
        val authority = context.getString(R.string.file_provider)
        val uris = ArrayList<Uri>()
        for (file in files) {
            val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull()
            if (uri != null) uris.add(uri)
        }
        if (uris.isEmpty()) return null
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = MIME
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Delete every recorded file. Returns what was there, for the toast after the warning. */
    @AnyThread
    fun purge(context: Context): Inventory {
        val before = inventory(context)
        for (file in SkTrace.files(context)) {
            file.delete()
        }
        return before
    }

    /**
     * A numbered mark in every log that is recording — pressed on the notification at the moment
     * something goes wrong, so the line can be found again afterwards.
     */
    @AnyThread
    fun mark(context: Context): Int {
        val store = prefs(context)
        val number = store.getInt(KEY_MARK, 0) + 1
        store.edit().putInt(KEY_MARK, number).apply()
        for (kind in SkDiagKind.entries) {
            if (enabled(context, kind)) {
                SkTrace.log(context, kind.key, "mark #$number")
            }
        }
        return number
    }

    // ------------------------------------------------------------------ the recording notification

    /**
     * Ongoing while anything is recording — 白い熊 must never be recording without knowing it —
     * carrying Mark and 「Turn all off」. Dismissed the moment the last switch goes off.
     */
    @AnyThread
    fun refreshNotification(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!anyEnabled(context)) {
            runCatching { manager.cancel(NOTIFICATION_ID) }
            return
        }
        val recording = SkDiagKind.entries
            .filter { enabled(context, it) }
            .joinToString(", ") { context.getString(it.labelRes) }
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.sk_diag_notification_title),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                },
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.linphone_notification)
                .setContentTitle(context.getString(R.string.sk_diag_notification_title))
                .setContentText(recording)
                .setOngoing(true)
                .setShowWhen(false)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(0, context.getString(R.string.sk_diag_mark), pendingAction(context, SkDiagReceiver.ACTION_MARK))
                .addAction(0, context.getString(R.string.sk_diag_all_off), pendingAction(context, SkDiagReceiver.ACTION_ALL_OFF))
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun pendingAction(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, SkDiagReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ------------------------------------------------------------------ plumbing

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f kB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** The diag directory as a path 白い熊 can type into adb. */
    fun directoryPath(context: Context): String =
        SkTrace.directory(context)?.absolutePath ?: ""

    private const val PREFS_FILE = "sk_diag" // device-local, never exported
    private const val KEY_KEEP_DAYS = "keep_days"
    private const val KEY_MARK = "mark_counter"
    private const val CHANNEL_ID = "sk_diag_recording"

    /** Clear of every id upstream's NotificationsManager hands out. */
    private const val NOTIFICATION_ID = 9100
    private const val MIME = "text/plain"

    private val EXPORT_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
}
