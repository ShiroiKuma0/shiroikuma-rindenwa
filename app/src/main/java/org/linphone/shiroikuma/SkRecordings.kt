package org.linphone.shiroikuma

import androidx.annotation.WorkerThread
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — whether a call in the history was recorded.
 *
 * A call log carries no pointer to its recording; the recording carries the call. Its file name
 * is `call_recording_<peer URI>_on_<epoch millis>.<ext>` (see
 * [LinphoneUtils.getRecordingFilePathForAddress]), written when the call was placed or answered,
 * so a recording belongs to a history record when the peer matches and its time falls inside the
 * call. The directory is listed once and remembered for a few seconds — a history of several
 * hundred rows would otherwise list it several hundred times.
 */
object SkRecordings {
    private const val CACHE_MS = 5_000L
    private const val SLACK_MS = 15_000L

    private var loadedAt = 0L
    private var recordings: List<Pair<String, Long>> = emptyList()

    @WorkerThread
    @Synchronized
    private fun all(): List<Pair<String, Long>> {
        val now = System.currentTimeMillis()
        if (now - loadedAt < CACHE_MS) return recordings

        val header = LinphoneUtils.RECORDING_FILE_NAME_HEADER
        val separator = LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR
        recordings = try {
            FileUtils.getFileStorageDir(isRecording = true).listFiles().orEmpty().mapNotNull { file ->
                val name = file.name
                if (!name.startsWith(header)) return@mapNotNull null
                val at = name.lastIndexOf(separator)
                if (at < header.length) return@mapNotNull null
                val uri = name.substring(header.length, at)
                val millis = name.substring(at + separator.length).substringBefore('.').toLongOrNull()
                    ?: return@mapNotNull null
                uri to millis
            }
        } catch (e: Exception) {
            emptyList()
        }
        loadedAt = now
        return recordings
    }

    /** True when a recording of [peerUri] was started during the call that began at [startSeconds]. */
    @WorkerThread
    fun has(peerUri: String, startSeconds: Long, durationSeconds: Int): Boolean {
        val from = startSeconds * 1000 - SLACK_MS
        val to = (startSeconds + durationSeconds) * 1000 + SLACK_MS
        return all().any { (uri, millis) -> uri == peerUri && millis in from..to }
    }
}
