package org.linphone.shiroikuma

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.HorizontalScrollView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.LifecycleOwner
import org.linphone.databinding.SkAccountTabBinding
import org.linphone.ui.main.model.AccountModel

/**
 * shiroikuma-rindenwa fork — the account tabs across the top of the main screens.
 *
 * The same card folder as the bottom navigation bar, stood the other way up: the panel is below, so
 * these tabs rise out of the separator line and are open at the bottom. Every tab is the same size;
 * the one in the foreground is the account in use and shows its picture and display name, the ones
 * filed behind it are the other accounts, same picture, no name. Tapping one makes it the default account — the same thing the
 * drawer menu does — and the whole strip scrolls sideways when there are more accounts than fit.
 *
 * Long-pressing a tab buzzes and picks it up: drag left or right to reorder the accounts. The order
 * is 白い熊's, shared with the drawer menu's list through [SkAccountOrder] and persisted, so both
 * lists always read the same way round.
 */
class SkAccountTabsView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : HorizontalScrollView(context, attrs, defStyleAttr) {
        companion object {
            private const val ANIMATION_DURATION_MS = 260L

            /** Room left inside a tab on each side of its content. */
            private const val TAB_PADDING_DP = 10f

            /** Gap left between two neighbouring tabs. */
            private const val TAB_GAP_DP = 6f

            /** Room before the first tab and after the last one. */
            private const val STRIP_PADDING_DP = 8f
        }

