package org.linphone.shiroikuma

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.widget.CompoundButtonCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * shiroikuma-rindenwa fork — the one place every inflated switch, radio button and check box
 * passes through.
 *
 * Named by `viewInflaterClass` in `Theme.SkBlackYellow`, so AppCompat hands it every view a layout
 * declares — upstream's screens, dialogs and sheets included, with no layout or call site touched.
 * It exists so that the two colours of a switch are the **settable roles** ([SkSlot.ACCENT] when
 * on, [SkSlot.SWITCH_OFF] when off) everywhere, not only on the 臨電話 UI page: declared in XML
 * alone, the off colour of upstream's switches was a constant no setting could reach.
 *
 * Radio buttons and check boxes stay what they are — selection marks (白い熊, 2026-11-21) — and
 * take the same two colours, so no white or grey mark is left.
 */
@Keep
class SkViewInflater : MaterialComponentsViewInflater() {

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? =
        if (name == MATERIAL_SWITCH) {
            MaterialSwitch(context, attrs).also { SkStyler.styleSwitch(it) }
        } else {
            super.createView(context, name, attrs)
        }

    override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton =
        super.createRadioButton(context, attrs).also {
            CompoundButtonCompat.setButtonTintList(it, marks(context))
        }

    override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox =
        super.createCheckBox(context, attrs).also {
            CompoundButtonCompat.setButtonTintList(it, marks(context))
        }

    private fun marks(context: Context) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(
            SkRoles.color(context, "mark_on"),
            SkRoles.color(context, "mark_off"),
        ),
    )

    private companion object {
        const val MATERIAL_SWITCH = "com.google.android.material.materialswitch.MaterialSwitch"
    }
}
