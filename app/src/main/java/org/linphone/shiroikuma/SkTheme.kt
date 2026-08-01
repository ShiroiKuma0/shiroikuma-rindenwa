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

/*
 * The shipped type scale, matched to the sister dialer (shiroikuma-denwa), whose call log 白い熊
 * reads far more comfortably than upstream Linphone's 14sp/12sp rows.
 *
 * Derived by measuring denwa's own rendering rather than guessing: both apps were captured on the
 * Mate XT in the same state (override density 390, font scale 1.3, 2048×2232), and rindenwa's
 * known 14sp and 12sp lines in that pair calibrate to exactly 3.00 px of CJK ink per declared sp
 * — a linear fit across both, so the scale is trustworthy. Measuring denwa's CJK lines against it:
 *
 *   · name          denwa 65-66 px em  → ~22 sp   (cross-checked against its Latin rows: an
 *                                                  ascender-to-descender span of 65 px, and a
 *                                                  no-descender span of 51 px, both agree)
 *   · time/duration denwa 52-53 px     → 18 sp    (denwa draws these at 0.8 × its name — 0.8 × 22
 *                                                  = 17.6 — and 52.5 px against this fork's own
 *                                                  measured 2.95 px per declared sp gives 17.8;
 *                                                  18 is where both land, so 18 it is)
 *   · day headline  denwa 83 px        → ~27 sp   (well above the 0.76 × name its layout would
 *                                                  give, so denwa carries a size override there)
 *
 * Confirmed afterwards against denwa's own preferences, pulled through its automation export:
 * `font_size_theme_call_log_name = 22` and `font_size_theme_call_log_day_date = 27`, exactly the
 * two numbers measured. Its time and duration lines carry no stored size — denwa derives them at
 * 0.8 × the name, which is the 17 below.
 *
 * That export also settled the weights: denwa stores NONE. Everything reads at its family's own
 * weight, and what looks heavy there is the font itself (Animo-Regular for names, A-OTF 勘亭流 Std
 * Ultra for the day headline), not a weight override — so nothing here fakes it with one.
 *
 * The Contacts list is sized from the sister address book (shiroikuma-renrakusaki) rather than
 * from denwa, taken out of its own settings rather than off a screenshot:
 *
 *   · letter heading  its stored override, `font_size_contacts_section_header = 21`
 *   · name and number 24 sp, and the 24 is the point. renrakusaki stores no size for either —
 *                     both fields carry `:0` in `contacts_list_fields`, and its `default_font_size`
 *                     of 2 (FONT_SIZE_LARGE) resolves to Commons' `big_text_size` = 18 sp. But it
 *                     applies that through `setTextSize(COMPLEX_UNIT_PX, getDimension(...))`,
 *                     which scales an sp resource LINEARLY by the system font scale, while our
 *                     `setTextSize(COMPLEX_UNIT_SP, …)` goes through Android's non-linear font
 *                     scaling (API 34+, and we target 37). At 白い熊's font scale of 1.3 the two
 *                     paths diverge by ~1.32×, so a declared 18 here renders a third smaller than
 *                     a declared 18 there. 18 × 1.32 ≈ 24.
 *
 * Measured rather than assumed, and the measurement is calibrated: the letter heading is 21 sp in
 * BOTH apps and both set it the same sp way, and it renders 62 px there against 63 px here — so
 * the fonts and the sp scale agree, and the row difference is real. The identical word "Androgeos"
 * then measures 70 px there against 53 px here, which is that same 1.32.
 */
private const val SK_SIZE_NAME = 22
private const val SK_SIZE_SECONDARY = 18
private const val SK_SIZE_DETAIL = 18
private const val SK_SIZE_DAY = 27
private const val SK_SIZE_SECTION = 21
private const val SK_SIZE_CONTACT = 24

/** Top-level groups on the UI page — each renders as a big bold text-wide underlined heading. */
enum class SkSection(val labelRes: Int) {
    FOUNDATION(R.string.sk_section_foundation),
    TOP_BAR(R.string.sk_section_top_bar),
    MAIN_SCREEN(R.string.sk_section_main),
    CALLS(R.string.sk_section_calls),
    CONVERSATIONS(R.string.sk_section_conversations),
    CONTROLS(R.string.sk_section_controls),
}

