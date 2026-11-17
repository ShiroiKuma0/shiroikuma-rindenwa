package org.linphone.shiroikuma

import android.graphics.Typeface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.widget.SwitchCompat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.databinding.BindingAdapter
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the menu rule of the black-yellow theme, for every menu in the app.
 *
 * Upstream's menus are a column of plain lines: nothing says which of them can be pressed. Here a
 * row that does something is an **outlined pill across the whole menu** — 1.25 dp frame, the
 * background showing through, never filled — with its icon inside at the left and a › at the right
 * when it opens something. A destructive row lends its colour to the frame and the label too.
 *
 * And the icon is **coloured by what the row does** (白い熊, 2026-11-17), so a menu can be read by
 * colour before it is read by word: green places a call, blue writes, cyan is a person, orange
 * copies or sends something out, magenta silences or leaves, red deletes, and plain yellow only
 * looks or opens. The frame and the label stay yellow — one coloured thing per row, at the left.
 * Each colour is a settable role on the 臨電話 UI page ([SkSlot.MENU_CALL] and its neighbours).
 *
 * Upstream draws all its menus from data-bound layouts, so one binding adapter restyles a row
 * where it is declared — `skMenuRole="@{`call`}"` — with no call site touched.
 */
object SkMenus {
    const val CALL = "call"
    const val MESSAGE = "message"
    const val CONTACT = "contact"
    const val SHARE = "share"
    const val VIEW = "view"
    const val BLOCK = "block"
    const val DELETE = "delete"

    /** A drawable painted [color] that ignores whatever tint its host view pushes onto it. */
    class FixedTint(val inner: Drawable, color: Int, size: Int) : InsetDrawable(inner, 0) {
        init {
            inner.setTintList(ColorStateList.valueOf(color))
            setBounds(0, 0, size, size)
        }

        override fun setTintList(tint: ColorStateList?) = Unit

        override fun setTint(tintColor: Int) = Unit

        override fun getIntrinsicWidth(): Int = bounds.width()

        override fun getIntrinsicHeight(): Int = bounds.height()
    }

    fun slotOf(role: String?): SkSlot = when (role) {
        CALL -> SkSlot.MENU_CALL
        MESSAGE -> SkSlot.MENU_MESSAGE
        CONTACT -> SkSlot.MENU_CONTACT
        SHARE -> SkSlot.MENU_SHARE
        BLOCK -> SkSlot.MENU_BLOCK
        DELETE -> SkSlot.MENU_DELETE
        else -> SkSlot.MENU_VIEW
    }

    private fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics
    ).toInt()

    /** The unfilled pill: [frame] round the row, and the same colour at 30 % for the ripple. */
    fun pill(context: Context, frame: Int, widthDp: Float = 1.25f): Drawable {
        val outline = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 100f).toFloat()
            setColor(0x00000000)
            setStroke(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    widthDp,
                    context.resources.displayMetrics
                ).toInt().coerceAtLeast(2),
                frame
            )
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 100f).toFloat()
            setColor(0xFFFFFFFF.toInt())
        }
        val ripple = (frame and 0x00FFFFFF) or 0x4D000000
        return RippleDrawable(ColorStateList.valueOf(ripple), outline, mask)
    }

    /**
     * Show [popup] under [anchor], shifted up by whatever does not fit below it.
     *
     * A stock popup is given only the larger of the room above and the room below its anchor, so
     * a long menu opened from low on the screen is squeezed and scrolls with half the screen
     * free. Shifted up, it can use the whole height before anything has to scroll.
     */
    fun showBelow(popup: PopupWindow, anchor: View) {
        val content = popup.contentView
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorBottom = location[1] + anchor.height
        val missing = (content.measuredHeight - (frame.bottom - anchorBottom)).coerceAtLeast(0)
        val room = (anchorBottom - frame.top).coerceAtLeast(0)
        popup.showAsDropDown(anchor, 0, -minOf(missing, room), Gravity.BOTTOM)
    }

    /** One row of a menu built in code ([popup]). */
    class Item(
        val label: String,
        val iconRes: Int,
        val role: String,
        val opens: Boolean = false,
        val action: () -> Unit
    )

    /**
     * A menu of action pills at [anchor], for the few menus that are not one of upstream's
     * data-bound layouts. Framed and styled like those.
     */
    fun popup(anchor: View, items: List<Item>): PopupWindow {
        val context = anchor.context
        val column = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.sk_menu_frame)
            setPadding(0, dp(context, 8f), 0, dp(context, 8f))
        }
        val popup = PopupWindow(
            column,
            dp(context, 240f),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = dp(context, 8f).toFloat()
        for (item in items) {
            val row = TextView(context, null, 0, R.style.context_menu_action_label_style)
            row.text = item.label
            row.maxLines = 1
            row.setCompoundDrawablesRelative(
                AppCompatResources.getDrawable(context, item.iconRes),
                null,
                null,
                null
            )
            column.addView(
                row,
                android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            style(row, item.role, item.opens)
            row.setOnClickListener {
                popup.dismiss()
                item.action()
            }
        }
        showBelow(popup, anchor)
        return popup
    }

    fun style(view: TextView, role: String?, opens: Boolean) {
        val context = view.context
        val accent = SkTheme.color(context, SkSlot.ACCENT)
        val roleColor = SkTheme.color(context, slotOf(role))
        val ink = if (role == DELETE) roleColor else accent

        view.background = pill(context, ink)
        view.setTextColor(ink)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        view.setPaddingRelative(dp(context, 14f), dp(context, 9f), dp(context, 12f), dp(context, 9f))
        view.compoundDrawablePadding = dp(context, 12f)
        view.isClickable = true

        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.topMargin = dp(context, 3f)
            params.bottomMargin = dp(context, 3f)
            params.marginStart = dp(context, 8f)
            params.marginEnd = dp(context, 8f)
            view.layoutParams = params
        }

        // A TextView tints all its compound drawables alike, and re-applies that tint whenever
        // they are set — which wiped the icon's own colour and left every icon grey (seen on the
        // phone, 2026-11-17). So the icon and the › each go in a wrapper that keeps its colour.
        val icon = (view.compoundDrawablesRelative[0] as? FixedTint)?.inner
            ?: view.compoundDrawablesRelative[0]
        val start = icon?.let { FixedTint(it.mutate(), roleColor, dp(context, 22f)) }
        val caret = if (opens) {
            AppCompatResources.getDrawable(context, R.drawable.caret_right)?.mutate()
                ?.let { FixedTint(it, ink, dp(context, 18f)) }
        } else {
            null
        }
        view.setCompoundDrawablesRelative(start, null, caret, null)
    }
}

