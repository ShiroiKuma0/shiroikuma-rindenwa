package org.linphone.shiroikuma

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.RegistrationState
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — the call door: how the sister apps place a call through this one.
 *
 * The address book (連絡先) shows a 臨N badge beside the SIM badges on every number, and lets a
 * swipe be set to "call through 臨1". For that it needs two things from here: which accounts
 * exist and what number each carries, and a way to say "call this, through that one".
 *
 * `ContentResolver.call()` on `content://shiroikuma.rindenwa.calls`:
 *
 * - **`accounts`** → `{ "result": "OK", "accounts": "<JSON array>" }`, each element
 *   `{"number": int (-1 when unset), "badge_color": ARGB int, "badge_text_color": ARGB int,
 *   "identity": "sip:user@domain", "name": "…", "registered": bool, "default": bool,
 *   "callback": bool}` — `callback` says the account's provider can set up a callback.
 * - **`dial`**, extras `{ "to": "<number or sip: URI>", "account": "<臨 number, or identity>" }`
 *   (`account` absent = the selected account) → `{ "result": "OK" }` or
 *   `{ "result": "ERROR:<why>" }`. The call is placed and this app's own call screen comes up.
 * - **`callback`**, same extras as `dial` → `{ "result": "OK" | "ERROR:<why>", "message": "<a
 *   line to show>" }`. Orders the provider's callback ([SkOdorikCallback]) through an account
 *   whose `callback` flag in `accounts` is true; this app then rings with the first leg. It
 *   **blocks for the length of an HTTPS request** (up to ~20 s), so never call it on a main thread.
 * - **`silence_ringer`**, no extras → `{ "result": "OK" }` when a ringing call was silenced,
 *   `{ "result": "OK:nothing ringing" }` when none rang unsilenced. Ringtone and vibration stop;
 *   the call itself is untouched ([SkRinger]). Open to the apps let in to dial **and** to the
 *   callers of the data door (自由作業盤): see [SkRinger] for why the volume button has to come
 *   round this way.
 *
 * ## Why a provider, and why its own
 *
 * A provider, because the framework vouches for who is calling; an activity intent tells the
 * receiver nothing about its sender, and this door **places calls unasked**. Its own, and not the
 * automation provider next to it, because that one is pinned by signing certificate to the two
 * backup apps and can export everything this app holds — admitting the address book there to let
 * it dial would hand it the data door as well.
 *
 * ## Who is let in
 *
 * The apps 白い熊 allows on the 臨電話 UI page ([SkCallDoor]) — the address book and the SIM
 * dialer by default. The package name the framework reports is confirmed against the calling uid.
 * A refusal is answered, never thrown: the caller shows it, and an exception across a binder
 * would show our stack trace instead.
 */
class SkCallsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")
        val caller = callingPackage
        if (method == METHOD_SILENCE_RINGER) {
            // Taking a ring away is not placing a call: besides the apps let in to dial, any app
            // the data door identifies by its signing certificate may ask — 自由作業盤, whose key
            // grabber is the one holding the volume button.
            val allowed = SkCallDoor.isAllowed(ctx, caller, Binder.getCallingUid()) ||
                org.linphone.shiroikuma.automation.AutomationCallers.verify(ctx, caller) is
                org.linphone.shiroikuma.automation.AutomationCallers.Verdict.Allowed
            if (!allowed) {
                Log.i(TAG, "$method refused for $caller")
                return fail("ERROR:not allowed")
            }
            return fail(if (SkRinger.silenceFromOutside(caller.orEmpty())) "OK" else "OK:nothing ringing")
        }
        if (!SkCallDoor.isAllowed(ctx, caller, Binder.getCallingUid())) {
            Log.i(TAG, "$method refused for $caller")
            return fail("ERROR:not allowed")
        }
        return try {
            when (method) {
                METHOD_ACCOUNTS -> accounts(ctx)
                METHOD_DIAL -> dial(ctx, extras)
                METHOD_CALLBACK -> callback(ctx, extras)
                else -> fail("ERROR:unknown method: $method")
            }
        } catch (e: Exception) {
            fail("ERROR:${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}")
        }
    }

    private fun accounts(ctx: Context): Bundle {
        var json: String? = null
        val done = CountDownLatch(1)
        coreContext.postOnCoreThread { core ->
            try {
                val array = JSONArray()
                val default = core.defaultAccount
                for (account in core.accountList) {
                    val identity = account.params.identityAddress
                    val uri = identity?.asStringUriOnly().orEmpty()
                    val number = SkAccountNumbers.numberOf(ctx, uri)
                    val (ground, text) = SkAccountNumbers.colors(ctx, number)
                    array.put(
                        JSONObject()
                            .put("number", number ?: -1)
                            .put("badge_color", ground)
                            .put("badge_text_color", text)
                            .put("identity", uri)
                            .put(
                                "name",
                                identity?.displayName?.takeIf { it.isNotEmpty() }
                                    ?: identity?.username.orEmpty()
                            )
                            .put("registered", account.state == RegistrationState.Ok)
                            .put("default", account == default)
                            .put("callback", SkOdorikCallback.isOdorik(account))
                    )
                }
                json = array.toString()
            } finally {
                done.countDown()
            }
        }
        if (!done.await(TIMEOUT_S, TimeUnit.SECONDS) || json == null) return fail("ERROR:not ready")
        return Bundle().apply {
            putString(KEY_RESULT, "OK")
            putString(KEY_ACCOUNTS, json)
        }
    }

    private fun dial(ctx: Context, extras: Bundle?): Bundle {
        val to = extras?.getString(KEY_TO)?.trim().orEmpty()
        if (to.isEmpty()) return fail("ERROR:no number")
        val wanted = extras?.getString(KEY_ACCOUNT)?.trim().orEmpty()

        var result = "ERROR:not ready"
        val done = CountDownLatch(1)
        coreContext.postOnCoreThread { core ->
            try {
                val account = when {
                    wanted.isEmpty() -> core.defaultAccount
                    else -> {
                        val identity = wanted.toIntOrNull()
                            ?.let { SkAccountNumbers.identityOf(ctx, it) } ?: wanted
                        core.accountList.find {
                            it.params.identityAddress?.asStringUriOnly() == identity
                        }
                    }
                }
                if (account == null) {
                    result = "ERROR:no such account"
                    return@postOnCoreThread
                }
                val address = core.interpretUrl(to, LinphoneUtils.applyInternationalPrefix(account))
                if (address == null) {
                    result = "ERROR:not a number"
                    return@postOnCoreThread
                }
                coreContext.startAudioCall(
                    address,
                    localAddress = account.params.identityAddress,
                    skipNetworkReachabilityTest = true
                )
                result = "OK"
            } catch (e: Exception) {
                result = "ERROR:${e.message ?: e.javaClass.simpleName}"
            } finally {
                done.countDown()
            }
        }
        done.await(TIMEOUT_S, TimeUnit.SECONDS)
        Log.i(TAG, "dial from $callingPackage: $result")
        return Bundle().apply { putString(KEY_RESULT, result) }
    }

    private fun callback(ctx: Context, extras: Bundle?): Bundle {
        val to = extras?.getString(KEY_TO)?.trim().orEmpty()
        if (to.isEmpty()) return fail("ERROR:no number")
        val wanted = extras?.getString(KEY_ACCOUNT)?.trim().orEmpty()
        val outcome = SkOdorikCallback.orderBlocking(ctx, wanted, to)
        Log.i(TAG, "callback from $callingPackage: ${outcome.code}")
        return Bundle().apply {
            putString(KEY_RESULT, outcome.code)
            putString(KEY_MESSAGE, outcome.message)
        }
    }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    companion object {
        private const val TAG = "RindenwaCalls"
        private const val TIMEOUT_S = 3L

        const val METHOD_ACCOUNTS = "accounts"
        const val METHOD_DIAL = "dial"
        const val METHOD_CALLBACK = "callback"
        const val METHOD_SILENCE_RINGER = "silence_ringer"
        const val KEY_MESSAGE = "message"
        const val KEY_RESULT = "result"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_TO = "to"
        const val KEY_ACCOUNT = "account"
    }
}

/**
 * shiroikuma-rindenwa fork — which apps may use the call door ([SkCallsProvider]).
 *
 * The two that have a reason to: the address book, whose 臨 badges and swipes dial through here,
 * and the SIM dialer. Both are let in by default and each can be switched off on the 臨電話 UI
 * page. A debug build is let in when its release id is.
 */
object SkCallDoor {
    private const val KEY = "sk_call_door_apps"

    /** The apps offered on the UI page, in the order shown. */
    val KNOWN = listOf(SkSisterApps.RENRAKUSAKI, SkSisterApps.DENWA)

    fun allowed(context: Context): Set<String> =
        SkTheme.prefs(context).getStringSet(KEY, null)?.toSet() ?: KNOWN.toSet()

    fun setAllowed(context: Context, pkg: String, on: Boolean) {
        val set = allowed(context).toMutableSet()
        if (on) set += pkg else set -= pkg
        SkTheme.prefs(context).edit().putStringSet(KEY, set).apply()
    }

    fun isAllowed(context: Context, pkg: String?, uid: Int): Boolean {
        if (pkg.isNullOrEmpty()) return false
        // The framework reports the caller's declared package; confirm it against the uid.
        if (context.packageManager.getPackagesForUid(uid)?.contains(pkg) != true) return false
        val set = allowed(context)
        return pkg in set || pkg.removeSuffix(".debug") in set
    }
}
