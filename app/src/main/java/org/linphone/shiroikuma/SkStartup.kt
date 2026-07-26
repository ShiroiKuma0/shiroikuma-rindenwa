package org.linphone.shiroikuma

import android.content.Context

/**
 * shiroikuma-rindenwa fork — the way out of the startup assistant.
 *
 * On a clean install (or after clearing app data) upstream bounces straight into the account
 * assistant and will not let you reach the main screen until an account exists. That makes it
 * impossible to restore a backup: the accounts live *in* the backup, and the Export/Import panel
 * sits behind the UI page, which sits behind the main screen.
 *
 * So the landing screen carries a "Skip" exit. Tapping it records the flag below, and
 * [org.linphone.ui.main.MainActivity] stops auto-launching the assistant — long enough to open
 * 白い熊 臨電話 UI → Export / Import and restore.
 *
 * Device-local by design, in its own preference file: it is a transient "I am mid-restore" note,
 * not a setting, so it must never travel inside an export ZIP and must be wiped by the very
 * data-clear it exists to survive.
 */
object SkStartup {
    private const val PREFS_FILE = "sk_startup" // never exported
    private const val KEY_ASSISTANT_SKIPPED = "assistant_skipped"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun assistantSkipped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASSISTANT_SKIPPED, false)

    fun setAssistantSkipped(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ASSISTANT_SKIPPED, value).apply()
    }
}
