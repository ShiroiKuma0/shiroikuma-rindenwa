package org.linphone.shiroikuma

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.R
import org.linphone.databinding.DialogSkFontPickerBinding
import org.linphone.databinding.ItemSkFontOptionBinding

/**
 * shiroikuma-rindenwa fork — font picker that renders every font's name **in that font's own
 * glyphs**, with a trailing "Add font…" row for importing external ttf/otf files.
 */
class SkFontPickerDialog(
    private val activity: Activity,
    private val onAddFont: () -> Unit,
    private val onPick: (fileName: String) -> Unit,
) {
    init {
        val density = activity.resources.displayMetrics.density
        val binding = DialogSkFontPickerBinding.inflate(LayoutInflater.from(activity))
        val textColor = SkTheme.color(activity, SkSlot.TEXT)
        val accent = SkTheme.color(activity, SkSlot.ACCENT)
        val background = SkTheme.color(activity, SkSlot.BACKGROUND)

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (12 * density).toInt(), 0, (10 * density).toInt())
            this.background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(background)
                setStroke((2 * density).toInt(), accent)
            }
            addView(binding.root)
        }
        val dialog = AlertDialog.Builder(activity).setView(box).create()

        SkFonts.availableFontOptions(activity).forEach { option ->
            val row = ItemSkFontOptionBinding.inflate(
                LayoutInflater.from(activity), binding.skFontPickerHolder, false,
            )
            row.skFontOptionLabel.text = option.displayName
            row.skFontOptionLabel.setTextColor(textColor)
            // The whole point: each option is drawn in the font it selects.
            row.skFontOptionLabel.typeface = SkFonts.typeface(activity, option.fileName)
            row.skFontOptionLabel.setOnClickListener {
                dialog.dismiss()
                onPick(option.fileName)
            }
            binding.skFontPickerHolder.addView(row.root)
        }

        val addRow = ItemSkFontOptionBinding.inflate(
            LayoutInflater.from(activity), binding.skFontPickerHolder, false,
        )
        addRow.skFontOptionLabel.text = activity.getString(R.string.sk_add_font)
        addRow.skFontOptionLabel.setTextColor(accent)
        addRow.skFontOptionLabel.setOnClickListener {
            dialog.dismiss()
            onAddFont()
        }
        binding.skFontPickerHolder.addView(addRow.root)

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (10 * density).toInt(), (16 * density).toInt(), 0)
            addView(
                AppCompatTextView(activity).apply {
                    text = activity.getString(R.string.sk_cancel)
                    textSize = 14f
                    setTextColor(accent)
                    isClickable = true
                    isFocusable = true
                    this.background = GradientDrawable().apply {
                        setColor(background)
                        cornerRadius = 50 * density
                        setStroke((1.5f * density).toInt(), accent)
                    }
                    setPadding(
                        (18 * density).toInt(), (8 * density).toInt(),
                        (18 * density).toInt(), (8 * density).toInt(),
                    )
                    setOnClickListener { dialog.dismiss() }
                },
            )
        }
        box.addView(bar)

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
