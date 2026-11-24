package org.linphone.shiroikuma

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.linphone.R
import org.xmlpull.v1.XmlPullParser

/**
 * shiroikuma-rindenwa fork — the language of this app alone, chosen in the first section of the
 * 臨電話 UI page (black-yellow-theme skill, “App language”; 白い熊, 2026-11-24).
 *
 * The chosen BCP-47 tag is kept in the default preferences as `sk_app_language` (empty: follow
 * the system), so it travels in the export's appearance category, and it is **asserted again at
 * every start**: EMUI does not keep the system's per-app language across a sideloaded update.
 *
 * Below Android 13 the compatibility layer reaches activities only. Notifications, services and
 * everything else built off the application context would stay in the system's language, so the
 * application's own resources are given the chosen language as well ([applyToApp]).
 */
object SkLanguage {
    private const val KEY = "sk_app_language"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** The chosen tag, or "" for the system's language. */
    fun tag(context: Context): String = SkTheme.prefs(context).getString(KEY, "").orEmpty()

    fun set(context: Context, tag: String) {
        SkTheme.prefs(context).edit().putString(KEY, tag).apply()
        applyToApp(context.applicationContext, tag)
        AppCompatDelegate.setApplicationLocales(locales(tag))
    }

    /** From `Application.onCreate`, before anything is drawn or announced. */
    fun reassert(app: Application) {
        val tag = tag(app)
        applyToApp(app, tag)
        if (tag.isNotEmpty() && AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) {
            AppCompatDelegate.setApplicationLocales(locales(tag))
        }
        // A configuration change hands the application's resources the system's language again.
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = applyToApp(app, tag(app))

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }

    private fun locales(tag: String): LocaleListCompat =
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)

    @Suppress("DEPRECATION")
    private fun applyToApp(app: Context, tag: String) {
        // From Android 13 on the system does this itself, for every context of the app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val wanted = if (tag.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
        val resources = app.resources
        if (resources.configuration.locales[0] == wanted) return
        val config = Configuration(resources.configuration)
        config.setLocale(wanted)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * The languages the app declares, read from `res/xml/locales_config.xml` itself, so the list
     * offered and the declaration cannot drift apart. Upstream writes region tags the resource
     * way (`pt-rBR`); they are turned into BCP-47 here.
     */
    fun shippedTags(context: Context): List<String> {
        val tags = ArrayList<String>()
        val parser = context.resources.getXml(R.xml.locales_config)
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")?.let { tags += it.replace("-r", "-") }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return tags.distinct()
    }

    /** A language's name in that language, first letter raised. */
    fun ownName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** The country or region of [tag] in its own language, or "" when the tag names none. */
    fun ownRegion(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayCountry(locale)
    }

    /** What the row on the page shows: the chosen language's own name, or [systemLabel]. */
    fun currentName(context: Context, systemLabel: String): String =
        tag(context).let { if (it.isEmpty()) systemLabel else ownName(it) }
}
