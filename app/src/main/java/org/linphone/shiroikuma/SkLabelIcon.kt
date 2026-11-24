package org.linphone.shiroikuma

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.widget.TextView

/**
 * shiroikuma-rindenwa fork — the icon standing before a label: of a row of the 臨電話 UI page, of
 * a section heading, of an index chip.
 *
 * **Sized by the label** (black-yellow-theme skill, “Row icons — sized by the label”): 1.25 × the
 * label’s text size, never under [floorDp]. The size is read off the label **at every layout**,
 * not when the row is built, so a font pass that runs later is followed. The gap is half the
 * icon; the gutter the icon takes (icon + gap) is reported through `onGutter`, for whatever sits
 * under the label’s text — an explanation, a heading’s underline. The icon is centred on the
 * label’s **first line**, not on a label that wrapped.
 */
object SkLabelIcon {
    private const val SCALE = 1.25f
    const val ROW_FLOOR_DP = 18f

    fun set(
        label: TextView,
        icon: Drawable?,
        color: Int,
        floorDp: Float = ROW_FLOOR_DP,
        onGutter: ((Int) -> Unit)? = null,
    ) {
        if (icon == null) return
        val drawable = FirstLine(icon.mutate(), color, label)
        var shown = -1

        fun fit(): Boolean {
            val floor = (floorDp * label.resources.displayMetrics.density).toInt()
            val size = maxOf(floor, (label.textSize * SCALE).toInt())
            if (size == shown) return false
            shown = size
            val gap = size / 2
            drawable.setBounds(0, 0, size, size)
            label.compoundDrawablePadding = gap
            label.setCompoundDrawablesRelative(drawable, null, null, null)
            onGutter?.invoke(size + gap)
            return true
        }
        fit()
        // A changed size asks for a new layout, which cannot be done from inside this one.
        label.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> label.post { fit() } }
    }

    /** Painted one colour whatever tint the host pushes, and drawn level with the first line. */
    private class FirstLine(inner: Drawable, color: Int, private val label: TextView) : DrawableWrapper(inner) {
        init {
            inner.setTintList(ColorStateList.valueOf(color))
        }

        override fun setTintList(tint: ColorStateList?) = Unit

        override fun setTint(tintColor: Int) = Unit

        override fun getIntrinsicWidth(): Int = bounds.width()

        override fun getIntrinsicHeight(): Int = bounds.height()

        override fun draw(canvas: Canvas) {
            val layout = label.layout
            if (layout == null || layout.lineCount < 2) {
                super.draw(canvas)
                return
            }
            // The host centres a start drawable on its whole text area; move it up to line one.
            val area = label.height - label.compoundPaddingTop - label.compoundPaddingBottom
            val firstLine = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
            canvas.save()
            canvas.translate(0f, firstLine - area / 2f)
            super.draw(canvas)
            canvas.restore()
        }
    }
}
