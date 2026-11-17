package org.linphone.shiroikuma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import java.io.File

/**
 * shiroikuma-rindenwa fork — the 全 tab: every account's calls in one list.
 *
 * Each account's tab in the strip at the top shows that account's calls and nothing else; with
 * several SIP lines, one list of everything under whichever tab happened to be open was confusing
 * (白い熊, 2026-11-17). The list of everything is still wanted, so it has a tab of its own, first
 * in the strip: 全.
 *
 * Choosing 全 changes only what the history shows. The account calls go out through stays the one
 * last chosen — 全 is a view, not a line.
 *
 * The tab's picture is the character 全, drawn in its own settable font slot ([SkSlot.ALL_TAB]),
 * or a picture 白い熊 chooses in its place, kept in this app's files.
 */
object SkAllAccounts {
    private const val KEY = "sk_all_accounts_selected"
    private const val PICTURE = "sk_all_tab_picture"

    const val GLYPH = "全"

    /** Whether the 全 tab is the one in front. Persisted; posted on every change. */
    val selected = MutableLiveData<Boolean>()

    /** Bumped when the tab's look changed (its font slot, its picture). */
    val lookVersion = MutableLiveData(0)

    fun isSelected(context: Context): Boolean {
        selected.value?.let { return it }
        val stored = SkTheme.prefs(context).getBoolean(KEY, false)
        // First read on this process: remember it without waking the observers twice.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            selected.value = stored
        } else {
            selected.postValue(stored)
        }
        return stored
    }

    fun setSelected(context: Context, value: Boolean) {
        if (isSelected(context) == value) return
        SkTheme.prefs(context).edit().putBoolean(KEY, value).apply()
        selected.value = value
    }

    private fun pictureFile(context: Context) = File(context.filesDir, PICTURE)

    fun hasPicture(context: Context): Boolean = pictureFile(context).isFile

    fun picture(context: Context): Bitmap? = try {
        if (hasPicture(context)) BitmapFactory.decodeFile(pictureFile(context).path) else null
    } catch (e: Exception) {
        null
    }

    /** Copy the picture at [uri] in as the tab's picture. False when it is not an image. */
    fun importPicture(context: Context, uri: Uri): Boolean {
        val temp = File(context.cacheDir, "$PICTURE.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: return false
            if (BitmapFactory.decodeFile(temp.path) == null) return false
            temp.copyTo(pictureFile(context), overwrite = true)
            lookChanged()
            true
        } catch (e: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    fun removePicture(context: Context) {
        pictureFile(context).delete()
        lookChanged()
    }

    fun lookChanged() {
        lookVersion.postValue((lookVersion.value ?: 0) + 1)
    }
}
