package org.linphone.shiroikuma

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.databinding.BindingAdapter
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the card-folder tabs the bottom navigation bar is drawn as.
 *
 * The main screens no longer title themselves ("Contacts" / "Calls" / "Conversations" moved out of
 * the top bar in favour of the account tabs), so *this* is what says where you are. The panel is
 * above the bar, so the tabs hang down from the separator line; the account strip at the top of the
 * screen is the same object standing the other way up. See [SkFolderTabPainter] for the shape.
 *
 * Selection changes are interpolated (the front line dives around the incoming tab while the
 * outgoing one closes up), which is what makes a tab switch read as continuous rather than as a
 * jump between two unrelated screens.
 *
 * Geometry is taken from the actual navigation labels at draw time (by id, from the shared parent),
 * so hidden tabs — Conversations and Meetings can both be switched off — never leave a gap.
 */
class SkFolderTabsView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        companion object {
            private const val ANIMATION_DURATION_MS = 260L

            /** Gap left between two neighbouring tabs. */
            private const val TAB_SIDE_INSET_DP = 4f

            /** Distance kept between the tab's far edge and the edge of the bar. */
            private const val BAR_INSET_DP = 3f
        }

        /** Left-to-right, matching the ids in @layout/bottom_nav_bar. */
        private val anchorIds = intArrayOf(
            R.id.contacts,
            R.id.favourites,
            R.id.calls,
            R.id.conversations,
            R.id.meetings,
        )

        /** Per tab: 1 = fully in the foreground, 0 = fully filed away. */
        private val weights = FloatArray(anchorIds.size)
        private val weightsFrom = FloatArray(anchorIds.size)
        private val weightsTo = FloatArray(anchorIds.size)

        private var currentTab = Int.MIN_VALUE
        private var animator: ValueAnimator? = null

        private val painter = SkFolderTabPainter(context, pointUp = false)
        private val spans = ArrayList<SkFolderTabPainter.Span>()

        private val density = resources.displayMetrics.density
        private val sideInset = TAB_SIDE_INSET_DP * density
        private val barInset = BAR_INSET_DP * density

        /** The navigation bar can relayout under us (a tab being hidden); follow it. */
        private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            invalidate()
        }

        init {
            painter.refreshColors(context)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            painter.refreshColors(context)
            (parent as? ViewGroup)?.addOnLayoutChangeListener(layoutListener)
        }

        override fun onDetachedFromWindow() {
            (parent as? ViewGroup)?.removeOnLayoutChangeListener(layoutListener)
            animator?.cancel()
            animator = null
            super.onDetachedFromWindow()
        }

        /**
         * [index] is a position in [anchorIds], or a negative value for "no tab in the foreground".
         * The very first call lands instantly — there is nothing to move away from yet.
         */
        fun setSelectedTab(index: Int) {
            if (index == currentTab) return
            val first = currentTab == Int.MIN_VALUE
            currentTab = index

            for (i in weightsTo.indices) {
                weightsTo[i] = if (i == index) 1f else 0f
            }

            animator?.cancel()
            if (first) {
                weightsTo.copyInto(weights)
                invalidate()
                return
            }

            weights.copyInto(weightsFrom)
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float
                    for (i in weights.indices) {
                        weights[i] = weightsFrom[i] + (weightsTo[i] - weightsFrom[i]) * fraction
                    }
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0) return

            val baseline = painter.baselineInset
            val depth = height - baseline - barInset

            spans.clear()
            for (i in anchorIds.indices) {
                val anchor = anchorOf(i) ?: continue
                spans.add(
                    SkFolderTabPainter.Span(
                        anchor.left - left + sideInset,
                        anchor.right - left - sideInset,
                        weights[i],
                    ),
                )
            }
            painter.draw(canvas, width.toFloat(), baseline, depth, spans)
        }

        /** The navigation label the tab at [index] wraps, or null when that entry is hidden. */
        private fun anchorOf(index: Int): View? {
            val container = parent as? ViewGroup ?: return null
            val anchor = container.findViewById<View>(anchorIds[index]) ?: return null
            return anchor.takeIf { it.visibility == VISIBLE && it.width > 0 }
        }
    }

@BindingAdapter("skSelectedTab")
fun SkFolderTabsView.bindSelectedTab(index: Int) {
    setSelectedTab(index)
}
