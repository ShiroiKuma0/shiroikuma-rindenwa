package org.linphone.shiroikuma

import android.content.Context
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the colour role table.
 *
 * **The one place a colour is written.** `#FFFF00` and `#000000` are the defaults of
 * [SkSlot.ACCENT] / [SkSlot.TEXT] and [SkSlot.BACKGROUND] in [SkTheme]; every role here falls back to
 * them, directly or through another role, unless it carries a meaning of its own (danger red,
 * success green, …). Nothing in `res/` holds a colour: XML names a role (`@color/sk_…`, a theme
 * attribute that points at one, or upstream’s own colour names) and [SkPalette] feeds the value
 * in at run time. See `values/sk_colors.xml`.
 *
 * A role is one thing 白い熊 can see as a separate thing, and is set on the 臨電話 UI page in the
 * section of its [SkArea]. **A new coloured thing gets a new role here, in the same commit** —
 * and a key is never renamed: it is the preference key (`sk_role_<key>`) and travels in exports.
 */
enum class SkArea(val key: String, val label: String, val icon: Int) {
    ACCENT("accent", "Accent", R.drawable.sk_ic_drop),
    TEXT("text", "Text", R.drawable.sk_ic_text),
    SURFACES("surfaces", "Surfaces", R.drawable.squares_four),
    BUTTONS("buttons", "Buttons", R.drawable.power),
    DIALOGS("dialogs", "Dialogs", R.drawable.chat_teardrop_text),
    SWITCHES("switches", "Switches and marks", R.drawable.check),
    MENUS("menus", "Menus", R.drawable.list),
    LISTS("lists", "Lists", R.drawable.list),
    BARS("bars", "Bars and tabs", R.drawable.layout),
    FIELDS("fields", "Text fields", R.drawable.pencil_simple),
    STATUS("status", "Status colours", R.drawable.info),
    AVATARS("avatars", "Avatars", R.drawable.user_circle),
    BUBBLES("bubbles", "Conversations", R.drawable.chat_teardrop_text),
    IN_CALL("in_call", "Call screens", R.drawable.phone_call),
    DIAL("dial", "Dial pad", R.drawable.numpad),
    MARKS("marks", "Badges and marks", R.drawable.sk_star),
    SHEETS("sheets", "Bottom sheets", R.drawable.caret_up),
    FLASH("flash", "Flashes", R.drawable.bell),
    PAGE("page", "This page", R.drawable.gear),
    ASSISTANT("assistant", "Assistant", R.drawable.user_plus),
    MEETINGS("meetings", "Meetings", R.drawable.calendar),
    SCREEN_MAIN("screen_main", "Main frame", R.drawable.layout),
    SCREEN_HISTORY("screen_history", "History", R.drawable.clock),
    SCREEN_CONTACTS("screen_contacts", "Contacts", R.drawable.users),
    SCREEN_CALL("screen_call", "Call screens", R.drawable.phone_call),
    SCREEN_CHAT("screen_chat", "Conversations", R.drawable.chat_teardrop_text),
    SCREEN_START("screen_start", "New call and new conversation", R.drawable.phone_plus),
    SCREEN_SETTINGS("screen_settings", "Settings", R.drawable.gear),
    SCREEN_ACCOUNT("screen_account", "Account pages", R.drawable.user_circle),
    SCREEN_DRAWER("screen_drawer", "Drawer", R.drawable.list),
    SCREEN_DIALOGS("screen_dialogs", "Upstream's dialogs", R.drawable.chat_teardrop_text),
    SCREEN_MENUS("screen_menus", "Upstream's menus", R.drawable.list),
    SCREEN_RECORDINGS("screen_recordings", "Recordings", R.drawable.record_fill),
    SCREEN_HELP("screen_help", "Help", R.drawable.question),
    SCREEN_VIEWERS("screen_viewers", "File viewers", R.drawable.file),
    SCREEN_OTHER("screen_other", "Other layouts", R.drawable.squares_four),
    STYLES("styles", "Text styles", R.drawable.sk_ic_text),
    SHAPES("shapes", "Shapes", R.drawable.squares_four),
    STATES("states", "State colours", R.drawable.check),
}

/** [resId] is the colour resource the loader feeds for this role; 0 for a role only code reads. */
class SkRole(
    val key: String,
    val resId: Int,
    val area: SkArea,
    val label: String,
    val default: (Context) -> Int,
) {
    /** A role that simply follows another one — [base], by key — until it is set itself. */
    constructor(key: String, resId: Int, area: SkArea, label: String, base: String) :
        this(key, resId, area, label, { c -> SkRoles.color(c, base) })
}

object SkRoles {
    private const val PREFIX = "sk_role_"

