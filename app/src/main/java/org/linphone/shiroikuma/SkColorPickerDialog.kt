package org.linphone.shiroikuma

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.R
import org.linphone.databinding.DialogSkColorPickerBinding

/**
 * shiroikuma-rindenwa fork — the house color picker: four RGBA sliders with a live preview, and
 * one-click boxes prefilled with the prior-selected colors above.
 *
 * [callback] receives (wasPositive, color). When [showDefault] is true a neutral "Default" pill is
 * shown; it reports wasPositive=false so the caller resets the slot to its inherited value.
 *
 * The surface is hand-drawn (black box, yellow border, pill buttons) for the same reason the
 * Export/Import panel is: Material tints its own dialog surface and button backgrounds, which
 * destroys the black-yellow look.
 */
class SkColorPickerDialog(
    private val activity: Activity,
    initialColor: Int,
    private val showDefault: Boolean = true,
    private val callback: (wasPositive: Boolean, color: Int) -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private val accent = SkTheme.color(activity, SkSlot.ACCENT)
    private val background = SkTheme.color(activity, SkSlot.BACKGROUND)
    private val textColor = SkTheme.color(activity, SkSlot.TEXT)

    private val binding = DialogSkColorPickerBinding.inflate(LayoutInflater.from(activity))
    private var red = Color.red(initialColor)
    private var green = Color.green(initialColor)
    private var blue = Color.blue(initialColor)
    private var alpha = Color.alpha(initialColor)

    init {
        tintChrome()
        setSwatch(binding.skPickerOldColor, initialColor)
        setupSlider(binding.skSliderR, binding.skSliderRValue, red) { red = it }
        setupSlider(binding.skSliderG, binding.skSliderGValue, green) { green = it }
        setupSlider(binding.skSliderB, binding.skSliderBValue, blue) { blue = it }
        setupSlider(binding.skSliderA, binding.skSliderAValue, alpha) { alpha = it }
        setupRecentColors()
        updatePreview()

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(this@SkColorPickerDialog.background)
                setStroke(dp(2), accent)
            }
            addView(binding.root)
        }

        val dialog = AlertDialog.Builder(activity).setView(box).create()

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        bar.addView(pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        if (showDefault) {
            bar.addView(
                pill(activity.getString(R.string.sk_default)) {
                    dialog.dismiss()
                    callback(false, 0)
                },
            )
        }
        bar.addView(
            pill(activity.getString(R.string.sk_ok)) {
                val picked = currentColor()
                SkTheme.addRecentColor(activity, picked)
                dialog.dismiss()
                callback(true, picked)
            },
        )
        box.addView(bar)

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun tintChrome() {
        listOf(
            binding.skSliderRLabel, binding.skSliderGLabel,
            binding.skSliderBLabel, binding.skSliderALabel,
        ).forEach { it.setTextColor(accent) }
        listOf(
            binding.skSliderRValue, binding.skSliderGValue,
            binding.skSliderBValue, binding.skSliderAValue,
        ).forEach { it.setTextColor(textColor) }
        binding.skPickerArrow.setTextColor(textColor)
        binding.skPickerHex.setTextColor(accent)
        listOf(binding.skSliderR, binding.skSliderG, binding.skSliderB, binding.skSliderA)
            .forEach {
                it.progressTintList = android.content.res.ColorStateList.valueOf(accent)
                it.thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            }
    }

    private fun currentColor(): Int = Color.argb(alpha, red, green, blue)

    private fun setupSlider(
        seekBar: SeekBar,
        valueView: TextView,
        initial: Int,
        onChange: (Int) -> Unit,
    ) {
        seekBar.max = 255
        seekBar.progress = initial
        valueView.text = initial.toString()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChange(progress)
                valueView.text = progress.toString()
                updatePreview()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
    }

    /** The one-click boxes above the sliders, prefilled with the prior-selected colors. */
    private fun setupRecentColors() {
        val recents = SkTheme.recentColors(activity)
        if (recents.isEmpty()) {
            binding.skRecentColors.visibility = View.GONE
            return
        }
        val size = dp(34)
        val margin = dp(6)
        recents.take(SkTheme.RECENT_COLORS_MAX).forEach { recent ->
            val box = ImageView(activity)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = margin
            box.layoutParams = params
            box.background = swatchDrawable(recent)
            box.setOnClickListener { applyColor(recent) }
            binding.skRecentColors.addView(box)
        }
    }

    private fun applyColor(color: Int) {
        red = Color.red(color)
        green = Color.green(color)
        blue = Color.blue(color)
        alpha = Color.alpha(color)
        binding.skSliderR.progress = red
        binding.skSliderG.progress = green
        binding.skSliderB.progress = blue
        binding.skSliderA.progress = alpha
        updatePreview()
    }

    private fun updatePreview() {
        setSwatch(binding.skPickerNewColor, currentColor())
        binding.skPickerHex.text = SkTheme.hexString(currentColor())
    }

    private fun setSwatch(view: View, color: Int) {
        view.background = swatchDrawable(color)
    }

    private fun swatchDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 6 * density
        setColor(color)
        setStroke((1.5f * density).toInt(), accent)
    }

    private fun pill(label: String, onClick: () -> Unit): TextView =
        AppCompatTextView(activity).apply {
            text = label
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
                GradientDrawable().apply {
                    setColor(this@SkColorPickerDialog.background)
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
}
