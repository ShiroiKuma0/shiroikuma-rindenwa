package org.linphone.shiroikuma

import android.content.Context
import androidx.annotation.AnyThread
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * shiroikuma-rindenwa fork — the fork's own diagnostics trace: a few plain lines a day, written
 * where adb can read them without any permission.
 *
 * **One file per kind per day**, in the app's own diag directory —
 * `/sdcard/Android/data/shiroikuma.rindenwa/files/diag/rindenwa-<kind>_<yyyy-MM-dd>.txt`. Pull one
 * with
 * ```
 * adb pull /sdcard/Android/data/shiroikuma.rindenwa/files/diag/
 * ```
 *
 * **Never a `.log` extension** (the `diagnostics-ui-settings` rule, learned the hard way on
 * 2026-11-04): EMUI's Phone Manager runs a nightly auto-clean while the phone charges and deletes
 * every `*.log` under `Android/data` as junk — a drive log with the very fault to analyse in it was
 * lost that way, while the `.tsv` files beside it survived. Everything here is `.txt`.
 *
 * **Nothing is held open.** Each line opens the file, appends and closes, so a file deleted under
 * us — by that clean-up, or by 白い熊 — is simply recreated on the next line instead of every
 * further line vanishing into an unlinked inode.
 *
 * What is recorded is decided by [SkDiag]: every diagnostic sits behind its own switch and is off
 * until 白い熊 turns it on. The one exception is the keep-alive door's own answers ([SkKeepAlive]),
 * one short line per call, always on — they are the receipt of a contract another app relies on,
 * and a switch that was off at 03:00 would have recorded nothing about the call that never came.
 * Retention is [SkDiag]'s too (keep everything, or keep N days); a single day's file is capped
 * here so a runaway logger cannot fill the phone.
 */
object SkTrace {

    /** The keep-alive door. One line per call from 自由作業盤's 生存監視. */
    const val KIND_KEEP_ALIVE = "keepalive"

    @AnyThread
    @Synchronized
    fun log(context: Context, kind: String, line: String) {
        try {
            val directory = directory(context) ?: return
            val today = LocalDate.now()
            val file = File(directory, "$FILE_PREFIX$kind$DAY_SEPARATOR${today.format(DAY)}$EXT")
            // A day's first line is also when the old days go: once a day, off any hot path.
            if (file.length() < MAX_FILE_BYTES) {
                file.appendText("${LocalDateTime.now().format(STAMP)}  $line\n")
            }
        } catch (ignored: Throwable) {
            // A trace that cannot be written must never take the thing it was tracing down with it.
        }
    }

    /**
     * The diag directory, created on demand. External files first — that is the path adb reaches —
     * falling back to internal storage when no external volume is mounted.
     */
    @AnyThread
    fun directory(context: Context): File? {
        val base = try {
            context.getExternalFilesDir(null)
        } catch (ignored: Throwable) {
            null
        } ?: context.filesDir ?: return null
        val directory = File(base, DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            return null
        }
        return directory
    }

    /** Every recorded file, oldest name first — what the inventory, export and purge work on. */
    @AnyThread
    fun files(context: Context): List<File> {
        val directory = directory(context) ?: return emptyList()
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(EXT) }
            .sortedBy { it.name }
    }

    /** The day a file belongs to, read out of its name; null when the name says nothing. */
    @AnyThread
    fun dayOf(file: File): LocalDate? {
        val day = file.name.removeSuffix(EXT).substringAfterLast(DAY_SEPARATOR, "")
        return runCatching { LocalDate.parse(day, DAY) }.getOrNull()
    }

    private const val DIRECTORY = "diag"
    private const val FILE_PREFIX = "rindenwa-"
    private const val DAY_SEPARATOR = "_"

    /** `.txt`, never `.log` — see the class comment. */
    private const val EXT = ".txt"
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}
