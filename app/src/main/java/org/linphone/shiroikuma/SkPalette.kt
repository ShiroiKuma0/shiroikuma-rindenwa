package org.linphone.shiroikuma

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import com.google.android.material.color.SkColorLoaderBridge
import java.util.WeakHashMap

/**
 * shiroikuma-rindenwa fork — how a colour role reaches the screen.
 *
 * No XML of this app holds a colour: a layout, a drawable or a theme names a **role**
 * (`@color/sk_…`, a theme attribute pointing at one, or one of upstream's own colour names), and
 * the values come from the role table ([SkRoles], [SkTheme]) at run time. This object lays them
 * over the resources with a [ResourcesLoader] (Android 11 and later): a small resource table,
 * built in memory, that replaces the value of every role resource. Upstream's layouts are thereby
 * re-coloured without being edited, which is what keeps the rebase onto every upstream commit
 * free of conflicts (白い熊, 2026-11-21: the "mixed" way).
 *
 * - [apply] must run on a context **before anything is inflated from it**: the application in its
 *   `onCreate`, every activity from `getTheme()`. It is cheap when nothing changed.
 * - [changed] is called whenever a colour is stored. Each `Resources` then gets a fresh loader
 *   the next time [apply] sees it; an activity that was built from the old colours asks
 *   [isStale] in `onResume` and recreates itself.
 * - Below Android 11 there is no loader, and the placeholders of `sk_colors.xml` show.
 */
object SkPalette {
    private const val TAG = "SkPalette"

    private class Applied(val loader: ResourcesLoader, val generation: Int)

    private val applied = WeakHashMap<Resources, Applied>()

    @Volatile
    private var generation = 1

    fun changed() {
        generation++
    }

    fun generation(): Int = generation

    /** True when [context]'s resources still carry the colours of an earlier generation. */
    fun isStale(builtAt: Int): Boolean = builtAt != generation

    /**
     * Lays the roles over [context]'s resources, and over the application's as well — those are
     * a separate object, read by notifications and services, and must not keep old colours while
     * an activity already shows the new ones.
     */
    @Synchronized
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        applyTo(context)
        val application = context.applicationContext
        if (application != null && application !== context) applyTo(application)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun applyTo(context: Context) {
        val resources = context.resources
        val current = applied[resources]
        if (current != null && current.generation == generation) return
        try {
            val loader = SkColorLoaderBridge.create(context, SkRoles.resourceColors(context)) ?: return
            if (current != null) resources.removeLoaders(current.loader)
            resources.addLoaders(loader)
            applied[resources] = Applied(loader, generation)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not lay the colour roles over the resources", e)
        }
    }

    /**
     * What the platform drew before any code ran — the window's ground and the system bars, black
     * in the theme — repainted from their roles.
     */
    fun paintWindow(activity: Activity) {
        val window = activity.window ?: return
        window.setBackgroundDrawable(ColorDrawable(SkTheme.color(activity, SkSlot.BACKGROUND)))
        @Suppress("DEPRECATION")
        window.statusBarColor = SkRoles.color(activity, "sk_bar_background")
        @Suppress("DEPRECATION")
        window.navigationBarColor = SkTheme.color(activity, SkSlot.BACKGROUND)
    }
}