/** The label of a toggle row: no pill, the icon in its role colour, lined up with the pills. */
@BindingAdapter("skMenuToggleRole")
fun bindSkMenuToggleRole(view: TextView, role: String?) {
    val context = view.context
    view.setTextColor(SkTheme.color(context, SkSlot.ACCENT))
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    val current = view.compoundDrawablesRelative[0]
    val icon = ((current as? SkMenus.FixedTint)?.inner ?: current)?.let {
        SkMenus.FixedTint(
            it.mutate(),
            SkTheme.color(context, SkMenus.slotOf(role)),
            (22 * context.resources.displayMetrics.density).toInt()
        )
    }
    view.compoundDrawablePadding = (12 * context.resources.displayMetrics.density).toInt()
    view.setCompoundDrawablesRelative(icon, null, null, null)
}

/** The theme's outlined switch, for a switch declared in a layout. */
@BindingAdapter("skSwitch")
fun bindSkSwitch(view: SwitchCompat, on: Boolean?) {
    if (on == true) SkStyler.styleSwitch(view)
}

@BindingAdapter(value = ["skMenuRole", "skMenuOpens"], requireAll = false)
fun bindSkMenuRole(view: TextView, role: String?, opens: Boolean?) {
    SkMenus.style(view, role, opens == true)
}

/**
 * A row of a settings page that does something when tapped — opens a page, the browser, a picker,
 * or runs an action — drawn as what it is: an outlined pill as wide as its label, never a line
 * that reads as a heading or a note. [kind] is `opens` (a › at the right), `action` (acts in
 * place), `main` (the page's one link to this app's own settings: 3 dp frame, bold, ›) or
 * `danger` (a destructive action, in the warning colour).
 *
 * Upstream gives these rows the full width of the page; a short action stretched that far reads
 * as a banner, so the row is narrowed to its label and kept at the left.
 */
@BindingAdapter("skPill")
fun bindSkPill(view: TextView, kind: String?) {
    val context = view.context
    val density = context.resources.displayMetrics.density
    val accent = SkTheme.color(context, SkSlot.ACCENT)
    val ink = if (kind == "danger") SkTheme.color(context, SkSlot.MENU_DELETE) else accent
    val main = kind == "main"
    val opens = main || kind == "opens"

    view.background = SkMenus.pill(context, ink, if (main) 3f else 1.25f)
    view.setTextColor(ink)
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    view.setTypeface(Typeface.create(view.typeface, if (main) Typeface.BOLD else Typeface.NORMAL))
    view.maxLines = 1
    view.setPaddingRelative(
        (16 * density).toInt(),
        (9 * density).toInt(),
        (14 * density).toInt(),
        (9 * density).toInt()
    )
    view.compoundDrawablePadding = (8 * density).toInt()
    view.isClickable = true

    val caret = if (opens) {
        AppCompatResources.getDrawable(context, R.drawable.caret_right)?.mutate()
            ?.let { SkMenus.FixedTint(it, ink, (18 * density).toInt()) }
    } else {
        null
    }
    view.setCompoundDrawablesRelative(null, null, caret, null)

    when (val params = view.layoutParams) {
        is ConstraintLayout.LayoutParams -> {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.constrainedWidth = true
            params.horizontalBias = 0f
            view.layoutParams = params
        }

        is ViewGroup.LayoutParams -> {
            if (params.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = params
            }
        }
    }
}