/**
 * A themable surface.
 *
 * [defaultSizeSp] and [defaultWeight] are the fork's own shipped typography: what a text slot
 * reads at with nothing stored for it. 0 means "leave the layout's own size / the family's own
 * weight alone", which is what every slot did before the call log was sized against the sister
 * dialer. A stored value always wins, and storing 0 is a real choice — it puts a slot back on the
 * layout's size.
 */
enum class SkSlot(
    val key: String,
    val section: SkSection,
    val labelRes: Int,
    val hasFont: Boolean = false,
    val isFoundation: Boolean = false,
    val defaultSizeSp: Int = 0,
    val defaultWeight: Int = 0,
) {
    BACKGROUND("sk_background", SkSection.FOUNDATION, R.string.sk_slot_background, isFoundation = true),
    TEXT("sk_text", SkSection.FOUNDATION, R.string.sk_slot_text, hasFont = true, isFoundation = true),
    TEXT_SECONDARY("sk_text_secondary", SkSection.FOUNDATION, R.string.sk_slot_text_secondary, hasFont = true, isFoundation = true),
    ACCENT("sk_accent", SkSection.FOUNDATION, R.string.sk_slot_accent, isFoundation = true),

    TOOLBAR_BACKGROUND("sk_toolbar_background", SkSection.TOP_BAR, R.string.sk_slot_toolbar_background),
    TOOLBAR_TITLE("sk_toolbar_title", SkSection.TOP_BAR, R.string.sk_slot_toolbar_title, hasFont = true),
    TOOLBAR_ICON("sk_toolbar_icon", SkSection.TOP_BAR, R.string.sk_slot_toolbar_icon),

    LIST_NAME("sk_list_name", SkSection.MAIN_SCREEN, R.string.sk_slot_list_name, hasFont = true, defaultSizeSp = SK_SIZE_CONTACT),
    /** The number + address-book label line of a call-history record. */
    LIST_NUMBER("sk_list_number", SkSection.MAIN_SCREEN, R.string.sk_slot_list_number, hasFont = true, defaultSizeSp = SK_SIZE_SECONDARY),
    LIST_DETAIL("sk_list_detail", SkSection.MAIN_SCREEN, R.string.sk_slot_list_detail, hasFont = true, defaultSizeSp = SK_SIZE_DETAIL),
    LIST_BACKGROUND("sk_list_background", SkSection.MAIN_SCREEN, R.string.sk_slot_list_background),
    LIST_BORDER("sk_list_border", SkSection.MAIN_SCREEN, R.string.sk_slot_list_border),
    AVATAR_BACKGROUND("sk_avatar_background", SkSection.MAIN_SCREEN, R.string.sk_slot_avatar_background),
    AVATAR_TEXT("sk_avatar_text", SkSection.MAIN_SCREEN, R.string.sk_slot_avatar_text, hasFont = true),
    FAB_BACKGROUND("sk_fab_background", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_background),
    FAB_ICON("sk_fab_icon", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_icon),

    /** The one-letter heading standing above the contacts filed under it. */
    CONTACT_SECTION("sk_contact_section", SkSection.MAIN_SCREEN, R.string.sk_slot_contact_section, hasFont = true, defaultSizeSp = SK_SIZE_SECTION),
    /** The rule under a letter heading's text. */
    CONTACT_SECTION_UNDERLINE("sk_contact_section_underline", SkSection.MAIN_SCREEN, R.string.sk_slot_contact_section_underline),
    /** The full-width band framing an open section. */
    CONTACT_SECTION_DIVIDER("sk_contact_section_divider", SkSection.MAIN_SCREEN, R.string.sk_slot_contact_section_divider),
    /** The phone number written under a contact's name. */
    CONTACT_NUMBER("sk_contact_number", SkSection.MAIN_SCREEN, R.string.sk_slot_contact_number, hasFont = true, defaultSizeSp = SK_SIZE_CONTACT),
    /** The line between one contact row and the next. */
    CONTACT_ROW_DIVIDER("sk_contact_row_divider", SkSection.MAIN_SCREEN, R.string.sk_slot_contact_row_divider),
    /** The name under a Favorites tile. */
    FAVOURITE_NAME("sk_favourite_name", SkSection.MAIN_SCREEN, R.string.sk_slot_favourite_name, hasFont = true, defaultSizeSp = SK_SIZE_CONTACT),

    CALL_BACKGROUND("sk_call_background", SkSection.CALLS, R.string.sk_slot_call_background),
    CALL_NAME("sk_call_name", SkSection.CALLS, R.string.sk_slot_call_name, hasFont = true),
    CALL_STATUS("sk_call_status", SkSection.CALLS, R.string.sk_slot_call_status, hasFont = true),
    CALL_ANSWER("sk_call_answer", SkSection.CALLS, R.string.sk_slot_call_answer),
    CALL_HANGUP("sk_call_hangup", SkSection.CALLS, R.string.sk_slot_call_hangup),

    /** The day headline standing above the calls made on that day. */
    CALL_DAY("sk_call_day", SkSection.CALLS, R.string.sk_slot_call_day, hasFont = true, defaultSizeSp = SK_SIZE_DAY),
    /** The rule drawn directly under the day headline's text. */
    CALL_DAY_UNDERLINE("sk_call_day_underline", SkSection.CALLS, R.string.sk_slot_call_day_underline),
    /** The band closing off the previous day, above the next headline. */
    CALL_DAY_DIVIDER("sk_call_day_divider", SkSection.CALLS, R.string.sk_slot_call_day_divider),
    /**
     * The caller's name on a call-history record. Its own slot rather than the shared
     * [LIST_NAME], so the call log can be set apart from the contacts and conversations lists.
     */
    CALL_ROW_NAME("sk_call_row_name", SkSection.CALLS, R.string.sk_slot_call_row_name, hasFont = true, defaultSizeSp = SK_SIZE_NAME),
    /** The time-of-day line of a call-history record. */
    CALL_TIME("sk_call_time", SkSection.CALLS, R.string.sk_slot_call_time, hasFont = true, defaultSizeSp = SK_SIZE_DETAIL),
    /** How long the call lasted, written after the time. */
    CALL_DURATION("sk_call_duration", SkSection.CALLS, R.string.sk_slot_call_duration, hasFont = true, defaultSizeSp = SK_SIZE_DETAIL),
    CALL_DIR_INCOMING("sk_call_dir_incoming", SkSection.CALLS, R.string.sk_slot_call_dir_incoming),
    CALL_DIR_OUTGOING("sk_call_dir_outgoing", SkSection.CALLS, R.string.sk_slot_call_dir_outgoing),
    CALL_DIR_MISSED("sk_call_dir_missed", SkSection.CALLS, R.string.sk_slot_call_dir_missed),
    /** The hairline between two records of the same day; 0 thick by default. */
    CALL_ROW_DIVIDER("sk_call_row_divider", SkSection.CALLS, R.string.sk_slot_call_row_divider),

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
enum class SkDimen(
    val key: String,
    val labelRes: Int,
    val defaultDp: Int,
    val maxDp: Int,
    /** A plain count rather than a dp length — the row labels it without a unit. */
    val isCount: Boolean = false,
    val minValue: Int = 0,
) {
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

    /** The rule under a day headline's text — 0 removes it. */
    CALL_DAY_UNDERLINE_WIDTH("sk_call_day_underline_width", R.string.sk_dimen_call_day_underline, 2, 12),

    /** The band above a day headline, closing off the day before it — 0 removes it. */
    CALL_DAY_DIVIDER_WIDTH("sk_call_day_divider_width", R.string.sk_dimen_call_day_divider, 4, 24),

    /** The hairline between two records of the same day — off unless raised. */
    CALL_ROW_DIVIDER_WIDTH("sk_call_row_divider_width", R.string.sk_dimen_call_row_divider, 1, 12),

    // The Contacts letter headings — the sister address book's own live values.
    CONTACT_SECTION_UNDERLINE_WIDTH("sk_contact_section_underline_width", R.string.sk_dimen_contact_section_underline, 4, 12),
    CONTACT_SECTION_DIVIDER_WIDTH("sk_contact_section_divider_width", R.string.sk_dimen_contact_section_divider, 0, 24),
    CONTACT_SECTION_PADDING("sk_contact_section_padding", R.string.sk_dimen_contact_section_padding, 2, 24),

    // The contact rows themselves — 96 dp is the sister address book's own thumbnail size.
    CONTACT_AVATAR_SIZE("sk_contact_avatar_size", R.string.sk_dimen_contact_avatar, 96, 128),
    CONTACT_ROW_DIVIDER_WIDTH("sk_contact_row_divider_width", R.string.sk_dimen_contact_row_divider, 1, 12),
    CONTACT_ROW_PADDING("sk_contact_row_padding", R.string.sk_dimen_contact_row_padding, 0, 24),

    // The Favorites tile grid — five across, as the sister address book lays it out.
    /** The photo on a call-history record — big enough to span all three of its lines. */
    CALL_AVATAR_SIZE("sk_call_avatar_size", R.string.sk_dimen_call_avatar, 96, 160),

    FAVOURITE_COLUMNS("sk_favourite_columns", R.string.sk_dimen_favourite_columns, 5, 8, isCount = true, minValue = 1),
    FAVOURITE_AVATAR_SIZE("sk_favourite_avatar_size", R.string.sk_dimen_favourite_avatar, 96, 160),
    FAVOURITE_TILE_PADDING("sk_favourite_tile_padding", R.string.sk_dimen_favourite_tile_padding, 6, 24),
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
        SkSlot.CONTACT_SECTION -> color(context, SkSlot.ACCENT)
        SkSlot.CONTACT_SECTION_UNDERLINE -> color(context, SkSlot.ACCENT)
        SkSlot.CONTACT_SECTION_DIVIDER -> color(context, SkSlot.ACCENT)
        SkSlot.CONTACT_NUMBER -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CONTACT_ROW_DIVIDER -> color(context, SkSlot.ACCENT)
        SkSlot.FAVOURITE_NAME -> color(context, SkSlot.LIST_NAME)
        SkSlot.AVATAR_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.AVATAR_TEXT -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_BACKGROUND -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_ICON -> color(context, SkSlot.BACKGROUND)

        SkSlot.CALL_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.CALL_NAME -> color(context, SkSlot.TEXT)
        SkSlot.CALL_STATUS -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CALL_ANSWER -> 0xFF00C853.toInt()
        SkSlot.CALL_HANGUP -> 0xFFFF5252.toInt()

        SkSlot.CALL_DAY -> color(context, SkSlot.ACCENT)
        SkSlot.CALL_DAY_UNDERLINE -> color(context, SkSlot.ACCENT)
        SkSlot.CALL_DAY_DIVIDER -> color(context, SkSlot.ACCENT)
        SkSlot.CALL_ROW_NAME -> color(context, SkSlot.LIST_NAME)
        SkSlot.CALL_TIME -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CALL_DURATION -> color(context, SkSlot.TEXT_SECONDARY)
        // The three arrows keep their conventional meaning rather than inheriting the accent —
        // the same red / blue / green the sister dialer uses, so a missed call still reads as one.
        SkSlot.CALL_DIR_INCOMING -> 0xFF1E88E5.toInt()
        SkSlot.CALL_DIR_OUTGOING -> 0xFF43A047.toInt()
        SkSlot.CALL_DIR_MISSED -> 0xFFD32F2F.toInt()
        SkSlot.CALL_ROW_DIVIDER -> color(context, SkSlot.LIST_BORDER)

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
        prefs(context).getInt(FONT_WEIGHT_PREFIX + slot.key, slot.defaultWeight)

    fun setFontWeight(context: Context, slot: SkSlot, value: Int) {
        prefs(context).edit().putInt(FONT_WEIGHT_PREFIX + slot.key, value).apply()
    }

    fun fontSize(context: Context, slot: SkSlot): Int =
        prefs(context).getInt(FONT_SIZE_PREFIX + slot.key, slot.defaultSizeSp)

    /**
     * Whether this slot's font carries a stored value at all — as opposed to reading at the
     * fork's shipped defaults. Only a stored one justifies the runtime restyling pass, which is
     * why [SkStyler.refreshOverrideState] asks this rather than comparing values against 0.
     */
    fun hasFontOverride(context: Context, slot: SkSlot): Boolean = prefs(context).run {
        contains(FONT_FAMILY_PREFIX + slot.key) ||
            contains(FONT_WEIGHT_PREFIX + slot.key) ||
            contains(FONT_SIZE_PREFIX + slot.key)
    }

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
