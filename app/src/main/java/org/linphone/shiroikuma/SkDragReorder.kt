package org.linphone.shiroikuma

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs

/**
 * shiroikuma-rindenwa fork — long-press, buzz, drag to reorder.
 *
 * Installed on a container whose children are laid out in a single line, in either direction: the
 * account tab strip drags sideways, the drawer menu's account list drags up and down. A long press
 * lifts the child under the finger (haptic feedback, raised and slightly faded), the others slide
 * out of its way as it passes them, and on release [onReorder] is told where it landed.
 *
 * Children come and go — both lists are rebuilt from a data-bound list whenever the accounts
 * change — so the listeners are installed through the container's hierarchy-change hook rather than
 * once over the current children.
 */
class SkDragReorder(
    private val container: ViewGroup,
    private val vertical: Boolean,
    private val onReorder: (from: Int, to: Int) -> Unit,
) {
    companion object {
        private const val LIFT_DP = 8f
        private const val LIFT_ALPHA = 0.9f
        private const val SHIFT_DURATION_MS = 140L
    }

    /**
     * Called whenever a child has moved. A container that draws something around its children —
     * the account strip draws a tab around each — has to repaint itself: moving a child alone does
     * not re-record the parent's display list.
     */
    var onUpdate: (() -> Unit)? = null

    private val lift = LIFT_DP * container.resources.displayMetrics.density

    private var dragged: View? = null
    private var fromIndex = -1
    private var toIndex = -1
    private var anchor = 0f
    private var latest = 0f

    /** Where every child sat when the drag started, and how much room it takes. */
    private val starts = ArrayList<Float>()
    private val extents = ArrayList<Float>()
    private var gap = 0f

    fun attach() {
        container.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) = install(child)

            override fun onChildViewRemoved(parent: View, child: View) = Unit
        })
        for (i in 0 until container.childCount) {
            install(container.getChildAt(i))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun install(child: View) {
        child.setOnLongClickListener { view -> begin(view) }
        child.setOnTouchListener { _, event -> onTouch(event) }
    }

    private fun begin(view: View): Boolean {
        if (container.childCount < 2) return false

        dragged = view
        fromIndex = container.indexOfChild(view)
        toIndex = fromIndex
        anchor = latest
        capture()

        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        view.translationZ = lift
        view.alpha = LIFT_ALPHA
        container.parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun onTouch(event: MotionEvent): Boolean {
        latest = if (vertical) event.rawY else event.rawX

        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val view = dragged ?: return false
                val delta = latest - anchor
                if (vertical) view.translationY = delta else view.translationX = delta
                updateTarget(delta)
                onUpdate?.invoke()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragged == null) false else finish()
            }
            else -> false
        }
    }

    /** Snapshot the resting layout — every position below is computed against it. */
    private fun capture() {
        starts.clear()
        extents.clear()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            starts.add((if (vertical) child.top else child.left).toFloat())
            extents.add((if (vertical) child.height else child.width).toFloat())
        }
        gap = if (starts.size > 1) {
            (starts[1] - starts[0] - extents[0]).coerceAtLeast(0f)
        } else {
            0f
        }
    }

    /** The children, by index, in the order they would sit in if the drag ended at [target]. */
    private fun orderAt(target: Int): List<Int> {
        val order = (0 until starts.size).filter { it != fromIndex }.toMutableList()
        order.add(target.coerceIn(0, order.size), fromIndex)
        return order
    }

    /** Where the dragged child would come to rest at [target]. */
    private fun restingStart(target: Int): Float {
        var position = starts[0]
        for (index in orderAt(target)) {
            if (index == fromIndex) break
            position += extents[index] + gap
        }
        return position
    }

    private fun updateTarget(delta: Float) {
        val dragStart = starts[fromIndex] + delta
        var best = fromIndex
        var bestDistance = Float.MAX_VALUE
        for (target in starts.indices) {
            val distance = abs(restingStart(target) - dragStart)
            if (distance < bestDistance) {
                bestDistance = distance
                best = target
            }
        }
        if (best == toIndex) return

        toIndex = best
        var position = starts[0]
        for (index in orderAt(best)) {
            if (index != fromIndex) {
                val shift = position - starts[index]
                val child = container.getChildAt(index)
                child.animate().apply {
                    duration = SHIFT_DURATION_MS
                    if (vertical) translationY(shift) else translationX(shift)
                    setUpdateListener { onUpdate?.invoke() }
                    start()
                }
            }
            position += extents[index] + gap
        }
    }

    private fun finish(): Boolean {
        val from = fromIndex
        val to = toIndex
        dragged?.translationZ = 0f
        dragged?.alpha = 1f
        dragged = null
        fromIndex = -1
        toIndex = -1

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.animate().cancel()
            child.translationX = 0f
            child.translationY = 0f
        }
        container.parent?.requestDisallowInterceptTouchEvent(false)
        onUpdate?.invoke()

        if (to >= 0 && to != from) {
            onReorder(from, to)
        }
        return true
    }
}
