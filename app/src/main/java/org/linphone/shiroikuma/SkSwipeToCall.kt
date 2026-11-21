package org.linphone.shiroikuma

import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R

/**
 * A call in the history is dialled by sweeping its row aside, to the left or to the right
 * (白い熊, 2026-11-20) — the same call the row's handset places, through the account the row's
 * call used.
 *
 * While the row moves, a handset in the call colour stands in the gap it leaves; it stops at the
 * distance from which letting go dials. The row comes back to its place afterwards: nothing is
 * removed from the list.
 */
class SkSwipeToCall(
    private val canCall: (RecyclerView.ViewHolder) -> Boolean,
    private val onCall: (RecyclerView.ViewHolder) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
        if (canCall(viewHolder)) super.getSwipeDirs(recyclerView, viewHolder) else 0

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = onCall(viewHolder)

    // A third of the row: far enough that scrolling the list askew never dials.
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = THRESHOLD

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val row = viewHolder.itemView
        val icon = ContextCompat.getDrawable(recyclerView.context, R.drawable.phone)?.mutate()
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && icon != null && dX != 0f) {
            val density = recyclerView.resources.displayMetrics.density
            val size = (ICON_DP * density).toInt().coerceAtMost(row.height)
            val margin = (MARGIN_DP * density).toInt()
            val shown = minOf(kotlin.math.abs(dX), THRESHOLD * row.width).toInt()
            if (shown > size + margin) {
                val top = row.top + (row.height - size) / 2
                val left = if (dX > 0) row.left + shown - size - margin else row.right - shown + margin
                icon.setTint(SkTheme.color(recyclerView.context, SkSlot.MENU_CALL))
                icon.setBounds(left, top, left + size, top + size)
                icon.draw(canvas)
            }
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private companion object {
        const val THRESHOLD = 0.33f
        const val ICON_DP = 32
        const val MARGIN_DP = 16
    }
}
