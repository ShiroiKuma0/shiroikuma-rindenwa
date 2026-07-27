package org.linphone.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the 白い熊 臨電話 UI theme model.
 *
 * Every themable surface is a [SkSlot]. A slot's color is the user override if one is stored,
 * otherwise an inherited default derived from the foundation slots (black background / yellow
 * text / yellow accent). Text slots additionally carry a font family / weight / size. Size-like
 * knobs (border thickness, corner radius) are [SkDimen]s — sliders that go all the way to 0.
 */

const val SK_PALETTE_BLACK = 0xFF000000.toInt()
const val SK_PALETTE_YELLOW = 0xFFFFFF00.toInt()
const val SK_UNSET = Int.MIN_VALUE

/** Top-level groups on the UI page — each renders as a big bold text-wide underlined heading. */
enum class SkSection(val labelRes: Int) {
    FOUNDATION(R.string.sk_section_foundation),
    TOP_BAR(R.string.sk_section_top_bar),
    MAIN_SCREEN(R.string.sk_section_main),
    CALLS(R.string.sk_section_calls),
    CONVERSATIONS(R.string.sk_section_conversations),
    CONTROLS(R.string.sk_section_controls),
}

enum class SkSlot(
    val key: String,
    val section: SkSection,
    val labelRes: Int,
    val hasFont: Boolean = false,
    val isFoundation: Boolean = false,
) {
    BACKGROUND("sk_background", SkSection.FOUNDATION, R.string.sk_slot_background, isFoundation = true),
    TEXT("sk_text", SkSection.FOUNDATION, R.string.sk_slot_text, hasFont = true, isFoundation = true),
    TEXT_SECONDARY("sk_text_secondary", SkSection.FOUNDATION, R.string.sk_slot_text_secondary, hasFont = true, isFoundation = true),
    ACCENT("sk_accent", SkSection.FOUNDATION, R.string.sk_slot_accent, isFoundation = true),

    TOOLBAR_BACKGROUND("sk_toolbar_background", SkSection.TOP_BAR, R.string.sk_slot_toolbar_background),
    TOOLBAR_TITLE("sk_toolbar_title", SkSection.TOP_BAR, R.string.sk_slot_toolbar_title, hasFont = true),
    TOOLBAR_ICON("sk_toolbar_icon", SkSection.TOP_BAR, R.string.sk_slot_toolbar_icon),

    LIST_NAME("sk_list_name", SkSection.MAIN_SCREEN, R.string.sk_slot_list_name, hasFont = true),
    /** The number + address-book label line of a call-history record. */
    LIST_NUMBER("sk_list_number", SkSection.MAIN_SCREEN, R.string.sk_slot_list_number, hasFont = true),
    LIST_DETAIL("sk_list_detail", SkSection.MAIN_SCREEN, R.string.sk_slot_list_detail, hasFont = true),
    LIST_BACKGROUND("sk_list_background", SkSection.MAIN_SCREEN, R.string.sk_slot_list_background),
    LIST_BORDER("sk_list_border", SkSection.MAIN_SCREEN, R.string.sk_slot_list_border),
    AVATAR_BACKGROUND("sk_avatar_background", SkSection.MAIN_SCREEN, R.string.sk_slot_avatar_background),
    AVATAR_TEXT("sk_avatar_text", SkSection.MAIN_SCREEN, R.string.sk_slot_avatar_text, hasFont = true),
    FAB_BACKGROUND("sk_fab_background", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_background),
    FAB_ICON("sk_fab_icon", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_icon),

    CALL_BACKGROUND("sk_call_background", SkSection.CALLS, R.string.sk_slot_call_background),
    CALL_NAME("sk_call_name", SkSection.CALLS, R.string.sk_slot_call_name, hasFont = true),
    CALL_STATUS("sk_call_status", SkSection.CALLS, R.string.sk_slot_call_status, hasFont = true),
    CALL_ANSWER("sk_call_answer", SkSection.CALLS, R.string.sk_slot_call_answer),
    CALL_HANGUP("sk_call_hangup", SkSection.CALLS, R.string.sk_slot_call_hangup),

    BUBBLE_OUTGOING("sk_bubble_outgoing", SkSection.CONVERSATIONS, R.string.sk_slot_bubble_outgoing),
    BUBBLE_OUTGOING_TEXT("sk_bubble_outgoing_text", SkSection.CONVERSATIONS, R.string.sk_slot_bubble_outgoing_text, hasFont = true),
    BUBBLE_INCOMING("sk_bubble_incoming", SkSection.CONVERSATIONS, R.string.sk_slot_bubble_incoming),
    BUBBLE_INCOMING_TEXT("sk_bubble_incoming_text", SkSection.CONVERSATIONS, R.string.sk_slot_bubble_incoming_text, hasFont = true),

    BUTTON_BACKGROUND("sk_button_background", SkSection.CONTROLS, R.string.sk_slot_button_background),
    BUTTON_TEXT("sk_button_text", SkSection.CONTROLS, R.string.sk_slot_button_text, hasFont = true),
    BUTTON_BORDER("sk_button_border", SkSection.CONTROLS, R.string.sk_slot_button_border),
    ;

    companion object {
        fun bySection(section: SkSection) = entries.filter { it.section == section }
    }
}

