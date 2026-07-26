package org.linphone.shiroikuma

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * shiroikuma-rindenwa fork — draws a row of card-folder tabs.
 *
 * One continuous **bold, opaque** line runs the full width of the strip; where a tab is in the
 * foreground the line leaves that baseline, curls outward, runs along both sides and around the two
 * far corners — the tab you are looking at, open on the baseline side because it *is* the panel
 * behind it. Every other tab is the same box drawn **thinner and more transparent** behind that
 * line: every tab in the folder is the same size, what tells them apart is which one is in front.
 *
 * [pointUp] picks the direction the tabs stand out in: the bottom navigation bar hangs its tabs
 * downwards from the panel above, the account strip stands its tabs up from the panel below.
 * Everything else — the shape, the weights, the colours — is shared, so the two strips are visibly
 * the same object seen from two sides.
 */
class SkFolderTabPainter(context: Context, pointUp: Boolean) {
    companion object {
        private const val FLARE_DP = 6f
        private const val SHOULDER_DP = 10f
        private const val CORNER_DP = 12f
        private const val FRONT_STROKE_DP = 3f
        private const val BACK_STROKE_DP = 1.5f

        /** Filed-away tabs are the same colour, just further away. */
        const val BACK_ALPHA = 0.3f
    }

    /** One tab: where it starts and ends, and how far forward it is (1 = selected, 0 = filed). */
    class Span(var left: Float = 0f, var right: Float = 0f, var weight: Float = 0f)

    private val density = context.resources.displayMetrics.density
    private val flare = FLARE_DP * density
    private val shoulder = SHOULDER_DP * density
    private val corner = CORNER_DP * density
    private val sign = if (pointUp) -1f else 1f
    private val path = Path()

    val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = FRONT_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BACK_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Half the bold stroke — how far the baseline has to sit inside the view to not be clipped. */
    val baselineInset: Float
        get() = frontPaint.strokeWidth / 2f

    fun refreshColors(context: Context) {
        val accent = SkTheme.color(context, SkSlot.ACCENT)
        frontPaint.color = accent
        backPaint.color = SkTheme.withAlpha(accent, BACK_ALPHA)
    }

    /**
     * Draws the whole strip: the filed-away tabs first, then the panel edge across [width] at
     * [baseline], diving [depth] around whichever tabs carry weight.
     */
    fun draw(canvas: Canvas, width: Float, baseline: Float, depth: Float, spans: List<Span>) {
        if (depth <= 0f) return

        for (span in spans) {
            val fade = 1f - span.weight
            if (fade <= 0.01f || span.right <= span.left) continue
            backPaint.alpha = (255 * BACK_ALPHA * fade).toInt()
            path.reset()
            path.moveTo(span.left - shoulder, baseline)
            outline(span.left, span.right, baseline, depth)
            canvas.drawPath(path, backPaint)
        }

        path.reset()
        path.moveTo(0f, baseline)
        for (span in spans) {
            if (span.weight <= 0.01f || span.right <= span.left) continue
            path.lineTo(span.left - shoulder, baseline)
            outline(span.left, span.right, baseline, depth * span.weight)
        }
        path.lineTo(width, baseline)
        canvas.drawPath(path, frontPaint)
    }

    /**
     * Leaves the baseline with an outward curl at [left], runs along that side, around the two far
     * corners and back down the [right] side, then curls back onto the baseline. The path is
     * entered at `(left - shoulder, baseline)` and left at `(right + shoulder, baseline)`, so
     * several tabs chain onto one continuous line.
     */
    private fun outline(left: Float, right: Float, baseline: Float, depth: Float) {
        val curl = minOf(flare, depth / 2f)
        val far = baseline + sign * depth
        val radius = minOf(corner, (right - left) / 2f, depth - curl).coerceAtLeast(0f)
        val nearSide = baseline + sign * curl
        val farSide = far - sign * radius

        path.cubicTo(left - shoulder / 2f, baseline, left, baseline, left, nearSide)
        path.lineTo(left, farSide)
        path.quadTo(left, far, left + radius, far)
        path.lineTo(right - radius, far)
        path.quadTo(right, far, right, farSide)
        path.lineTo(right, nearSide)
        path.cubicTo(right, baseline, right + shoulder / 2f, baseline, right + shoulder, baseline)
    }
}
