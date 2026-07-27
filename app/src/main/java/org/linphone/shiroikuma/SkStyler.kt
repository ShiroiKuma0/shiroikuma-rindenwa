package org.linphone.shiroikuma

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * shiroikuma-rindenwa fork — applies the 白い熊 臨電話 UI theme to the live view tree.
 *
 * Called from [org.linphone.ui.GenericActivity] (onPostCreate + onResume), so every screen picks
 * up the current colors/fonts. The chrome pass is deliberately generic — window background,
 * toolbars, FABs, buttons — because upstream's screens are data-bound and change shape between
 * releases; per-screen bindings are added on top through [styleListRow] and [styleBubble] from
 * the adapters that own those views.
 */
object SkStyler {

    /**
     * Whole-activity chrome pass. Safe to call repeatedly.
     *
     * **The defaults are not applied here.** `Theme.SkBlackYellow` (values/sk_theme.xml) redefines
     * every attribute upstream resolves its colours through, so the black-yellow look lands
     * natively at inflation time, in every fragment and dialog, with no walking and no flicker.
     * This pass exists only for the slots 白い熊 has actually overridden in the UI page — walking
     * the tree to repaint what the theme already got right would fight it and lose on any view
     * created after we ran.
     */
    fun apply(activity: Activity) {
        if (!anyOverride()) return
        if (SkTheme.hasOverride(activity, SkSlot.BACKGROUND)) {
            val background = SkTheme.color(activity, SkSlot.BACKGROUND)
            activity.window?.decorView?.setBackgroundColor(background)
            activity.findViewById<View>(android.R.id.content)?.setBackgroundColor(background)
        }
        activity.findViewById<View>(android.R.id.content)?.let { styleTree(it) }
    }

    /** True when at least one slot or font carries a user override worth a runtime pass. */
    private fun anyOverride(): Boolean = overridesPresent

    /**
     * Set by the UI page whenever a slot changes, so an untouched install never pays for the walk.
     * Recomputed lazily on first use per process.
     */
    @Volatile
    private var overridesPresent: Boolean = true

    fun refreshOverrideState(context: android.content.Context) {
        overridesPresent = SkSlot.entries.any { SkTheme.hasOverride(context, it) } ||
            SkSlot.entries.any {
                it.hasFont && (
                    SkTheme.fontFamily(context, it).isNotEmpty() ||
                        SkTheme.fontWeight(context, it) > 0 ||
                        SkTheme.fontSize(context, it) > 0
                    )
            }
    }

    /** Walk a subtree applying the role rules for overridden slots only. */
    fun styleTree(root: View) {
        val context = root.context
        walk(root) { view ->
            when (view) {
                is Toolbar -> styleToolbar(view)
                is FloatingActionButton -> {
                    if (SkTheme.hasOverride(context, SkSlot.FAB_BACKGROUND)) {
                        view.backgroundTintList =
                            ColorStateList.valueOf(SkTheme.color(context, SkSlot.FAB_BACKGROUND))
                    }
                    if (SkTheme.hasOverride(context, SkSlot.FAB_ICON)) {
                        view.imageTintList =
                            ColorStateList.valueOf(SkTheme.color(context, SkSlot.FAB_ICON))
                    }
                }
                is Button -> {
                    if (SkTheme.hasOverride(context, SkSlot.BUTTON_TEXT)) {
                        view.setTextColor(SkTheme.color(context, SkSlot.BUTTON_TEXT))
                    }
                    SkFonts.applyFont(view, SkSlot.BUTTON_TEXT, Typeface.BOLD)
                }
                else -> Unit
            }
        }
    }