/** Slider-driven dp dimensions; every one of them goes down to 0. */
enum class SkDimen(val key: String, val labelRes: Int, val defaultDp: Int, val maxDp: Int) {
    LIST_BORDER_WIDTH("sk_list_border_width", R.string.sk_dimen_list_border_width, 1, 12),
    LIST_CORNER_RADIUS("sk_list_corner_radius", R.string.sk_dimen_list_corner_radius, 8, 32),
    BUBBLE_CORNER_RADIUS("sk_bubble_corner_radius", R.string.sk_dimen_bubble_corner_radius, 12, 32),
    BUBBLE_BORDER_WIDTH("sk_bubble_border_width", R.string.sk_dimen_bubble_border_width, 1, 12),
    BUTTON_BORDER_WIDTH("sk_button_border_width", R.string.sk_dimen_button_border_width, 2, 12),
    BUTTON_CORNER_RADIUS("sk_button_corner_radius", R.string.sk_dimen_button_corner_radius, 24, 40),
    AVATAR_CORNER_RADIUS("sk_avatar_corner_radius", R.string.sk_dimen_avatar_corner_radius, 24, 40),

    /** Vertical padding above and below a whole call-history record — the gap between records. */
    CALL_ROW_PADDING("sk_call_row_padding", R.string.sk_dimen_call_row_padding, 2, 24),

    /** Gap between the three lines *inside* one call-history record. */
    CALL_LINE_SPACING("sk_call_line_spacing", R.string.sk_dimen_call_line_spacing, 0, 16),
}

object SkTheme {
    const val FONT_FAMILY_PREFIX = "sk_font_family_"
    const val FONT_WEIGHT_PREFIX = "sk_font_weight_"
    const val FONT_SIZE_PREFIX = "sk_font_size_"
    const val RECENT_COLORS_KEY = "sk_recent_colors"
    const val RECENT_COLORS_MAX = 6
    const val MAX_FONT_SIZE_SP = 40

    /**
     * The app's default preference file — opened by name rather than through
     * `androidx.preference`, which upstream does not depend on. `<package>_preferences` is exactly
     * the file PreferenceManager would hand back, so anything else in the app that uses default
     * preferences sees the same store.
     */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(defaultPrefsName(context), Context.MODE_PRIVATE)

    fun defaultPrefsName(context: Context): String = context.packageName + "_preferences"

    // --- colors ---

    fun color(context: Context, slot: SkSlot): Int {
        val override = prefs(context).getInt(slot.key, SK_UNSET)
        return if (override != SK_UNSET) override else default(context, slot)
    }

    fun hasOverride(context: Context, slot: SkSlot): Boolean =
        prefs(context).getInt(slot.key, SK_UNSET) != SK_UNSET

    fun setColor(context: Context, slot: SkSlot, color: Int) {
        prefs(context).edit().putInt(slot.key, color).apply()
    }

    fun clearColor(context: Context, slot: SkSlot) {
        prefs(context).edit().remove(slot.key).apply()
    }