        private val strip = Strip(context)
        private val bindings = ArrayList<SkAccountTabBinding>()
        private var models: List<AccountModel> = emptyList()
        private var activeIdentity: String? = null

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // So the panel edge runs the full width of the screen even with a single account.
            strip.minimumWidth = w
        }

        fun setAccounts(accounts: List<AccountModel>) {
            if (accounts.map { it.identity } == models.map { it.identity }) {
                // Same accounts, new model instances after a refresh: rebind rather than rebuild.
                models = accounts
                for ((index, binding) in bindings.withIndex()) {
                    binding.model = accounts.getOrNull(index)
                }
                applyActive(animate = false)
                return
            }

            models = accounts
            bindings.clear()
            strip.removeAllViews()

            val inflater = LayoutInflater.from(context)
            for (model in accounts) {
                val binding = SkAccountTabBinding.inflate(inflater, strip, false)
                binding.model = model
                binding.active = model.identity == activeIdentity
                (context as? LifecycleOwner)?.let { binding.lifecycleOwner = it }
                binding.root.setOnClickListener { model.setAsDefault() }
                strip.addView(binding.root)
                bindings.add(binding)
            }
            applyActive(animate = false)
        }

        fun setActiveIdentity(identity: String?) {
            if (identity == activeIdentity) return
            activeIdentity = identity
            applyActive(animate = true)
        }

        private fun applyActive(animate: Boolean) {
            val index = models.indexOfFirst { it.identity == activeIdentity }
            for ((position, binding) in bindings.withIndex()) {
                binding.active = position == index
            }
            strip.setSelectedTab(index, animate)
            post { scrollTabIntoView(index) }
        }

        private fun scrollTabIntoView(index: Int) {
            val child = strip.getChildAt(index) ?: return
            val padding = STRIP_PADDING_DP * resources.displayMetrics.density
            val start = (child.left - padding).toInt()
            val end = (child.right + padding).toInt()
            when {
                start < scrollX -> smoothScrollTo(start, 0)
                end > scrollX + width -> smoothScrollTo(end - width, 0)
            }
        }

        /** Called by the drag helper once a tab has been dropped somewhere else. */
        private fun commitOrder(from: Int, to: Int) {
            if (from !in models.indices || to !in models.indices) return

            val reordered = models.toMutableList()
            reordered.add(to, reordered.removeAt(from))
            models = reordered

            val view = strip.getChildAt(from)
            strip.removeViewAt(from)
            strip.addView(view, to)
            bindings.add(to, bindings.removeAt(from))

            SkAccountOrder.save(context, models.map { it.identity })
            applyActive(animate = false)
        }

        /**
         * Lays the tabs out and draws them. A [ViewGroup] rather than a plain view because each tab
         * hosts a real, data-bound account view — picture, name, presence — inside its outline.
         */
        private inner class Strip(context: Context) : ViewGroup(context) {
            private val density = resources.displayMetrics.density
            private val tabPadding = TAB_PADDING_DP * density
            private val tabGap = TAB_GAP_DP * density

            private val painter = SkFolderTabPainter(context, pointUp = true)
            private val spans = ArrayList<SkFolderTabPainter.Span>()
            private val tabStarts = ArrayList<Float>()
            private val tabEnds = ArrayList<Float>()

            private var weights = FloatArray(0)
            private var weightsFrom = FloatArray(0)
            private var weightsTo = FloatArray(0)
            private var selected = -1
            private var animator: ValueAnimator? = null

            private val reorder = SkDragReorder(this, vertical = false) { from, to ->
                commitOrder(from, to)
            }

            init {
                setWillNotDraw(false)
                clipToPadding = false
                val padding = (STRIP_PADDING_DP * density).toInt()
                setPadding(padding, 0, padding, 0)
                painter.refreshColors(context)
                reorder.onUpdate = { invalidate() }
                reorder.attach()
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                painter.refreshColors(context)
            }

            override fun onDetachedFromWindow() {
                animator?.cancel()
                animator = null
                super.onDetachedFromWindow()
            }

            fun setSelectedTab(index: Int, animate: Boolean) {
                if (weights.size != childCount) {
                    weights = FloatArray(childCount)
                    weightsFrom = FloatArray(childCount)
                    weightsTo = FloatArray(childCount)
                }
                selected = index
                for (i in weightsTo.indices) {
                    weightsTo[i] = if (i == index) 1f else 0f
                }

                animator?.cancel()
                requestLayout()
                if (!animate) {
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

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val height = MeasureSpec.getSize(heightMeasureSpec)
                val childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
                val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

                var width = paddingLeft.toFloat()
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    child.measure(childWidthSpec, childHeightSpec)
                    width += child.measuredWidth + 2 * tabPadding
                    if (i < childCount - 1) width += tabGap
                }
                width += paddingRight

                setMeasuredDimension(maxOf(width.toInt(), suggestedMinimumWidth), height)
            }

            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                tabStarts.clear()
                tabEnds.clear()

                val baseline = height - painter.baselineInset
                val fullDepth = height - 2 * painter.baselineInset
                var x = paddingLeft.toFloat()

                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val tabWidth = child.measuredWidth + 2 * tabPadding
                    val top = baseline - fullDepth + (fullDepth - child.measuredHeight) / 2f
                    val left = x + tabPadding
                    child.layout(
                        left.toInt(),
                        top.toInt(),
                        (left + child.measuredWidth).toInt(),
                        (top + child.measuredHeight).toInt(),
                    )

                    tabStarts.add(x)
                    tabEnds.add(x + tabWidth)
                    x += tabWidth + tabGap
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (childCount == 0 || tabStarts.size != childCount) return

                val baseline = height - painter.baselineInset
                val depth = height - 2 * painter.baselineInset

                spans.clear()
                for (i in 0 until childCount) {
                    // Follows the child while it is being dragged, so a tab carries its account.
                    val shift = getChildAt(i).translationX
                    spans.add(
                        SkFolderTabPainter.Span(
                            tabStarts[i] + shift,
                            tabEnds[i] + shift,
                            weights.getOrElse(i) { 0f },
                        ),
                    )
                }
                painter.draw(canvas, width.toFloat(), baseline, depth, spans)
            }

            override fun generateDefaultLayoutParams(): LayoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }

@BindingAdapter("skAccounts")
fun SkAccountTabsView.bindAccounts(accounts: List<AccountModel>?) {
    setAccounts(accounts.orEmpty())
}

@BindingAdapter("skActiveAccount")
fun SkAccountTabsView.bindActiveAccount(account: AccountModel?) {
    setActiveIdentity(account?.identity)
}