    /** Toolbar chrome: background, title (color + font), nav / overflow / action icons. */
    fun styleToolbar(toolbar: Toolbar) {
        val context = toolbar.context
        val iconColor = SkTheme.color(context, SkSlot.TOOLBAR_ICON)
        if (SkTheme.hasOverride(context, SkSlot.TOOLBAR_BACKGROUND)) {
            toolbar.setBackgroundColor(SkTheme.color(context, SkSlot.TOOLBAR_BACKGROUND))
        }
        toolbar.setTitleTextColor(SkTheme.color(context, SkSlot.TOOLBAR_TITLE))
        toolbar.navigationIcon?.setTint(iconColor)
        toolbar.overflowIcon?.setTint(iconColor)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.setTint(iconColor)
        }
        titleTextView(toolbar)?.let { SkFonts.applyFont(it, SkSlot.TOOLBAR_TITLE) }
        if (toolbar is MaterialToolbar) {
            // Kill the scroll-elevation surface tint so the bar stays our exact color.
            toolbar.elevation = 0f
        }
    }

    private fun titleTextView(toolbar: Toolbar): TextView? {
        val title = toolbar.title ?: return null
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView && child.text == title) {
                return child
            }
        }
        return null
    }

    /**
     * One list row (history / contacts / conversations): frame background, border and radius from
     * the LIST_* slots, primary and secondary label text.
     */
    fun styleListRow(row: View, name: TextView?, detail: TextView?) {
        val context = row.context
        row.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SkTheme.color(context, SkSlot.LIST_BACKGROUND))
            cornerRadius = SkTheme.dimenPx(context, SkDimen.LIST_CORNER_RADIUS).toFloat()
            setStroke(
                SkTheme.dimenPx(context, SkDimen.LIST_BORDER_WIDTH),
                SkTheme.color(context, SkSlot.LIST_BORDER),
            )
        }
        name?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.LIST_NAME))
            SkFonts.applyFont(it, SkSlot.LIST_NAME)
        }
        detail?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.LIST_DETAIL))
            SkFonts.applyFont(it, SkSlot.LIST_DETAIL)
        }
    }

    /**
     * shiroikuma fork: one call-history record. Both of its spacings are 白い熊-settable — the
     * padding that separates one record from the next ([SkDimen.CALL_ROW_PADDING]) and the gap
     * between the three lines inside a record ([SkDimen.CALL_LINE_SPACING]) — and the number line
     * carries its own [SkSlot.LIST_NUMBER] font slot, so its size can be raised without dragging
     * the name and the timestamp up with it.
     *
     * Applied per bind rather than through [styleTree], because RecyclerView creates these rows
     * long after the Activity has been styled and recycles them freely.
     */
    fun styleCallHistoryRow(row: View, number: TextView?, lines: List<View?>) {
        val context = row.context

        val padding = SkTheme.dimenPx(context, SkDimen.CALL_ROW_PADDING)
        row.setPadding(row.paddingLeft, padding, row.paddingRight, padding)

        // The first line sits flush against the top; every later line is pushed down by the gap.
        val spacing = SkTheme.dimenPx(context, SkDimen.CALL_LINE_SPACING)
        for ((index, line) in lines.withIndex()) {
            val params = line?.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            val wanted = if (index == 0) 0 else spacing
            if (params.topMargin == wanted) continue
            params.topMargin = wanted
            line.layoutParams = params
        }

        number?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.LIST_NUMBER))
            SkFonts.applyFont(it, SkSlot.LIST_NUMBER)
        }
    }

    /** One chat bubble; [outgoing] picks the outgoing or incoming slot pair. */
    fun styleBubble(bubble: View, text: TextView?, outgoing: Boolean) {
        val context = bubble.context
        val fill = if (outgoing) SkSlot.BUBBLE_OUTGOING else SkSlot.BUBBLE_INCOMING
        val ink = if (outgoing) SkSlot.BUBBLE_OUTGOING_TEXT else SkSlot.BUBBLE_INCOMING_TEXT
        bubble.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SkTheme.color(context, fill))
            cornerRadius = SkTheme.dimenPx(context, SkDimen.BUBBLE_CORNER_RADIUS).toFloat()
            setStroke(
                SkTheme.dimenPx(context, SkDimen.BUBBLE_BORDER_WIDTH),
                SkTheme.color(context, SkSlot.ACCENT),
            )
        }
        text?.let {
            it.setTextColor(SkTheme.color(context, ink))
            SkFonts.applyFont(it, ink)
        }
    }

    /** An avatar tile: fill, ink and corner radius (0 = square, max = round). */
    fun styleAvatar(container: View, initials: TextView?, icon: ImageView?) {
        val context = container.context
        container.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SkTheme.color(context, SkSlot.AVATAR_BACKGROUND))
            cornerRadius = SkTheme.dimenPx(context, SkDimen.AVATAR_CORNER_RADIUS).toFloat()
            setStroke(
                SkTheme.dimenPx(context, SkDimen.LIST_BORDER_WIDTH),
                SkTheme.color(context, SkSlot.ACCENT),
            )
        }
        initials?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.AVATAR_TEXT))
            SkFonts.applyFont(it, SkSlot.AVATAR_TEXT)
        }
        icon?.imageTintList =
            ColorStateList.valueOf(SkTheme.color(context, SkSlot.AVATAR_TEXT))
    }

    /** The pill shape used by our own dialogs and by any button we own. */
    fun pillBackground(context: android.content.Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(SkTheme.color(context, SkSlot.BUTTON_BACKGROUND))
        cornerRadius = SkTheme.dimenPx(context, SkDimen.BUTTON_CORNER_RADIUS).toFloat()
        setStroke(
            SkTheme.dimenPx(context, SkDimen.BUTTON_BORDER_WIDTH),
            SkTheme.color(context, SkSlot.BUTTON_BORDER),
        )
    }

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i), action)
            }
        }
    }
}