    /** Inherited defaults — the black/yellow house look, initialized for everything. */
    fun default(context: Context, slot: SkSlot): Int = when (slot) {
        SkSlot.BACKGROUND -> SK_PALETTE_BLACK
        SkSlot.TEXT -> SK_PALETTE_YELLOW
        SkSlot.TEXT_SECONDARY -> withAlpha(color(context, SkSlot.TEXT), 0.65f)
        SkSlot.ACCENT -> SK_PALETTE_YELLOW

        SkSlot.TOOLBAR_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.TOOLBAR_TITLE -> color(context, SkSlot.TEXT)
        SkSlot.TOOLBAR_ICON -> color(context, SkSlot.ACCENT)

        SkSlot.LIST_NAME -> color(context, SkSlot.TEXT)
        SkSlot.LIST_NUMBER -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.LIST_DETAIL -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.LIST_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.LIST_BORDER -> color(context, SkSlot.ACCENT)
        SkSlot.AVATAR_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.AVATAR_TEXT -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_BACKGROUND -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_ICON -> color(context, SkSlot.BACKGROUND)

        SkSlot.CALL_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.CALL_NAME -> color(context, SkSlot.TEXT)
        SkSlot.CALL_STATUS -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CALL_ANSWER -> 0xFF00C853.toInt()
        SkSlot.CALL_HANGUP -> 0xFFFF5252.toInt()

        SkSlot.BUBBLE_OUTGOING -> color(context, SkSlot.BACKGROUND)
        SkSlot.BUBBLE_OUTGOING_TEXT -> color(context, SkSlot.TEXT)
        SkSlot.BUBBLE_INCOMING -> color(context, SkSlot.BACKGROUND)
        SkSlot.BUBBLE_INCOMING_TEXT -> color(context, SkSlot.TEXT_SECONDARY)

        SkSlot.BUTTON_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.BUTTON_TEXT -> color(context, SkSlot.ACCENT)
        SkSlot.BUTTON_BORDER -> color(context, SkSlot.ACCENT)
    }

    // --- dimens ---

    fun dimenDp(context: Context, dimen: SkDimen): Int =
        prefs(context).getInt(dimen.key, dimen.defaultDp)

    fun setDimenDp(context: Context, dimen: SkDimen, dp: Int) {
        prefs(context).edit().putInt(dimen.key, dp).apply()
    }

    fun dimenPx(context: Context, dimen: SkDimen): Int =
        (dimenDp(context, dimen) * context.resources.displayMetrics.density).toInt()

    // --- fonts (per text slot) ---

    fun fontFamily(context: Context, slot: SkSlot): String =
        prefs(context).getString(FONT_FAMILY_PREFIX + slot.key, "") ?: ""

    fun setFontFamily(context: Context, slot: SkSlot, value: String) {
        prefs(context).edit().putString(FONT_FAMILY_PREFIX + slot.key, value).apply()
    }

    fun fontWeight(context: Context, slot: SkSlot): Int =
        prefs(context).getInt(FONT_WEIGHT_PREFIX + slot.key, 0)

    fun setFontWeight(context: Context, slot: SkSlot, value: Int) {
        prefs(context).edit().putInt(FONT_WEIGHT_PREFIX + slot.key, value).apply()
    }

    fun fontSize(context: Context, slot: SkSlot): Int =
        prefs(context).getInt(FONT_SIZE_PREFIX + slot.key, 0)

    fun setFontSize(context: Context, slot: SkSlot, value: Int) {
        prefs(context).edit().putInt(FONT_SIZE_PREFIX + slot.key, value).apply()
    }

    // --- recent colors (shared across all pickers) ---

    fun recentColors(context: Context): List<Int> =
        (prefs(context).getString(RECENT_COLORS_KEY, "") ?: "")
            .split(',')
            .mapNotNull { it.trim().toLongOrNull()?.toInt() }

    fun addRecentColor(context: Context, color: Int) {
        val updated = (listOf(color) + recentColors(context).filter { it != color })
            .take(RECENT_COLORS_MAX)
        prefs(context).edit()
            .putString(RECENT_COLORS_KEY, updated.joinToString(",") { it.toString() })
            .apply()
    }

    // --- helpers ---

    fun withAlpha(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha * 255).toInt().coerceIn(0, 255))

    fun hexString(color: Int): String = String.format("#%08X", color)

    /** A readable contrast color (for text drawn over [color]). */
    fun contrastColor(color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color or 0xFF000000.toInt())
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}
