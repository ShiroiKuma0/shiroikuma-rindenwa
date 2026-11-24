package org.linphone.shiroikuma

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.hypot

/**
 * shiroikuma-rindenwa fork — where the 臨電話 UI page keeps its arrangement: the order of its
 * sections and which of them are unfolded.
 *
 * Both live in the app's default preferences under `sk_` keys, so they travel in the export with
 * the colours and fonts (the "appearance" category) and a restore brings the arrangement back.
 * Sections are named by **stable string keys** — never by resource ids, which shift between
 * builds. Folded by default: a section is open only while its key is in the open set.
 */
object SkPageState {
    private const val KEY_ORDER = "sk_ui_section_order"
    private const val KEY_OPEN = "sk_ui_sections_open"

    /**
     * The stored order applied to [built]: stored keys first, as far as they still exist, then
     * any section added since at its built place among the rest; unknown stored keys are dropped.
     */
    fun order(context: Context, built: List<String>): List<String> {
        val stored = SkTheme.prefs(context).getString(KEY_ORDER, null).orEmpty()
            .split(',').filter { it in built }.distinct()
        return stored + built.filter { it !in stored }
    }

    /**
     * A section that belongs at the top and arrived in an update: put first in a stored order
     * too, **once** — nothing dragged it anywhere yet. Afterwards it stays where it is dragged.
     */
    fun putFirstOnce(context: Context, key: String) {
        val prefs = SkTheme.prefs(context)
        val seen = "sk_ui_section_first_seen_$key"
        if (prefs.getBoolean(seen, false)) return
        val stored = prefs.getString(KEY_ORDER, null)
        val editor = prefs.edit().putBoolean(seen, true)
        if (!stored.isNullOrEmpty()) {
            editor.putString(KEY_ORDER, (listOf(key) + stored.split(',').filter { it != key }).joinToString(","))
        }
        editor.apply()
    }

    fun setOrder(context: Context, order: List<String>) {
        SkTheme.prefs(context).edit().putString(KEY_ORDER, order.joinToString(",")).apply()
    }

    fun resetOrder(context: Context) {
        SkTheme.prefs(context).edit().remove(KEY_ORDER).apply()
    }

    fun isOpen(context: Context, key: String): Boolean =
        SkTheme.prefs(context).getStringSet(KEY_OPEN, null)?.contains(key) == true

    fun setOpen(context: Context, key: String, open: Boolean) {
        val prefs = SkTheme.prefs(context)
        val set = prefs.getStringSet(KEY_OPEN, null).orEmpty().toMutableSet()
        if (open) set += key else set -= key
        prefs.edit().putStringSet(KEY_OPEN, set).apply()
    }
}

/**
 * shiroikuma-rindenwa fork — the jump index of the 臨電話 UI page: a wrapping grid of small
 * outlined chips, one for every top-level section (black-yellow-theme skill; after 白い熊 地図's
 * `ChizuSectionIndex`).
 *
 * A **tap** on a chip unfolds its section and scrolls to it. A **long press** lifts the chip into
 * a drag: the chips reorder **live** under the finger — the drag is driven by its shadow, so
 * moving the chip in the tree does not cancel it — and on release the sections below are put in
 * the same order.
 */
object SkSectionIndex {
    class Item(val key: String, val label: String, val icon: Int, val iconColor: Int)

    private const val GAP_DP = 6f
    private const val FRAME_DP = 1.25f
    private const val LIFTED_ALPHA = 0.3f

    fun build(
        context: Context,
        items: List<Item>,
        frame: Int,
        text: Int,
        onJump: (String) -> Unit,
        onReordered: (List<String>) -> Unit,
    ): View {
        val density = context.resources.displayMetrics.density
        val flow = Flow(context, (GAP_DP * density).toInt())
        flow.setPadding(0, (2 * density).toInt(), 0, (6 * density).toInt())
        for (item in items) {
            val chip = chip(context, item, frame, text, density)
            chip.tag = item.key
            chip.setOnClickListener { onJump(it.tag as String) }
            chip.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                it.alpha = LIFTED_ALPHA
                it.startDragAndDrop(
                    ClipData.newPlainText("sk_section", it.tag as String),
                    View.DragShadowBuilder(it),
                    it,
                    0,
                )
                true
            }
            flow.addView(chip)
        }
        val before = items.map { it.key }
        flow.setOnDragListener { _, event ->
            val dragged = event.localState as? View
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION -> if (dragged != null && dragged.parent === flow) {
                    val to = slotAt(flow, event.x, event.y)
                    val from = flow.indexOfChild(dragged)
                    if (to >= 0 && to != from) {
                        flow.removeView(dragged)
                        flow.addView(dragged, to.coerceAtMost(flow.childCount))
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> if (dragged != null && dragged.parent === flow) {
                    dragged.alpha = 1f
                    val order = (0 until flow.childCount).map { flow.getChildAt(it).tag as String }
                    // Rebuilding the page from inside the drag's own dispatch would pull the
                    // views out from under it.
                    if (order != before) flow.post { onReordered(order) }
                }
            }
            true
        }
        return flow
    }

    /** The chip the finger is over, or the one whose centre is nearest. */
    private fun slotAt(flow: ViewGroup, x: Float, y: Float): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until flow.childCount) {
            val child = flow.getChildAt(i)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) return i
            val distance = hypot(x - (child.left + child.right) / 2f, y - (child.top + child.bottom) / 2f)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /**
     * As wide as its icon, its label and its padding — never a uniform width. The icon is the
     * section's own, in the section's colour, sized by the chip's label.
     */
    private fun chip(context: Context, item: Item, frame: Int, ink: Int, density: Float) =
        TextView(context).apply {
            text = item.label
            SkLabelIcon.set(
                this,
                androidx.appcompat.content.res.AppCompatResources.getDrawable(context, item.icon),
                item.iconColor,
                floorDp = 0f,
            )
            setTextColor(ink)
            textSize = 12f
            setSingleLine()
            gravity = Gravity.CENTER
            setPadding(
                (10 * density).toInt(),
                (5 * density).toInt(),
                (12 * density).toInt(),
                (5 * density).toInt(),
            )
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 100 * density
                setStroke((FRAME_DP * density + 0.5f).toInt().coerceAtLeast(1), frame)
            }
            isClickable = true
            isFocusable = true
        }

    /** A minimal wrapping row layout. */
    @SuppressLint("ViewConstructor")
    private class Flow(context: Context, private val gap: Int) : ViewGroup(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val width = MeasureSpec.getSize(widthSpec)
            val inner = width - paddingLeft - paddingRight
            var x = 0
            var y = paddingTop
            var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                measureChild(child, MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), heightSpec)
                if (x > 0 && x + child.measuredWidth > inner) {
                    x = 0
                    y += rowHeight + gap
                    rowHeight = 0
                }
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
            setMeasuredDimension(width, y + rowHeight + paddingBottom)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val inner = width - paddingLeft - paddingRight
            var x = paddingLeft
            var y = paddingTop
            var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (x > paddingLeft && x + child.measuredWidth > paddingLeft + inner) {
                    x = paddingLeft
                    y += rowHeight + gap
                    rowHeight = 0
                }
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
        }
    }
}
