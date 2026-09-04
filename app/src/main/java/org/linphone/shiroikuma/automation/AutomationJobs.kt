package org.linphone.shiroikuma.automation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The jobs the data door has started, and the flag each of them watches to stop.
 *
 * One at a time is not enforced here — a caller asking for two exports at once is asking for two
 * files, and refusing that is [AutomationDataService]'s business. What this owns is the mapping
 * from the id a caller was handed to a cancellation it can act on, which must outlive the binder
 * call that created it and be reachable from a service that never saw the caller.
 *
 * ## The one local addition to the family's version
 *
 * [SkEximport][org.linphone.shiroikuma.SkEximport] cancels by **thread interrupt** — its
 * `checkCancelled` tests `Thread.currentThread().isInterrupted` at each entry boundary, which is
 * what the Export/Import panel's own Cancel button already uses. So a job registers its worker
 * thread here and [cancel] interrupts it as well as raising the flag. Both halves matter: the flag
 * is what decides the terminal reply is `ERROR:cancelled` rather than a generic failure, and the
 * interrupt is what actually unwinds a write loop that is between entries.
 */
object AutomationJobs {

    private val cancelled = ConcurrentHashMap<String, Boolean>()
    private val workers = ConcurrentHashMap<String, Thread>()

    fun begin(): String = UUID.randomUUID().toString().also { cancelled[it] = false }

    /** The thread doing the work, so a later [cancel] can interrupt it out of the write loop. */
    fun register(jobId: String, thread: Thread) {
        workers[jobId] = thread
    }

    /**
     * Ask a job to stop. A no-op for an id that is finished or was never real.
     *
     * Deliberately silent: a cancel arriving after the work completed is the normal race, not an
     * error, and answering it as one would make every well-behaved caller look broken.
     */
    fun cancel(jobId: String?) {
        val id = jobId ?: return
        cancelled.computeIfPresent(id) { _, _ -> true }
        workers[id]?.interrupt()
    }

    /** Polled at write boundaries — never mid-write, so a cancelled archive is never half a file. */
    fun isCancelled(jobId: String): Boolean = cancelled[jobId] == true

    fun finish(jobId: String) {
        cancelled.remove(jobId)
        workers.remove(jobId)
    }
}
