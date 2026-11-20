package org.linphone.shiroikuma

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — a flash: one short line said in the middle of the display, in the
 * black-yellow look, gone again by itself (白い熊, 2026-11-20).
 *
 * Upstream's toasts slide in at the top with a green, blue or red frame and text, which is neither
 * where 白い熊 looks nor our colours; Android's own toast is grey at the bottom. A flash is the
 * theme's ground behind the theme's text, framed in the accent like a dialog, in the centre of the
 * window. **Only the icon keeps a colour of its own** — green for “done”, blue for “note”, red for
 * “this went wrong” ([Kind]) — which is all that is left of upstream's three kinds, and enough.
 *
 * Every flash of the app comes through here: `GenericActivity`'s `showGreenToast` /
 * `showBlueToast` / `showRedToast` hand theirs over, and the fork's own pages call [show]
 * directly. Two things do not: upstream's *persistent* red banner, which is a standing notice and
 * not a flash, and a message sent from where there is no window (`SkDiagReceiver`), which can only
 * be Android's toast.
 *
 * A flash takes no touches — what is under it stays usable — and a second one replaces the first.
 */
object SkFlash {
    private const val TAG_VIEW = "sk_flash"
    private const val FADE_MS = 180L
    private const val HOLD_MS = 3000L

    /** What colour the icon is; the box and the words are the theme's whatever the kind. */
    enum class Kind { DONE, NOTE, WRONG }

    @UiThread
    fun show(activity: Activity, @StringRes message: Int) = show(activity, activity.getString(message))

    /**
     * [icon] 0 is a flash of words alone. [tintIcon] false leaves a drawable that brings its own
     * colours as it is. [holdMs] is how long it stands between fading in and fading out.
     */
    @UiThread
    fun show(
        activity: Activity,
        message: String,
        @DrawableRes icon: Int = 0,
        kind: Kind = Kind.DONE,
        tintIcon: Boolean = true,
        holdMs: Long = HOLD_MS,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(TAG_VIEW)?.let { old ->
            old.animate().cancel()
            root.removeView(old)
        }

        val density = activity.resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).toInt()

        val flash = LinearLayout(activity).apply {
            tag = TAG_VIEW
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(22), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(SkTheme.color(activity, SkSlot.BACKGROUND))
                setStroke(dp(2), SkTheme.color(activity, SkSlot.ACCENT))
            }
            elevation = 12 * density
            isClickable = false
            isFocusable = false
            alpha = 0f
        }
        if (icon != 0) {
            flash.addView(
                ImageView(activity).apply {
                    setImageResource(icon)
                    if (tintIcon) setColorFilter(iconColor(activity, kind))
                },
                LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(12) },
            )
        }
        flash.addView(
            AppCompatTextView(activity).apply {
                text = message
                textSize = 16f
                setTextColor(SkTheme.color(activity, SkSlot.TEXT))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ).apply {
            marginStart = dp(32)
            marginEnd = dp(32)
        }
        root.addView(flash, params)

        flash.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
            flash.animate().alpha(0f).setStartDelay(holdMs).setDuration(FADE_MS).withEndAction {
                (flash.parent as? ViewGroup)?.removeView(flash)
            }
        }
    }

    /** The colours upstream's three toasts had, kept for the icon alone. */
    @ColorInt
    private fun iconColor(activity: Activity, kind: Kind): Int = ContextCompat.getColor(
        activity,
        when (kind) {
            Kind.DONE -> R.color.success_500
            Kind.NOTE -> R.color.info_500
            Kind.WRONG -> R.color.danger_500
        },
    )
}
