package org.linphone.shiroikuma

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the small black-yellow dialogs the UI page needs.
 *
 * Material's own dialogs tint their surface and button backgrounds, which destroys the
 * black-yellow look, so — exactly as in [SkEximportPanel] — the window stays transparent and the
 * bordered black box plus pill buttons are hand-drawn.
 */
private class SkDialogChrome(val activity: Activity) {
    val density = activity.resources.displayMetrics.density
    val accent = SkTheme.color(activity, SkSlot.ACCENT)
    val background = SkTheme.color(activity, SkSlot.BACKGROUND)
    val textColor = SkTheme.color(activity, SkSlot.TEXT)

    fun dp(value: Int): Int = (value * density).toInt()

    fun box(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(20), dp(22), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = 16 * density
            setColor(this@SkDialogChrome.background)
            setStroke(dp(2), accent)
        }
    }

    fun title(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 19f
        setTextColor(accent)
        setTypeface(typeface, Typeface.BOLD)
    }

    fun body(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 14f
        setTextColor(textColor)
        setPadding(0, dp(10), 0, 0)
    }

    fun pill(label: String, onClick: () -> Unit): TextView =
        AppCompatTextView(activity).apply {
            text = label
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
                GradientDrawable().apply {
                    setColor(this@SkDialogChrome.background)
                    cornerRadius = 50 * density
                    setStroke((1.5f * density).toInt(), accent)
                },
                null,
            )
            setPadding(dp(18), dp(8), dp(18), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8) }
            setOnClickListener { onClick() }
        }

    fun bar(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(14), 0, 0)
    }

    fun show(content: LinearLayout): AlertDialog =
        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(content) })
            .create()
            .also {
                it.show()
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
}

/** A yes/no confirmation in the house look; [onConfirm] runs only on the positive pill. */
class SkConfirmDialog(
    activity: Activity,
    title: String,
    message: String,
    confirmLabel: String? = null,
    onConfirm: () -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        box.addView(chrome.body(message))
        val bar = chrome.bar()
        lateinit var dialog: AlertDialog
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        bar.addView(
            chrome.pill(confirmLabel ?: activity.getString(R.string.sk_ok)) {
                dialog.dismiss()
                onConfirm()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
    }
}

/** A single-choice list in the house look — used for the per-slot font weight. */
class SkChoiceDialog(
    activity: Activity,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (index: Int) -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        lateinit var dialog: AlertDialog
        options.forEachIndexed { index, label ->
            box.addView(
                TextView(activity).apply {
                    text = if (index == selectedIndex) "• $label" else label
                    textSize = 16f
                    setTextColor(if (index == selectedIndex) chrome.accent else chrome.textColor)
                    isClickable = true
                    isFocusable = true
                    setPadding(chrome.dp(4), chrome.dp(9), chrome.dp(4), chrome.dp(9))
                    setOnClickListener {
                        dialog.dismiss()
                        onPick(index)
                    }
                },
            )
        }
        val bar = chrome.bar()
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        box.addView(bar)
        dialog = chrome.show(box)
    }
}
