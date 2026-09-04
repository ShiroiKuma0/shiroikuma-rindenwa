package org.linphone.shiroikuma.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.linphone.shiroikuma.SkAutomation
import org.linphone.shiroikuma.SkEximport

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared
 * secret, which cannot survive the wipe that this feature exists to recover from. A provider gets
 * the caller's identity from the framework for free — see [AutomationCallers] for what is actually
 * checked and why a package-name prefix would have been worse than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a file descriptor the caller
 * opened, and the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A
 * file this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing
 * (応用管理, 2026-09-04). A descriptor is also a capability that **expires when it is closed** —
 * precisely the property a URI grant failed to give us on the 地図 contract, where the revoke needed
 * a five-minute floor.
 *
 * It also means this app no longer needs `MANAGE_EXTERNAL_STORAGE` to be backed up this way. That
 * permission is still declared, because §1's `path` extra hands the receiver an absolute directory,
 * but nothing on *this* path depends on it.
 *
 * ## `import` lives here and nowhere else
 *
 * It never gets a broadcast action. An import overwrites this app's accounts, history and settings,
 * and [org.linphone.shiroikuma.SkStateExportReceiver] is `exported="true"` with no permission — an
 * import there would let any app on the phone wipe this one.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> {
                Log.i(TAG, "$method refused: ${verdict.why}")
                return fail(verdict.why)
            }

            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then the app's own switches — a token is ignored unless this app asks for one.
        SkAutomation.refuse(ctx, extras?.getString(KEY_TOKEN))?.let {
            Log.i(TAG, "$method refused: $it")
            return fail(it)
        }
        Log.i(TAG, "$method from $callingPackage")

        return try {
            when (method) {
                METHOD_DESCRIBE -> ok(describe(ctx))
                METHOD_EXPORT -> start(ctx, extras, importing = false)
                METHOD_IMPORT -> start(ctx, extras, importing = true)
                METHOD_CANCEL -> {
                    AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                    ok("OK:cancelled")
                }

                else -> fail("ERROR:unknown method: $method")
            }
        } catch (e: Exception) {
            // Never across the binder. See the class doc.
            fail("ERROR:${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive (応用管理, 2026-09-04).
     *
     * Built with [JSONObject] rather than string concatenation so a category label containing a
     * quote cannot produce a header the caller fails to parse.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)

        @Suppress("DEPRECATION")
        val code = pkg.versionCode
        val contains = JSONArray()
        for (cat in SkEximport.Cat.topLevel().filter { it.default }) {
            contains.put(ctx.getString(cat.labelRes))
        }
        val header = JSONObject()
            .put("app_id", ctx.packageName)
            .put("version_code", code)
            .put("version_name", pkg.versionName.orEmpty())
            .put("format", FORMAT)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            // Import writes .linphonerc sections, the SDK databases and our own preference keys
            // straight into filesDir — none of which needs the Core to have started once. The
            // restore order install → do NOT launch → import → force-stop is honoured as written.
            .put("requires_launch_first", false)
            .put("contains", contains)
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when [call] returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        val started = AutomationDataService.start(ctx, jobId, dup, importing, extras)
        if (!started) {
            // Nothing else will ever close this copy, and a leaked descriptor holds the caller's
            // file open — it can then neither checksum nor encrypt it.
            runCatching { dup.close() }
            AutomationJobs.finish(jobId)
            return fail("ERROR:cannot start background work")
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        private const val TAG = "RindenwaAutomation"

        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /** This app's archive format. Bumped when an older build could no longer read what we write. */
        const val FORMAT = 1

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
