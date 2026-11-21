package org.linphone.shiroikuma

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the selection of apps that may place calls through this one.
 *
 * It replaced the two fixed switches of the call door on 2026-11-21 (白い熊: “make it an app
 * selection option”): one list of every app on the phone that has a launcher entry, each row the
 * app's own icon, its name and the theme's outlined switch. The sister apps ([SkCallDoor.KNOWN])
 * stand on top, whether installed or not, with anything else already let in; the rest follow by
 * name under a divider. A switch stores at once — there is nothing to confirm, and Done only
 * closes.
 *
 * The rows are toggle rows, so they are plain (no pill) and carry a switch, never a check box.
 * The list is a real [ListView]: a phone holds a few hundred apps, and their icons are fetched
 * only for the rows on screen.
 */
class SkAppPickerDialog(
    private val activity: Activity,
    private val onClosed: () -> Unit,
) {
    private class App(val pkg: String, val label: String, val installed: Boolean)

    private sealed class Row {
        class Heading(val text: String, val divided: Boolean) : Row()

        class Entry(val app: App) : Row()
    }

    private val density = activity.resources.displayMetrics.density
    private val accent = SkRoles.color(activity, "dialog_frame")
    private val ground = SkRoles.color(activity, "dialog_ground")
    private val ink = SkRoles.color(activity, "dialog_text")
    private val dim = SkRoles.color(activity, "dialog_note")
    private val divider = SkRoles.color(activity, "dialog_divider")

    private var top: List<App> = emptyList()
    private var others: List<App> = emptyList()
    private var rows: List<Row> = emptyList()
    private var filter = ""
    private val icons = HashMap<String, Drawable?>()
    private val allowed = SkCallDoor.allowed(activity).toMutableSet()

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()

    fun show() {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(ground)
                setStroke(dp(2f), accent)
            }
        }
        box.addView(
            TextView(activity).apply {
                setText(R.string.sk_call_door_apps)
                textSize = 19f
                setTextColor(SkRoles.color(activity, "dialog_title"))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        box.addView(
            TextView(activity).apply {
                setText(R.string.sk_call_door_apps_note)
                textSize = 13f
                setTextColor(dim)
                setPadding(0, dp(6f), 0, dp(8f))
            },
        )

        val adapter = Adapter()
        val field = EditText(activity).apply {
            setHint(R.string.sk_call_door_apps_filter)
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(dim)
            setSingleLine()
            background = GradientDrawable().apply {
                cornerRadius = 50 * density
                setColor(Color.TRANSPARENT)
                setStroke(dp(1.25f), SkRoles.color(activity, "dialog_field_frame"))
            }
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    filter = s?.toString().orEmpty().trim()
                    rebuild()
                    adapter.notifyDataSetChanged()
                }
            })
        }
        box.addView(field)

        val list = ListView(activity).apply {
            this.adapter = adapter
            this.divider = null
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.heightPixels * LIST_HEIGHT_SHARE).toInt(),
            ).apply { topMargin = dp(8f) }
        }
        box.addView(list)

        lateinit var dialog: AlertDialog
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10f), 0, 0)
        }
        bar.addView(
            AppCompatTextView(activity).apply {
                setText(R.string.sk_done)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(SkRoles.color(activity, "dialog_button_text"))
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = SkMenus.pill(activity, SkRoles.color(activity, "dialog_button_frame"), MAIN_FRAME_DP)
                setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
                setOnClickListener { dialog.dismiss() }
            },
        )
        box.addView(bar)

        dialog = AlertDialog.Builder(activity).setView(box).create()
        dialog.setOnDismissListener { onClosed() }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // The sister apps are known without asking; the phone's list takes a moment to read.
        top = topApps(activity, emptyMap())
        rebuild()
        adapter.notifyDataSetChanged()
        Thread({
            val launchable = runCatching { launchableApps(activity) }.getOrDefault(emptyMap())
            activity.runOnUiThread {
                if (!activity.isFinishing && dialog.isShowing) {
                    top = topApps(activity, launchable)
                    val onTop = top.map { it.pkg }.toSet()
                    others = launchable
                        .filterKeys { it !in onTop && it.removeSuffix(".debug") !in onTop }
                        .map { App(it.key, it.value, true) }
                        .sortedBy { it.label.lowercase() }
                    rebuild()
                    adapter.notifyDataSetChanged()
                }
            }
        }, "sk-app-picker").start()
    }

    /** The sister apps first, then whatever else is let in but is not among the launchable. */
    private fun topApps(context: Context, launchable: Map<String, String>): List<App> {
        val pm = context.packageManager
        val packages = SkCallDoor.KNOWN + allowed.filter { it !in SkCallDoor.KNOWN && it !in launchable }
        return packages.map { pkg ->
            val label = launchable[pkg] ?: runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
            App(pkg, label ?: (FALLBACK_NAMES[pkg] ?: pkg), label != null)
        }
    }

    private fun launchableApps(context: Context): Map<String, String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = LinkedHashMap<String, String>()
        for (info in pm.queryIntentActivities(intent, 0)) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || pkg in found) continue
            found[pkg] = info.activityInfo.applicationInfo.loadLabel(pm).toString()
        }
        return found
    }

    private fun rebuild() {
        fun matches(app: App) = filter.isEmpty() ||
            app.label.contains(filter, ignoreCase = true) ||
            app.pkg.contains(filter, ignoreCase = true)

        val list = ArrayList<Row>()
        val first = top.filter(::matches)
        if (first.isNotEmpty()) {
            list += Row.Heading(activity.getString(R.string.sk_call_door_apps_sisters), false)
            first.mapTo(list) { Row.Entry(it) }
        }
        val rest = others.filter(::matches)
        if (rest.isNotEmpty()) {
            list += Row.Heading(activity.getString(R.string.sk_call_door_apps_others), first.isNotEmpty())
            rest.mapTo(list) { Row.Entry(it) }
        }
        rows = list
    }

    private fun iconOf(app: App): Drawable? = icons.getOrPut(app.pkg) {
        if (!app.installed) {
            null
        } else {
            runCatching { activity.packageManager.getApplicationIcon(app.pkg) }.getOrNull()
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): Any = rows[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = if (rows[position] is Row.Heading) 0 else 1

        override fun isEnabled(position: Int): Boolean = rows[position] is Row.Entry

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val row = rows[position]) {
                is Row.Heading -> heading(row, convertView)
                is Row.Entry -> entry(row.app, convertView)
            }

        /** A group's name; from the second group on, the clear line that sets it apart above. */
        private fun heading(row: Row.Heading, convertView: View?): View {
            val view = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    View(activity).apply { setBackgroundColor(divider) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
                        topMargin = dp(6f)
                        bottomMargin = dp(4f)
                    },
                )
                addView(
                    TextView(activity).apply {
                        textSize = 13f
                        setTextColor(SkRoles.color(activity, "dialog_heading"))
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, dp(4f), 0, dp(2f))
                    },
                )
            }
            view.getChildAt(0).visibility = if (row.divided) View.VISIBLE else View.GONE
            (view.getChildAt(1) as TextView).text = row.text
            return view
        }

        private fun entry(app: App, convertView: View?): View {
            val view = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(ROW_HEIGHT_DP)
                addView(
                    ImageView(activity),
                    LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(12f) },
                )
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            TextView(activity).apply {
                                textSize = 16f
                                setTextColor(ink)
                                setSingleLine()
                            },
                        )
                        addView(
                            TextView(activity).apply {
                                textSize = 12f
                                setTextColor(dim)
                                setSingleLine()
                            },
                        )
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    SwitchCompat(activity).apply {
                        SkStyler.styleSwitch(this)
                        minHeight = 0
                        minimumHeight = 0
                        isClickable = false
                        isFocusable = false
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = dp(8f) },
                )
            }
            val icon = view.getChildAt(0) as ImageView
            val labels = view.getChildAt(1) as LinearLayout
            val toggle = view.getChildAt(2) as SwitchCompat

            val drawable = iconOf(app)
            if (drawable != null) {
                icon.clearColorFilter()
                icon.setImageDrawable(drawable)
            } else {
                // An app that is not on the phone has no icon of its own: the plain app glyph,
                // in the colour of a row that only looks.
                icon.setImageResource(R.drawable.squares_four)
                icon.setColorFilter(SkTheme.color(activity, SkSlot.MENU_VIEW))
            }
            (labels.getChildAt(0) as TextView).text = app.label
            (labels.getChildAt(1) as TextView).text = if (app.installed) {
                app.pkg
            } else {
                activity.getString(R.string.sk_call_door_apps_absent, app.pkg)
            }
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = app.pkg in allowed
            toggle.jumpDrawablesToCurrentState()
            view.setOnClickListener {
                val on = app.pkg !in allowed
                if (on) allowed += app.pkg else allowed -= app.pkg
                SkCallDoor.setAllowed(activity, app.pkg, on)
                toggle.isChecked = on
            }
            return view
        }
    }

    companion object {
        private const val LIST_HEIGHT_SHARE = 0.5f
        private const val MAIN_FRAME_DP = 3f
        private const val ROW_HEIGHT_DP = 36f
        private const val ICON_DP = 30f

        /** What a sister app is called while it is not installed and cannot say so itself. */
        private val FALLBACK_NAMES = mapOf(
            SkSisterApps.RENRAKUSAKI to "白い熊 連絡先",
            SkSisterApps.DENWA to "白い熊 電話",
            SkSisterApps.MESSEJI to "白い熊 メッセージ",
        )

        /** The names of the apps let in, for the status line of the row that opens this. */
        fun describe(context: Context): String {
            val pm = context.packageManager
            return SkCallDoor.allowed(context)
                .sortedBy { SkCallDoor.KNOWN.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .map { pkg ->
                    runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrNull() ?: FALLBACK_NAMES[pkg] ?: pkg
                }
                .joinToString(", ")
        }
    }
}