    private fun bg(c: Context) = SkTheme.color(c, SkSlot.BACKGROUND)

    private fun tx(c: Context) = SkTheme.color(c, SkSlot.TEXT)

    private fun ac(c: Context) = SkTheme.color(c, SkSlot.ACCENT)

    private fun r(c: Context, key: String) = color(c, key)

    /** The roles written by hand; [ALL] adds the generated ones (one per item of upstream's XML). */
    private val HAND: List<SkRole> = listOf(
        SkRole("sk_yellow", R.color.sk_yellow, SkArea.ACCENT, "Accent of upstream’s screens — icons, frames, active states") { c -> ac(c) },
        SkRole("sk_yellow_wash", R.color.sk_yellow_wash, SkArea.BUTTONS, "Wash of a pressed button or a selected row") { c -> SkTheme.withAlpha(r(c, "sk_yellow"), 0.2f) },
        SkRole("sk_yellow_wash_strong", R.color.sk_yellow_wash_strong, SkArea.BUTTONS, "Stronger wash of a pressed main button") { c -> SkTheme.withAlpha(r(c, "sk_yellow"), 0.35f) },
        SkRole("sk_yellow_38", R.color.sk_yellow_38, SkArea.BUTTONS, "Frame and label of a disabled button") { c -> SkTheme.withAlpha(r(c, "sk_yellow"), 0.38f) },
        SkRole("sk_disabled", R.color.sk_disabled, SkArea.BUTTONS, "Ground of a disabled round button") { c -> 0xFF3A3A3A.toInt() },
        SkRole("sk_black", R.color.sk_black, SkArea.SURFACES, "Content drawn on the accent") { c -> bg(c) },
        SkRole("sk_surface", R.color.sk_surface, SkArea.SURFACES, "Base surface") { c -> bg(c) },
        SkRole("sk_surface_alt", R.color.sk_surface_alt, SkArea.SURFACES, "Alternate surface — fields, sheets, cards") { c -> SkTheme.blend(bg(c), tx(c), 0.04f) },
        SkRole("sk_surface_raised", R.color.sk_surface_raised, SkArea.SURFACES, "Raised surface — bubbles, avatars, containers") { c -> SkTheme.blend(bg(c), tx(c), 0.08f) },
        SkRole("sk_ink", R.color.sk_ink, SkArea.TEXT, "Primary ink") { c -> tx(c) },
        SkRole("sk_ink_dim", R.color.sk_ink_dim, SkArea.TEXT, "Dim ink — secondary labels and icons") { c -> SkTheme.withAlpha(r(c, "sk_ink"), 0.7f) },
        SkRole("sk_ink_faint", R.color.sk_ink_faint, SkArea.TEXT, "Faint ink — hints, handles") { c -> SkTheme.withAlpha(r(c, "sk_ink"), 0.5f) },
        SkRole("sk_ink_ghost", R.color.sk_ink_ghost, SkArea.TEXT, "Ghost ink — outlines, separators") { c -> SkTheme.withAlpha(r(c, "sk_ink"), 0.3f) },
        SkRole("sk_bar_background", R.color.sk_bar_background, SkArea.BARS, "Status bar and top bar ground") { c -> SkTheme.color(c, SkSlot.TOOLBAR_BACKGROUND) },
        SkRole("sk_viewer_bar", R.color.sk_viewer_bar, SkArea.BARS, "Top bar of the file viewers and the recording player") { c -> r(c, "sk_bar_background") },
        // Paper is white: a PDF page is drawn with dark ink and no ground of its own.
        SkRole("sk_pdf_paper", R.color.sk_pdf_paper, SkArea.SURFACES, "Paper behind a PDF page") { _ -> 0xFFFFFFFF.toInt() },
        SkRole("sk_field_border", R.color.sk_field_border, SkArea.FIELDS, "Frame of a text field") { c -> ac(c) },
        SkRole("sk_field_border_dim", R.color.sk_field_border_dim, SkArea.FIELDS, "Frame of a text field that is not in use") { c -> SkTheme.withAlpha(r(c, "sk_field_border"), 0.4f) },
        SkRole("sk_danger", R.color.sk_danger, SkArea.STATUS, "Danger — errors, decline, delete") { c -> 0xFFFF5252.toInt() },
        SkRole("sk_danger_dim", R.color.sk_danger_dim, SkArea.STATUS, "Danger ground") { c -> 0xFF7F1D1D.toInt() },
        SkRole("sk_success", R.color.sk_success, SkArea.STATUS, "Success — registered, answered") { c -> 0xFF00C853.toInt() },
        SkRole("sk_success_dim", R.color.sk_success_dim, SkArea.STATUS, "Success ground") { c -> 0xFF14532D.toInt() },
        SkRole("sk_info", R.color.sk_info, SkArea.STATUS, "Information") { c -> 0xFF40C4FF.toInt() },
        SkRole("sk_warning", R.color.sk_warning, SkArea.STATUS, "Warning") { c -> 0xFFFFC400.toInt() },
        SkRole("sk_warning_dim", R.color.sk_warning_dim, SkArea.STATUS, "Warning ground") { c -> 0xFF4A3B00.toInt() },
        SkRole("sk_offline", R.color.sk_offline, SkArea.STATUS, "Offline") { c -> 0xFF9E9E9E.toInt() },
        SkRole("sk_blocked_pill", R.color.sk_blocked_pill, SkArea.MARKS, "Blocked mark — ground") { c -> 0xFFFF0000.toInt() },
        SkRole("sk_blocked_pill_text", R.color.sk_blocked_pill_text, SkArea.MARKS, "Blocked mark — text") { c -> 0xFFFFFFFF.toInt() },
        SkRole("sk_online_id_pill", R.color.sk_online_id_pill, SkArea.MARKS, "ID mark — ground") { c -> ac(c) },
        SkRole("sk_online_id_pill_text", R.color.sk_online_id_pill_text, SkArea.MARKS, "ID mark — text") { c -> bg(c) },
        SkRole("sk_badge_rin_text", R.color.sk_badge_rin_text, SkArea.MARKS, "臨 badge — text on the call screens") { c -> 0xFF0000FF.toInt() },
        SkRole("sk_recorded", R.color.sk_recorded, SkArea.MARKS, "Recording mark") { c -> 0xFFFF3030.toInt() },
        SkRole("sk_menu_divider", R.color.sk_menu_divider, SkArea.MENUS, "Line between two groups of a menu") { c -> SkTheme.blend(bg(c), ac(c), 0.7f) },
        SkRole("sk_a_main1_100", R.color.sk_a_main1_100, SkArea.ACCENT, "Accent wash") { c -> r(c, "sk_yellow_wash") },
        SkRole("sk_a_main1_500", R.color.sk_a_main1_500, SkArea.ACCENT, "Accent — icons, links, active states") { c -> r(c, "sk_yellow") },
        SkRole("sk_a_main2_000", R.color.sk_a_main2_000, SkArea.SURFACES, "Page ground") { c -> r(c, "sk_surface") },
        SkRole("sk_a_main2_100", R.color.sk_a_main2_100, SkArea.SURFACES, "Card and field ground") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_main2_200", R.color.sk_a_main2_200, SkArea.SURFACES, "Raised ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_main2_300", R.color.sk_a_main2_300, SkArea.TEXT, "Outlines") { c -> r(c, "sk_ink_ghost") },
        SkRole("sk_a_main2_400", R.color.sk_a_main2_400, SkArea.TEXT, "Faint labels") { c -> r(c, "sk_ink_faint") },
        SkRole("sk_a_main2_500", R.color.sk_a_main2_500, SkArea.TEXT, "Icons and labels") { c -> r(c, "sk_ink_dim") },
        SkRole("sk_a_main2_600", R.color.sk_a_main2_600, SkArea.TEXT, "Secondary labels and icons") { c -> r(c, "sk_ink_dim") },
        SkRole("sk_a_main2_700", R.color.sk_a_main2_700, SkArea.TEXT, "Strong labels") { c -> r(c, "sk_ink") },
        SkRole("sk_a_main2_800", R.color.sk_a_main2_800, SkArea.TEXT, "Stronger labels") { c -> r(c, "sk_ink") },
        SkRole("sk_a_main2_900", R.color.sk_a_main2_900, SkArea.TEXT, "Strongest labels") { c -> r(c, "sk_ink") },
        SkRole("sk_a_grey_100", R.color.sk_a_grey_100, SkArea.SURFACES, "Neutral ground — menus, cells") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_grey_200", R.color.sk_a_grey_200, SkArea.SURFACES, "Neutral raised ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_grey_300", R.color.sk_a_grey_300, SkArea.TEXT, "Neutral outlines") { c -> r(c, "sk_ink_ghost") },
        SkRole("sk_a_grey_400", R.color.sk_a_grey_400, SkArea.TEXT, "Neutral faint labels") { c -> r(c, "sk_ink_faint") },
        SkRole("sk_a_grey_500", R.color.sk_a_grey_500, SkArea.TEXT, "Neutral labels") { c -> r(c, "sk_ink_dim") },
        SkRole("sk_a_grey_600", R.color.sk_a_grey_600, SkArea.TEXT, "Neutral secondary labels") { c -> r(c, "sk_ink_dim") },
        SkRole("sk_a_grey_800", R.color.sk_a_grey_800, SkArea.TEXT, "Neutral strong labels") { c -> r(c, "sk_ink") },
        SkRole("sk_a_grey_900", R.color.sk_a_grey_900, SkArea.TEXT, "Neutral strongest labels") { c -> r(c, "sk_ink") },
        SkRole("sk_a_danger_100", R.color.sk_a_danger_100, SkArea.STATUS, "Danger — ground") { c -> r(c, "sk_danger_dim") },
        SkRole("sk_a_danger_500", R.color.sk_a_danger_500, SkArea.STATUS, "Danger — text and icons") { c -> r(c, "sk_danger") },
        SkRole("sk_a_danger_600", R.color.sk_a_danger_600, SkArea.STATUS, "Danger — pressed") { c -> r(c, "sk_danger") },
        SkRole("sk_a_danger_700", R.color.sk_a_danger_700, SkArea.STATUS, "Danger — frames") { c -> r(c, "sk_danger") },
        SkRole("sk_a_danger_900", R.color.sk_a_danger_900, SkArea.STATUS, "Danger — dark ground") { c -> r(c, "sk_danger_dim") },
        SkRole("sk_a_success_100", R.color.sk_a_success_100, SkArea.STATUS, "Success — ground") { c -> r(c, "sk_success_dim") },
        SkRole("sk_a_success_200", R.color.sk_a_success_200, SkArea.STATUS, "Success — light ground") { c -> r(c, "sk_success_dim") },
        SkRole("sk_a_success_500", R.color.sk_a_success_500, SkArea.STATUS, "Success — text and icons") { c -> r(c, "sk_success") },
        SkRole("sk_a_success_700", R.color.sk_a_success_700, SkArea.STATUS, "Success — pressed") { c -> r(c, "sk_success") },
        SkRole("sk_a_success_900", R.color.sk_a_success_900, SkArea.STATUS, "Success — dark ground") { c -> r(c, "sk_success_dim") },
        SkRole("sk_a_info_500", R.color.sk_a_info_500, SkArea.STATUS, "Information — text and icons") { c -> r(c, "sk_info") },
        SkRole("sk_a_warning_100", R.color.sk_a_warning_100, SkArea.STATUS, "Warning — ground") { c -> r(c, "sk_warning_dim") },
        SkRole("sk_a_warning_600", R.color.sk_a_warning_600, SkArea.STATUS, "Warning — text and icons") { c -> r(c, "sk_warning") },
        SkRole("sk_a_warning_900", R.color.sk_a_warning_900, SkArea.STATUS, "Warning — dark ground") { c -> r(c, "sk_warning_dim") },
        SkRole("sk_a_plain_text_security_warning_600", R.color.sk_a_plain_text_security_warning_600, SkArea.STATUS, "Unencrypted-call warning") { c -> r(c, "sk_warning") },
        SkRole("sk_a_offline_100", R.color.sk_a_offline_100, SkArea.STATUS, "Offline — ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_offline_900", R.color.sk_a_offline_900, SkArea.STATUS, "Offline — text") { c -> r(c, "sk_offline") },
        SkRole("sk_a_on_main", R.color.sk_a_on_main, SkArea.SURFACES, "Content drawn on the accent") { c -> r(c, "sk_black") },
        SkRole("sk_a_background_contrast_in_dark_mode", R.color.sk_a_background_contrast_in_dark_mode, SkArea.SURFACES, "Ground behind lists and pages") { c -> r(c, "sk_surface") },
        SkRole("sk_a_separator", R.color.sk_a_separator, SkArea.LISTS, "Separator line") { c -> r(c, "sk_ink_ghost") },
        SkRole("sk_a_text", R.color.sk_a_text, SkArea.TEXT, "Body text") { c -> r(c, "sk_ink") },
        SkRole("sk_a_hint_text", R.color.sk_a_hint_text, SkArea.FIELDS, "Hint text") { c -> r(c, "sk_ink_faint") },
        SkRole("sk_a_text_field_background", R.color.sk_a_text_field_background, SkArea.FIELDS, "Text field ground") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_avatar_background", R.color.sk_a_avatar_background, SkArea.AVATARS, "Avatar ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_avatar_text", R.color.sk_a_avatar_text, SkArea.AVATARS, "Avatar initials") { c -> r(c, "sk_ink") },
        SkRole("sk_a_chat_bubble_incoming", R.color.sk_a_chat_bubble_incoming, SkArea.BUBBLES, "Incoming bubble") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_chat_bubble_outgoing", R.color.sk_a_chat_bubble_outgoing, SkArea.BUBBLES, "Outgoing bubble") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_chat_bubble_reply", R.color.sk_a_chat_bubble_reply, SkArea.BUBBLES, "Reply quote ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_chat_bubble_file", R.color.sk_a_chat_bubble_file, SkArea.BUBBLES, "File bubble ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_message_text_field_background", R.color.sk_a_message_text_field_background, SkArea.BUBBLES, "Message field ground") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_conversation_send_area_background", R.color.sk_a_conversation_send_area_background, SkArea.BUBBLES, "Send area ground") { c -> r(c, "sk_surface") },
        SkRole("sk_a_button_background", R.color.sk_a_button_background, SkArea.BUTTONS, "Round button ground") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_button_pressed_background", R.color.sk_a_button_pressed_background, SkArea.BUTTONS, "Round button — pressed") { c -> r(c, "sk_yellow_wash") },
        SkRole("sk_a_button_disabled_background", R.color.sk_a_button_disabled_background, SkArea.BUTTONS, "Round button — disabled") { c -> r(c, "sk_disabled") },
        SkRole("sk_a_gradient_start", R.color.sk_a_gradient_start, SkArea.SURFACES, "Gradient — start") { c -> r(c, "sk_surface") },
        SkRole("sk_a_gradient_end", R.color.sk_a_gradient_end, SkArea.SURFACES, "Gradient — end") { c -> r(c, "sk_surface") },
        SkRole("sk_a_trust_background", R.color.sk_a_trust_background, SkArea.AVATARS, "Trust ring ground") { c -> r(c, "sk_surface_raised") },
        SkRole("sk_a_trust_track", R.color.sk_a_trust_track, SkArea.AVATARS, "Trust ring track") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_bottom_sheet_handle", R.color.sk_a_bottom_sheet_handle, SkArea.SHEETS, "Sheet handle") { c -> r(c, "sk_ink_faint") },
        SkRole("sk_a_bottom_sheet_background", R.color.sk_a_bottom_sheet_background, SkArea.SHEETS, "Sheet ground") { c -> r(c, "sk_surface_alt") },
        SkRole("sk_a_bottom_sheet_disabled_menu", R.color.sk_a_bottom_sheet_disabled_menu, SkArea.SHEETS, "Sheet — disabled row") { c -> r(c, "sk_ink_ghost") },
        SkRole("up_bc_white", R.color.bc_white, SkArea.IN_CALL, "Ink of the call screens and viewers") { c -> tx(c) },
        SkRole("up_bc_black", R.color.bc_black, SkArea.IN_CALL, "Ground of the viewers and the scanner") { c -> bg(c) },
        SkRole("up_gray_900", R.color.gray_900, SkArea.IN_CALL, "Ground of the call screens") { c -> bg(c) },
        SkRole("up_gray_600", R.color.gray_600, SkArea.IN_CALL, "Ground of call panels and sheets") { c -> r(c, "sk_surface_raised") },
        SkRole("up_gray_700", R.color.gray_700, SkArea.IN_CALL, "Frame of a disabled answer or hang-up button") { c -> r(c, "sk_disabled") },
        SkRole("up_gray_400", R.color.gray_400, SkArea.IN_CALL, "Dimmed call icon") { c -> r(c, "sk_ink_dim") },
        SkRole("up_gray_main2_600", R.color.gray_main2_600, SkArea.IN_CALL, "Secondary text of the call screens") { c -> r(c, "sk_ink_dim") },
        SkRole("up_gray_main2_500", R.color.gray_main2_500, SkArea.IN_CALL, "Labels of the call screens") { c -> r(c, "sk_ink_dim") },
        SkRole("up_gray_main2_400", R.color.gray_main2_400, SkArea.IN_CALL, "Faint labels of the call screens") { c -> r(c, "sk_ink_faint") },
        SkRole("up_gray_main2_300", R.color.gray_main2_300, SkArea.IN_CALL, "Pressed round control") { c -> r(c, "sk_yellow_wash") },
        SkRole("up_gray_main2_200", R.color.gray_main2_200, SkArea.IN_CALL, "Border round a call tile") { c -> r(c, "sk_ink_ghost") },
        SkRole("up_gray_300_alpha_40", R.color.gray_300_alpha_40, SkArea.MENUS, "Scrim behind the message menu") { c -> SkTheme.withAlpha(bg(c), 0.6f) },
        SkRole("up_dialog_background", R.color.dialog_background, SkArea.DIALOGS, "Scrim behind a dialog or a busy notice") { c -> SkTheme.withAlpha(bg(c), 0.6f) },
        SkRole("up_gradient_progress", R.color.gradient_progress, SkArea.BUBBLES, "Voice recording progress") { c -> SkTheme.withAlpha(tx(c), 0.5f) },
        SkRole("up_blue_info_500", R.color.blue_info_500, SkArea.STATUS, "Information — on the call screens") { c -> r(c, "sk_info") },
        SkRole("up_orange_warning_600", R.color.orange_warning_600, SkArea.STATUS, "Warning — on the call screens") { c -> r(c, "sk_warning") },
        SkRole("up_orange_warning_600_night", R.color.orange_warning_600_night, SkArea.STATUS, "Warning — encryption details") { c -> r(c, "sk_warning") },
        SkRole("up_red_danger_500", R.color.red_danger_500, SkArea.STATUS, "Danger — on the call screens") { c -> r(c, "sk_danger") },
        SkRole("up_red_danger_500_night", R.color.red_danger_500_night, SkArea.STATUS, "Danger — encryption details") { c -> r(c, "sk_danger") },
        SkRole("up_green_success_500", R.color.green_success_500, SkArea.STATUS, "Success — on the call screens") { c -> r(c, "sk_success") },
        SkRole("up_green_success_500_night", R.color.green_success_500_night, SkArea.STATUS, "Success — encryption details") { c -> r(c, "sk_success") },
        SkRole("pill_frame", 0, SkArea.BUTTONS, "Pill — frame") { c -> ac(c) },
        SkRole("pill_text", 0, SkArea.BUTTONS, "Pill — label") { c -> tx(c) },
        SkRole("call_control_icon", 0, SkArea.IN_CALL, "Call controls — icon") { c -> ac(c) },
        SkRole("call_control_label", 0, SkArea.IN_CALL, "Call controls — label") { c -> tx(c) },
        SkRole("call_account_label", 0, SkArea.IN_CALL, "Account named on the call screen") { c -> tx(c) },
        SkRole("dial_digit", 0, SkArea.DIAL, "Dial pad — digit") { c -> ac(c) },
        SkRole("dial_letters", 0, SkArea.DIAL, "Dial pad — letters") { c -> SkTheme.withAlpha(tx(c), 0.7f) },
        SkRole("dial_field_text", 0, SkArea.DIAL, "Dial pad — number being typed") { c -> tx(c) },
        SkRole("dial_icon", 0, SkArea.DIAL, "Dial pad — icons") { c -> ac(c) },
        SkRole("tab_label", 0, SkArea.BARS, "Account tab — label") { c -> ac(c) },
        SkRole("dialog_ground", 0, SkArea.DIALOGS, "Dialog — ground") { c -> bg(c) },
        SkRole("dialog_frame", 0, SkArea.DIALOGS, "Dialog — frame") { c -> ac(c) },
        SkRole("dialog_title", 0, SkArea.DIALOGS, "Dialog — title") { c -> ac(c) },
        SkRole("dialog_text", 0, SkArea.DIALOGS, "Dialog — text") { c -> tx(c) },
        SkRole("dialog_note", 0, SkArea.DIALOGS, "Dialog — notes and hints") { c -> SkTheme.color(c, SkSlot.TEXT_SECONDARY) },
        SkRole("dialog_button_frame", 0, SkArea.DIALOGS, "Dialog — button frame") { c -> ac(c) },
        SkRole("dialog_button_text", 0, SkArea.DIALOGS, "Dialog — button label") { c -> ac(c) },
        SkRole("dialog_field_frame", 0, SkArea.DIALOGS, "Dialog — text field frame") { c -> ac(c) },
        SkRole("dialog_heading", 0, SkArea.DIALOGS, "Dialog — group heading and label") { c -> r(c, "dialog_title") },
        SkRole("dialog_divider", 0, SkArea.DIALOGS, "Dialog — line between groups") { c -> SkTheme.blend(r(c, "dialog_ground"), r(c, "dialog_frame"), 0.70f) },
        SkRole("dialog_caption", 0, SkArea.DIALOGS, "Dialog — caption of a fact") { c -> SkTheme.blend(r(c, "dialog_ground"), r(c, "dialog_frame"), 0.78f) },
        SkRole("dialog_slider", 0, SkArea.DIALOGS, "Dialog — slider") { c -> r(c, "dialog_frame") },
        SkRole("dialog_fact_icon", 0, SkArea.DIALOGS, "Dialog — icon of a plain fact") { c -> SkTheme.color(c, SkSlot.MENU_VIEW) },
        SkRole("switch_on", 0, SkArea.SWITCHES, "Switch that is on — outline and dot") { c -> ac(c) },
        SkRole("mark_on", 0, SkArea.SWITCHES, "Radio button or check box — chosen") { c -> ac(c) },
        SkRole("mark_off", 0, SkArea.SWITCHES, "Radio button or check box — not chosen") { c -> SkTheme.color(c, SkSlot.SWITCH_OFF) },
        SkRole("menu_frame", 0, SkArea.MENUS, "Menu row — frame") { c -> ac(c) },
        SkRole("menu_label", 0, SkArea.MENUS, "Menu row — label") { c -> ac(c) },
        SkRole("menu_caret", 0, SkArea.MENUS, "Menu row — › and check") { c -> ac(c) },
        SkRole("page_heading", 0, SkArea.PAGE, "Section heading") { c -> ac(c) },
        SkRole("page_heading_rule", 0, SkArea.PAGE, "Rule under a heading") { c -> ac(c) },
        SkRole("page_divider", 0, SkArea.PAGE, "Line between two sections") { c -> SkTheme.blend(bg(c), ac(c), 0.7f) },
        SkRole("page_chip_frame", 0, SkArea.PAGE, "Index chip — frame") { c -> ac(c) },
        SkRole("page_chip_text", 0, SkArea.PAGE, "Index chip — label") { c -> ac(c) },
        SkRole("page_rule", 0, SkArea.PAGE, "Rule left of an explained row") { c -> SkTheme.color(c, SkSlot.TEXT_SECONDARY) },
        SkRole("page_slider", 0, SkArea.PAGE, "Slider") { c -> ac(c) },
        SkRole("page_swatch_frame", 0, SkArea.PAGE, "Frame of a colour swatch") { c -> ac(c) },
        SkRole("page_label", 0, SkArea.PAGE, "Row label") { c -> tx(c) },
        SkRole("page_note", 0, SkArea.PAGE, "Row value and explanation") { c -> SkTheme.color(c, SkSlot.TEXT_SECONDARY) },
        SkRole("bubble_frame", 0, SkArea.BUBBLES, "Bubble — frame") { c -> ac(c) },
        SkRole("avatar_frame", 0, SkArea.AVATARS, "Avatar — frame") { c -> ac(c) },
        SkRole("tab_frame", 0, SkArea.BARS, "Account tab — outline") { c -> ac(c) },
        SkRole("menu_ground", 0, SkArea.MENUS, "Menu — ground") { c -> r(c, "sk_surface") },
        SkRole("menu_box_frame", 0, SkArea.MENUS, "Menu — frame round the whole menu") { c -> ac(c) },
        SkRole("settings_group_ground", 0, SkArea.SCREEN_SETTINGS, "Settings group — ground") { c -> r(c, "sk_surface") },
        SkRole("settings_group_frame", 0, SkArea.SCREEN_SETTINGS, "Settings group — frame") { c -> ac(c) },
        SkRole("settings_header_ground", 0, SkArea.SCREEN_SETTINGS, "Settings group — heading ground") { c -> r(c, "sk_surface_alt") },
        SkRole("search_frame", 0, SkArea.BARS, "Search pill — frame") { c -> ac(c) },
        SkRole("status_pill_ground", 0, SkArea.BARS, "Account status pill — ground") { c -> r(c, "sk_surface") },
        SkRole("status_pill_frame", 0, SkArea.BARS, "Account status pill — frame") { c -> r(c, "sk_field_border") },
        SkRole("dial_key_ground", 0, SkArea.DIAL, "Dial pad — key ground") { c -> r(c, "sk_surface") },
        SkRole("dial_key_frame", 0, SkArea.DIAL, "Dial pad — key frame") { c -> ac(c) },
        SkRole("dial_key_wash", 0, SkArea.DIAL, "Dial pad — pressed key") { c -> r(c, "sk_yellow_wash") },
        SkRole("dial_underline", 0, SkArea.DIAL, "Dial pad — line under the number") { c -> ac(c) },
        SkRole("dial_panel_edge", 0, SkArea.DIAL, "Dial pad — top edge of the panel") { c -> ac(c) },
        SkRole("dial_panel_ground", 0, SkArea.DIAL, "Dial pad — panel ground") { c -> r(c, "sk_surface") },
        SkRole("call_key_ground", 0, SkArea.IN_CALL, "Round call button — ground") { c -> r(c, "sk_surface") },
        SkRole("call_key_ring", 0, SkArea.IN_CALL, "Round call button — ring") { c -> ac(c) },
        SkRole("call_key_wash", 0, SkArea.IN_CALL, "Round call button — pressed") { c -> r(c, "sk_yellow_wash") },
        SkRole("call_main_ring", 0, SkArea.IN_CALL, "Answer and Call buttons — ring") { c -> ac(c) },
        SkRole("call_main_wash", 0, SkArea.IN_CALL, "Answer and Call buttons — pressed") { c -> r(c, "sk_yellow_wash_strong") },
        SkRole("danger_pill_frame", 0, SkArea.DIALOGS, "Deleting pill — frame and label") { c -> r(c, "sk_danger") },
        SkRole("danger_pill_wash", 0, SkArea.DIALOGS, "Deleting pill — pressed") { c -> r(c, "sk_yellow_wash") },
        SkRole("danger_pill_disabled", 0, SkArea.DIALOGS, "Deleting pill — disabled frame") { c -> r(c, "sk_danger_dim") },
        SkRole("flash_ground", 0, SkArea.FLASH, "Flash — ground") { c -> bg(c) },
        SkRole("flash_text", 0, SkArea.FLASH, "Flash — text") { c -> tx(c) },
        SkRole("flash_frame", 0, SkArea.FLASH, "Flash — frame") { c -> ac(c) },
        SkRole("flash_done", 0, SkArea.FLASH, "Flash — done icon") { c -> r(c, "sk_success") },
        SkRole("flash_note", 0, SkArea.FLASH, "Flash — note icon") { c -> r(c, "sk_info") },
        SkRole("flash_wrong", 0, SkArea.FLASH, "Flash — wrong icon") { c -> r(c, "sk_danger") },
        SkRole("rin_badge_ground", 0, SkArea.MARKS, "臨 badge — default ground") { c -> ac(c) },
        SkRole("rin_badge_text", 0, SkArea.MARKS, "臨 badge — default text") { c -> 0xFF0000FF.toInt() },
    )

    /** Resources that follow a role or a slot without being a role of their own. */
    private val DERIVED: List<Pair<Int, (Context) -> Int>> = listOf(
        R.color.sk_role_message to { c: Context -> SkTheme.color(c, SkSlot.MENU_MESSAGE) },
        R.color.sk_role_call to { c: Context -> SkTheme.color(c, SkSlot.MENU_CALL) },
        R.color.sk_role_contact to { c: Context -> SkTheme.color(c, SkSlot.MENU_CONTACT) },
        R.color.sk_role_share to { c: Context -> SkTheme.color(c, SkSlot.MENU_SHARE) },
        R.color.sk_role_view to { c: Context -> SkTheme.color(c, SkSlot.MENU_VIEW) },
        R.color.sk_role_block to { c: Context -> SkTheme.color(c, SkSlot.MENU_BLOCK) },
        R.color.sk_role_delete to { c: Context -> SkTheme.color(c, SkSlot.MENU_DELETE) },
        R.color.sk_dim_gold to { c: Context -> SkTheme.color(c, SkSlot.TEXT_SECONDARY) },
        R.color.sk_switch_on to { c: Context -> r(c, "switch_on") },
        R.color.sk_switch_off to { c: Context -> SkTheme.color(c, SkSlot.SWITCH_OFF) },
    )

    val ALL: List<SkRole> by lazy { HAND + SkRolesGenerated.ALL }

    private val BY_KEY: Map<String, SkRole> by lazy { ALL.associateBy { it.key } }

    private val BY_AREA: Map<SkArea, List<SkRole>> by lazy { ALL.groupBy { it.area } }

    fun byKey(key: String): SkRole? = BY_KEY[key]

    fun inArea(area: SkArea): List<SkRole> = BY_AREA[area].orEmpty()

    fun color(context: Context, role: SkRole): Int {
        val stored = SkTheme.prefs(context).getInt(PREFIX + role.key, SK_UNSET)
        return if (stored != SK_UNSET) stored else role.default(context)
    }

    /** By key: a role of this table, or — so one name space serves the layouts — an [SkSlot]. */
    fun color(context: Context, key: String): Int {
        BY_KEY[key]?.let { return color(context, it) }
        SkSlot.entries.firstOrNull { it.key == key }?.let { return SkTheme.color(context, it) }
        return SkTheme.color(context, SkSlot.TEXT)
    }

    fun hasOverride(context: Context, role: SkRole): Boolean = SkTheme.prefs(context).contains(PREFIX + role.key)

    fun set(context: Context, role: SkRole, color: Int) {
        SkTheme.prefs(context).edit().putInt(PREFIX + role.key, color).apply()
        SkPalette.changed()
    }

    fun clear(context: Context, role: SkRole) {
        SkTheme.prefs(context).edit().remove(PREFIX + role.key).apply()
        SkPalette.changed()
    }

    /** Every stored colour — of these roles and of the [SkSlot]s — goes back to its default. */
    fun resetAll(context: Context) {
        val editor = SkTheme.prefs(context).edit()
        ALL.forEach { editor.remove(PREFIX + it.key) }
        SkSlot.entries.forEach { editor.remove(it.key) }
        editor.apply()
        SkPalette.changed()
    }

    /** What the loader lays over the resources: every role resource and its colour of the moment. */
    fun resourceColors(context: Context): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        for (role in ALL) if (role.resId != 0) map[role.resId] = color(context, role)
        for ((resId, value) in DERIVED) map[resId] = value(context)
        return map
    }
}
