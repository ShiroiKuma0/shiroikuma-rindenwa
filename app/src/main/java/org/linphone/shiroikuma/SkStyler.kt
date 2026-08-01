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
            SkSlot.entries.any { it.hasFont && SkTheme.hasFontOverride(context, it) }
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
     * shiroikuma fork: one call-history record — every text line, the direction arrow and the
     * hairline below it.
     *
     * Both of its spacings are 白い熊-settable — the padding that separates one record from the
     * next ([SkDimen.CALL_ROW_PADDING]) and the gap between the lines inside a record
     * ([SkDimen.CALL_LINE_SPACING]) — and each line carries its own font slot, so the number or
     * the time can be raised without dragging the rest of the record up with it.
     *
     * Applied per bind rather than through [styleTree], because RecyclerView creates these rows
     * long after the Activity has been styled and recycles them freely.
     */
    fun styleCallHistoryRow(
        row: View,
        avatar: View?,
        name: TextView?,
        number: TextView?,
        time: TextView?,
        durationSeparator: TextView?,
        duration: TextView?,
        directionIcon: ImageView?,
        direction: SkCallLog.Direction?,
        rowDivider: View?,
    ) {
        val context = row.context

        val padding = SkTheme.dimenPx(context, SkDimen.CALL_ROW_PADDING)
        row.setPadding(row.paddingLeft, padding, row.paddingRight, padding)

        val avatarSize = SkTheme.dimenPx(context, SkDimen.CALL_AVATAR_SIZE)
        avatar?.layoutParams?.let { params ->
            if (params.width != avatarSize || params.height != avatarSize) {
                params.width = avatarSize
                params.height = avatarSize
                avatar.layoutParams = params
            }
        }

        // The first line sits flush against the top; every later line is pushed down by the gap.
        val spacing = SkTheme.dimenPx(context, SkDimen.CALL_LINE_SPACING)
        for ((index, line) in listOf(name, number, time).withIndex()) {
            val params = line?.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            val wanted = if (index == 0) 0 else spacing
            if (params.topMargin == wanted) continue
            params.topMargin = wanted
            line.layoutParams = params
        }

        paint(name, SkSlot.CALL_ROW_NAME)
        paint(number, SkSlot.LIST_NUMBER)
        paint(time, SkSlot.CALL_TIME)
        paint(duration, SkSlot.CALL_DURATION)
        paint(durationSeparator, SkSlot.CALL_DURATION)

        directionIcon?.let { icon ->
            val shown = direction != null && SkCallLog.directionShown(context)
            icon.visibility = if (shown) View.VISIBLE else View.GONE
            if (direction != null) {
                icon.imageTintList = ColorStateList.valueOf(
                    SkTheme.color(context, directionSlot(direction)),
                )
            }
        }

        applyRule(
            rowDivider,
            SkTheme.color(context, SkSlot.CALL_ROW_DIVIDER),
            SkTheme.dimenPx(context, SkDimen.CALL_ROW_DIVIDER_WIDTH),
        )
    }

    /**
     * shiroikuma fork: the day headline standing above the calls made on that day — the band that
     * closes off the day before it, the date itself, and the rule under the text. Both rules go
     * down to 0, which removes them.
     */
    fun styleDayHeader(holder: View, divider: View?, text: TextView?, underline: View?) {
        val context = holder.context

        // The decoration is drawn sticky, over the rows scrolling beneath it — it has to be opaque.
        holder.setBackgroundColor(SkTheme.color(context, SkSlot.BACKGROUND))

        applyRule(
            divider,
            SkTheme.color(context, SkSlot.CALL_DAY_DIVIDER),
            SkTheme.dimenPx(context, SkDimen.CALL_DAY_DIVIDER_WIDTH),
        )
        text?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.CALL_DAY))
            SkFonts.applyFont(it, SkSlot.CALL_DAY, Typeface.BOLD)
        }
        applyRule(
            underline,
            SkTheme.color(context, SkSlot.CALL_DAY_UNDERLINE),
            SkTheme.dimenPx(context, SkDimen.CALL_DAY_UNDERLINE_WIDTH),
        )
    }

    /**
     * shiroikuma fork: the text of an ordinary list row (contacts, conversations) on the shipped
     * type scale.
     *
     * Deliberately NOT [styleListRow]: that one also repaints the row's frame from the LIST_*
     * background/border slots, which would draw a bordered box around every contact. Here only the
     * type is touched, so the rows keep upstream's shape and gain the scale.
     */
    fun styleListText(name: TextView?, vararg details: TextView?) {
        paint(name, SkSlot.LIST_NAME)
        for (detail in details) {
            paint(detail, SkSlot.LIST_DETAIL)
        }
    }

    /**
     * shiroikuma fork: one contact row, in the sister address book's shape — the avatar spanning
     * the name and the number written under it, closed off by a full-width line. Avatar size, row
     * padding, both text slots and the line are all 白い熊-settable, and rows are recycled, so
     * everything is re-applied on every bind.
     */
    fun styleContactRow(row: View, avatar: View?, name: TextView?, number: TextView?, divider: View?) {
        val context = row.context

        val padding = SkTheme.dimenPx(context, SkDimen.CONTACT_ROW_PADDING)
        row.setPadding(row.paddingLeft, padding, row.paddingRight, padding)

        val size = SkTheme.dimenPx(context, SkDimen.CONTACT_AVATAR_SIZE)
        avatar?.layoutParams?.let { params ->
            if (params.width != size || params.height != size) {
                params.width = size
                params.height = size
                avatar.layoutParams = params
            }
        }

        paint(name, SkSlot.LIST_NAME)
        paint(number, SkSlot.CONTACT_NUMBER)

        applyRule(
            divider,
            SkTheme.color(context, SkSlot.CONTACT_ROW_DIVIDER),
            SkTheme.dimenPx(context, SkDimen.CONTACT_ROW_DIVIDER_WIDTH),
        )
    }

    /**
     * shiroikuma fork: one Favorites tile — a large round photo with the name centred under it.
     * Photo size, the gap between tiles and the name's own type are all 白い熊-settable.
     */
    fun styleFavouriteTile(tile: View, avatar: View?, name: TextView?) {
        val context = tile.context

        val padding = SkTheme.dimenPx(context, SkDimen.FAVOURITE_TILE_PADDING)
        tile.setPadding(padding, padding, padding, padding)

        val size = SkTheme.dimenPx(context, SkDimen.FAVOURITE_AVATAR_SIZE)
        avatar?.layoutParams?.let { params ->
            if (params.width != size || params.height != size) {
                params.width = size
                params.height = size
                avatar.layoutParams = params
            }
        }

        paint(name, SkSlot.FAVOURITE_NAME)
    }

    /**
     * shiroikuma fork: one letter heading of the Contacts list — the band framing open content,
     * the letter with its fold indicator, and the rule under the text. Mirrors the sister address
     * book (shiroikuma-renrakusaki): bold unless a weight is set for the slot, and both rules go
     * down to 0, which removes them.
     */
    fun styleContactSection(holder: View, divider: View?, title: TextView?, indicator: TextView?, underline: View?, content: View?, showDivider: Boolean) {
        val context = holder.context

        // Drawn as a real row rather than a decoration — it has to be tappable — so it sits on the
        // list background like any other row.
        holder.setBackgroundColor(SkTheme.color(context, SkSlot.BACKGROUND))

        applyRule(
            divider,
            SkTheme.color(context, SkSlot.CONTACT_SECTION_DIVIDER),
            if (showDivider) SkTheme.dimenPx(context, SkDimen.CONTACT_SECTION_DIVIDER_WIDTH) else 0,
        )

        // Bold by default; an explicit slot weight is a deliberate choice and wins.
        val baseStyle = if (SkTheme.fontWeight(context, SkSlot.CONTACT_SECTION) == 0) {
            Typeface.BOLD
        } else {
            Typeface.NORMAL
        }
        for (view in listOf(title, indicator)) {
            view ?: continue
            view.setTextColor(SkTheme.color(context, SkSlot.CONTACT_SECTION))
            SkFonts.applyFont(view, SkSlot.CONTACT_SECTION, baseStyle)
        }

        val padding = SkTheme.dimenPx(context, SkDimen.CONTACT_SECTION_PADDING)
        (content?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            if (params.topMargin != padding || params.bottomMargin != padding) {
                params.topMargin = padding
                params.bottomMargin = padding
                content.layoutParams = params
            }
        }

        applyRule(
            underline,
            SkTheme.color(context, SkSlot.CONTACT_SECTION_UNDERLINE),
            SkTheme.dimenPx(context, SkDimen.CONTACT_SECTION_UNDERLINE_WIDTH),
        )
    }

    private fun directionSlot(direction: SkCallLog.Direction): SkSlot = when (direction) {
        SkCallLog.Direction.INCOMING -> SkSlot.CALL_DIR_INCOMING
        SkCallLog.Direction.OUTGOING -> SkSlot.CALL_DIR_OUTGOING
        SkCallLog.Direction.MISSED -> SkSlot.CALL_DIR_MISSED
    }

    private fun paint(view: TextView?, slot: SkSlot) {
        view ?: return
        view.setTextColor(SkTheme.color(view.context, slot))
        SkFonts.applyFont(view, slot)
    }

    /** A settable rule: painted at [thicknessPx], or taken out of the layout entirely at 0. */
    private fun applyRule(view: View?, color: Int, thicknessPx: Int) {
        view ?: return
        if (thicknessPx <= 0) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.setBackgroundColor(color)
        val params = view.layoutParams
        if (params != null && params.height != thicknessPx) {
            params.height = thicknessPx
            view.layoutParams = params
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
