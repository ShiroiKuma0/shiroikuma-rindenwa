package org.linphone.shiroikuma

import android.app.ActivityOptions
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.View
import org.linphone.core.tools.Log

/**
 * shiroikuma-rindenwa fork — the doors into the two sister apps this one shares a screen with.
 *
 * 臨電話, 電話 (shiroikuma-denwa) and 連絡先 (shiroikuma-renrakusaki) show one bottom bar —
 * Favorites, Contacts, 電話, 臨電話 — and each owns the tabs that are its own: 連絡先 the first two,
 * 電話 the SIM call history, this app the SIP call history. A tab that belongs to another app
 * opens that app on that tab, with no animation, so it reads as a tab change and not as a launch.
 * Nobody finishes: all three stay alive behind each other and the swap stays warm.
 *
 * The intents are the ones the other two already answer (連絡先 1.6.0+122, 電話 +103), as their
 * chats spelled them out on 2026-11-17. Everything is addressed to an explicit component: a bare
 * ACTION_VIEW or ACTION_SENDTO raises the app chooser, and on this phone the chooser once cost
 * 白い熊 a default-app setting.
 */
object SkSisterApps {
    private const val TAG = "[SK Sister Apps]"

    const val RENRAKUSAKI = "shiroikuma.renrakusaki"
    const val DENWA = "shiroikuma.denwa"
    const val MESSEJI = "shiroikuma.messeji"

    private const val RENRAKUSAKI_MAIN = "org.fossify.contacts.activities.MainActivity"
    private const val DENWA_MAIN = "org.fossify.phone.activities.MainActivity"

    /** Shared by all three apps: which tab the receiving app should land on. */
    private const val EXTRA_OPEN_TAB = "shiroikuma_open_tab"

    /** What the other two send here: land on the call history, whatever was left open. */
    const val EXTRA_OPEN_HISTORY = "shiroikuma_open_history"

    // The commons tab masks the other two apps use.
    const val TAB_CONTACTS = 1
    const val TAB_FAVORITES = 2
    const val TAB_CALL_HISTORY = 4

    /** The installed package for [base] — release first, then its debug build — or null. */
    @JvmStatic
    fun installed(context: Context, base: String): String? =
        listOf(base, "$base.debug").firstOrNull {
            try {
                context.packageManager.getPackageInfo(it, 0)
                true
            } catch (e: Exception) {
                false
            }
        }

    /** For the bottom bar: a tab whose app is missing is not shown at all. */
    @JvmStatic
    fun tabVisibility(context: Context, base: String): Int =
        if (installed(context, base) != null) View.VISIBLE else View.GONE

    @JvmStatic
    fun openFavorites(context: Context) = openTab(context, RENRAKUSAKI, RENRAKUSAKI_MAIN, TAB_FAVORITES)

    @JvmStatic
    fun openContacts(context: Context) = openTab(context, RENRAKUSAKI, RENRAKUSAKI_MAIN, TAB_CONTACTS)

    @JvmStatic
    fun openDenwa(context: Context) = openTab(context, DENWA, DENWA_MAIN, TAB_CALL_HISTORY)

    private fun openTab(context: Context, base: String, activity: String, tab: Int) {
        val pkg = installed(context, base) ?: return
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(pkg, activity)
            .putExtra(EXTRA_OPEN_TAB, tab)
            // CLEAR_TOP | SINGLE_TOP: the extra is read in onNewIntent of the running instance.
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        start(context, intent, animated = false)
    }

    /** The contact page of a contact 連絡先 holds, by the private id its provider gave us. */
    fun viewContact(context: Context, contactId: Int) {
        val pkg = installed(context, RENRAKUSAKI) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setPackage(pkg)
            .setDataAndType(ContactsContract.Contacts.CONTENT_LOOKUP_URI, "vnd.android.cursor.dir/person")
            .putExtra("contact_id", contactId)
            .putExtra("is_private", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent, animated = true)
    }

    /**
     * 連絡先's editor for a new contact, with [number] already filled in — always in its
     * international form, `+420601123456`: a provider hands a Czech caller over as nine bare
     * digits, and a contact saved that way is not found again from abroad or by another line.
     *
     * [surname] goes into the surname field: a name found online ([SkOnlineId]) is a company more
     * often than a person, and 白い熊 files those under the surname. It travels as a
     * `StructuredName` data row with only the family name set, which is how Android hands a name
     * over in parts; `Insert.NAME` is one undivided string, and 連絡先 puts that in the first name.
     */
    fun newContact(context: Context, number: String, surname: String? = null) {
        val pkg = installed(context, RENRAKUSAKI) ?: return
        val international = SkNumbers.international(number)?.let { "+$it" } ?: number
        val intent = Intent(Intent.ACTION_INSERT)
            .setPackage(pkg)
            .setType("vnd.android.cursor.dir/contact")
            .putExtra(ContactsContract.Intents.Insert.PHONE, international)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!surname.isNullOrEmpty()) {
            val row = ContentValues()
            row.put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            )
            row.put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, surname)
            intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(row))
        }
        start(context, intent, animated = true)
    }

    /** The conversation with [number] in the sister messenger. */
    fun openMessages(context: Context, number: String) {
        val pkg = installed(context, MESSEJI) ?: return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent, animated = true)
    }

    private fun start(context: Context, intent: Intent, animated: Boolean) {
        try {
            if (animated) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    intent,
                    ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
                )
            }
        } catch (e: Exception) {
            Log.e("$TAG Could not open [${intent.`package` ?: intent.component}]: $e")
        }
    }
}
