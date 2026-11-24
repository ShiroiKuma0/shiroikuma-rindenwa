package org.linphone.shiroikuma

import android.view.ViewGroup
import org.linphone.utils.ImageUtils
import android.widget.ImageView
import android.graphics.Bitmap
import org.linphone.LinphoneApplication.Companion.coreContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.ZoneId
import org.linphone.R
import org.linphone.databinding.ActivitySkUiBinding
import org.linphone.databinding.ItemSkActionBinding
import org.linphone.databinding.ItemSkCallPreviewBinding
import org.linphone.databinding.ItemSkChoiceBinding
import org.linphone.databinding.ItemSkColorBinding
import org.linphone.databinding.ItemSkDimenBinding
import org.linphone.databinding.ItemSkNoteBinding
import org.linphone.databinding.ItemSkPreviewBoxBinding
import org.linphone.databinding.ItemSkSectionBinding
import org.linphone.databinding.ItemSkSubgroupBinding
import org.linphone.databinding.ItemSkSwitchBinding
import org.linphone.databinding.ItemSkTextBinding
import org.linphone.databinding.ItemSkTokenBinding

/**
 * shiroikuma-rindenwa fork — the 白い熊 臨電話 UI page.
 *
 * Programmatically built in the kxkb page format: big bold text-wide underlined section headings
 * separated by 1px hairlines, deeply indented items (each sub-level a further full step), tight
 * rows, and a live preview for everything — colors (RGBA slider picker with recent-color boxes),
 * external fonts rendered in their own glyphs, weight/size sliders, and 0-capable border/roundness
 * sliders.
 *
 * Every row that *does* something when it is tapped — opens a panel, a picker, a system settings
 * screen — is drawn through [addActionRow] as an outlined pill no wider than its own label, so it
 * can never be read as a heading nor as a banner; rows that carry their own control (switch,
 * swatch, slider, a picked value) stay plain, the control being affordance enough.
 *
 * The first section is Export/Import (Kōjiki flow), with the 保存復元 automation rows inside it.
 */
class SkUiActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySkUiBinding
    private var pendingFontSlot: SkSlot? = null
    private var listPreview: TextView? = null
    private var bubblePreview: TextView? = null
    private var buttonPreview: TextView? = null
    private var callPreview: ItemSkCallPreviewBinding? = null
    private var eximPanel: SkEximportPanel? = null

    /**
     * One top-level section: its stable key, its heading, the icon heading and chip both carry,
     * the colour role of that icon ([tone]: one of the seven section colours, by what kind of
     * thing the section is) and what fills it.
     */
    private class Section(
        val key: String,
        val labelRes: Int,
        val icon: Int,
        val tone: String,
        val build: () -> Unit,
    )

    /** Where the row builders put their rows: the body of the section being built. */
    private lateinit var body: LinearLayout
    private val sectionWrappers = HashMap<String, View>()
    private val sectionBodies = HashMap<String, LinearLayout>()
    private val sectionChevrons = HashMap<String, TextView>()
    private val builtSections = HashSet<String>()

    /** See [ruled]. */
    private class LeftRule(
        private val x: Float,
        private val width: Float,
        private val inset: Float,
        color: Int,
    ) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint().apply { this.color = color }

        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            canvas.drawRect(x, b.top + inset, x + width, b.bottom - inset, paint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(filter: android.graphics.ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private val indentStepPx: Int
        get() = (INDENT_STEP_DP * resources.displayMetrics.density).toInt()

    private val eximDirPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Persist across reboots, then remember it as the export directory.
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (ignored: SecurityException) {
                }
                SkEximport.setDirUri(this, uri)
            }
            eximPanel?.onDirPicked()
        }

    private val eximExportTarget =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            eximPanel?.onExportTarget(uri)
        }

    private val eximImportSource =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            eximPanel?.onImportSource(uri)
        }

    /**
     * Diagnostics export: a directory picked every time (never remembered), offered at
     * "/sdcard/tmp" — where every other hand-over to 白い熊 lands.
     */
    private val diagExportPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                exportDiagnostics(uri)
            }
        }

    private val openAllTabPicture =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            if (SkAllAccounts.importPicture(this, uri)) {
                refreshPage()
            } else {
                SkFlash.show(this, R.string.sk_all_tab_picture_invalid)
            }
        }

    private val openFontDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val slot = pendingFontSlot
            pendingFontSlot = null
            if (uri == null || slot == null) return@registerForActivityResult
            val imported = SkFonts.importFont(this, uri)
            if (imported == null) {
                SkFlash.show(this, R.string.sk_font_invalid)
            } else {
                SkTheme.setFontFamily(this, slot, imported)
                refreshPage()
            }
        }

    /** Same black-yellow overlay the rest of the app gets, so our own chrome matches it. */
    override fun getTheme(): android.content.res.Resources.Theme {
        SkPalette.apply(this)
        val theme = super.getTheme()
        theme.applyStyle(R.style.Theme_SkBlackYellow, true)
        return theme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SkShapes.install(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySkUiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setTitle(R.string.sk_ui_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        SkPalette.paintWindow(this)
    }

    override fun onPause() {
        // Retention runs on leaving the page and at start — never while something is recording.
        runCatching { SkDiag.cleanUp(this) }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        SkStyler.apply(this)
        buildRows()
    }

    private fun refreshPage() {
        // A colour may just have been set: this page's own resources take it at once.
        SkPalette.apply(this)
        SkPalette.paintWindow(this)
        // A slot may have just gained (or lost) an override — tell the runtime pass before it runs.
        SkStyler.refreshOverrideState(this)
        SkStyler.apply(this)
        buildRows()
    }

    // ------------------------------------------------------------------ rows

    private fun buildRows() {
        binding.skHolder.removeAllViews()
        binding.skHolder.setBackgroundColor(SkTheme.color(this, SkSlot.BACKGROUND))
        listPreview = null
        bubblePreview = null
        buttonPreview = null
        callPreview = null
        sectionWrappers.clear()
        sectionBodies.clear()
        sectionChevrons.clear()
        builtSections.clear()
        body = binding.skHolder

        // The one thing pinned above the sections: the way to the app's own settings.
        addActionRow(
            R.string.sk_app_settings,
            0,
            main = true,
            opens = true,
            level = 0,
            icon = R.drawable.gear,
        ) { openAppSettings() }

        val sections = sections()
        val order = SkPageState.order(this, sections.map { it.key })
        addSectionIndex(order.map { key -> sections.first { it.key == key } })
        for (key in order) {
            addSection(sections.first { it.key == key })
        }
        body = binding.skHolder
    }

    /**
     * The top-level sections in their built order — the order a fresh install shows, and the one
     * "Reset section order" goes back to. The keys are what [SkPageState] stores, so they are
     * never renamed; a section added later appears at its built place in a stored order.
     *
     * Each section names its icon and its tone — one of the seven section colours, chosen by
     * what kind of thing the section is (black-yellow-theme skill, “Section icons”): the base
     * (what the app is, and its settings), controls, words, the frame and its boxes, where the app
     * reaches out, warning, and — once — a brief flash. **In this order no two neighbours share a
     * tone**; keep it so when adding a section. An order 白い熊 dragged is never touched.
     */
    private fun sections(): List<Section> = listOf(
        Section("eximport", R.string.sk_section_eximport, R.drawable.share_network, TONE_REACH) { buildEximport() },
        Section("accounts", R.string.sk_section_accounts_apps, R.drawable.user_circle_gear, TONE_BASE) {
            buildAccountsAndApps()
        },
        Section("top_bar", SkSection.TOP_BAR.labelRes, R.drawable.layout, TONE_FRAME) { buildTopBar() },
        Section("foundation", SkSection.FOUNDATION.labelRes, R.drawable.sk_ic_drop, TONE_BASE) { buildFoundation() },
        Section("text", R.string.sk_section_text, R.drawable.sk_ic_text, TONE_WORDS) { buildText() },
        Section("main", SkSection.MAIN_SCREEN.labelRes, R.drawable.squares_four, TONE_BASE) { buildMainScreen() },
        Section("surfaces", R.string.sk_section_surfaces, R.drawable.image, TONE_FRAME) { buildSurfaces() },
        Section("calls", SkSection.CALLS.labelRes, R.drawable.phone_call, TONE_BASE) { buildCalls() },
        Section("buttons", R.string.sk_section_buttons, R.drawable.power, TONE_CONTROLS) { buildButtons() },
        Section("conversations", SkSection.CONVERSATIONS.labelRes, R.drawable.chat_teardrop_text, TONE_BASE) {
            buildConversations()
        },
        Section("switches", R.string.sk_section_switches, R.drawable.check_square_offset, TONE_CONTROLS) { buildSwitches() },
        Section("menus", R.string.sk_section_menus, R.drawable.list, TONE_REACH) { buildMenus() },
        Section("fields", R.string.sk_section_fields, R.drawable.pencil_simple, TONE_WORDS) { buildFields() },
        Section("dialogs", R.string.sk_section_dialogs, R.drawable.chat_teardrop_dots, TONE_REACH) { buildDialogs() },
        Section("status", R.string.sk_section_status, R.drawable.info, TONE_FRAME) { buildStatus() },
        Section("marks", R.string.sk_section_marks, R.drawable.sk_star, TONE_CONTROLS) { buildMarks() },
        Section("sheets", R.string.sk_section_sheets, R.drawable.caret_up, TONE_REACH) { buildSheets() },
        Section("flashes", R.string.sk_section_flashes, R.drawable.bell_ringing, TONE_FLASH) { buildFlashes() },
        Section("page", R.string.sk_section_page, R.drawable.sk_ic_sliders, TONE_BASE) { buildPage() },
        Section("screen_main", R.string.sk_section_screen_main, R.drawable.monitor, TONE_FRAME) {
            addRoleRows(SkArea.SCREEN_MAIN, 1)
        },
        Section("screen_start", R.string.sk_section_screen_start, R.drawable.phone_plus, TONE_BASE) {
            addRoleRows(SkArea.SCREEN_START, 1)
        },
        Section("screen_history", R.string.sk_section_screen_history, R.drawable.clock, TONE_FRAME) {
            addRoleRows(SkArea.SCREEN_HISTORY, 1)
        },
        Section("screen_settings", R.string.sk_section_screen_settings, R.drawable.gear, TONE_BASE) {
            addRoleRows(SkArea.SCREEN_SETTINGS, 1)
        },
        Section("screen_contacts", R.string.sk_section_screen_contacts, R.drawable.address_book, TONE_FRAME) {
            addRoleRows(SkArea.SCREEN_CONTACTS, 1)
        },
        Section("screen_account", R.string.sk_section_screen_account, R.drawable.user_circle, TONE_BASE) {
            addRoleRows(SkArea.SCREEN_ACCOUNT, 1)
        },
        Section("screen_drawer", R.string.sk_section_screen_drawer, R.drawable.dots_three_vertical, TONE_FRAME) {
            addRoleRows(SkArea.SCREEN_DRAWER, 1)
        },
        Section("screen_help", R.string.sk_section_screen_help, R.drawable.question, TONE_WORDS) {
            addRoleRows(SkArea.SCREEN_HELP, 1)
        },
        Section("screen_recordings", R.string.sk_section_screen_recordings, R.drawable.record_fill, TONE_FRAME) {
            addRoleRows(SkArea.SCREEN_RECORDINGS, 1)
        },
        Section("screen_other", R.string.sk_section_screen_other, R.drawable.resource_package, TONE_BASE) {
            addRoleRows(SkArea.SCREEN_OTHER, 1)
        },
        Section("screen_viewers", R.string.sk_section_screen_viewers, R.drawable.file, TONE_FRAME) {
            addRoleRows(SkArea.SCREEN_VIEWERS, 1)
        },
        Section("styles", R.string.sk_section_styles, R.drawable.book_open_text, TONE_WORDS) { addRoleRows(SkArea.STYLES, 1) },
        Section("shapes", R.string.sk_section_shapes, R.drawable.picture_in_picture, TONE_FRAME) {
            addRoleRows(SkArea.SHAPES, 1)
        },
        Section("states", R.string.sk_section_states, R.drawable.checks, TONE_CONTROLS) { addRoleRows(SkArea.STATES, 1) },
        Section("assistant", R.string.sk_section_assistant, R.drawable.user_plus, TONE_BASE) { buildAssistant() },
        Section("diag", R.string.sk_section_diag, R.drawable.waveform, TONE_WARNING) { buildDiagnostics() },
        Section("meetings", R.string.sk_section_meetings, R.drawable.calendar, TONE_BASE) { buildMeetings() },
        Section("maintenance", R.string.sk_section_maintenance, R.drawable.wrench, TONE_WARNING) { buildMaintenance() },
    )

    private fun buildEximport() {
        // ---- Export / Import — always first, per the Kōjiki page ----
        addEximportRow(1)
        // The 保存復元 automation lives where backup lives — inside this section, below its rows.
        addAutomationSwitchRow(1)
        addAutomationRequireTokenRow(1)
        // Hidden while the token is not being asked for: a 48-character secret sitting under an
        // off switch invites pasting it somewhere it will do nothing.
        if (SkAutomation.requireToken(this)) {
            addAutomationTokenRow(1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addAllFilesAccessRow(1)
        }
    }

    private fun buildAccountsAndApps() {
        // ---- 臨0, 臨1, 臨2 …: the number each SIP account is known by (SkAccountNumbers) ----
        addSubgroup(R.string.sk_group_rin_numbers, 1)
        addRinNumberRows(1)

        // ---- The call door: which sister apps may dial through this one (SkCallsProvider) ----
        addSubgroup(R.string.sk_group_call_door, 1)
        addCallDoorRow(1)
    }

    private fun buildFoundation() {
        addColorRow(SkSlot.BACKGROUND, 1)
        addTextSlot(SkSlot.TEXT, 1)
        addTextSlot(SkSlot.TEXT_SECONDARY, 1)
        addColorRow(SkSlot.ACCENT, 1)
        addRoleRows(SkArea.ACCENT, 1)
    }

    private fun buildTopBar() {
        addColorRow(SkSlot.TOOLBAR_BACKGROUND, 1)
        addTextSlot(SkSlot.TOOLBAR_TITLE, 1)
        addColorRow(SkSlot.TOOLBAR_ICON, 1)

        // The 全 tab of the account strip: its character's font, or a picture in its place.
        addSubgroup(R.string.sk_group_all_tab, 1)
        addTextSlot(SkSlot.ALL_TAB, 2)
        addActionRow(
            R.string.sk_all_tab_picture,
            R.string.sk_all_tab_picture_desc,
            main = false,
            opens = true,
            level = 2,
            icon = R.drawable.image,
        ) { openAllTabPicture.launch(arrayOf("image/*")) }
        if (SkAllAccounts.hasPicture(this)) {
            addActionRow(
                R.string.sk_all_tab_picture_remove,
                R.string.sk_all_tab_picture_remove_desc,
                main = false,
                opens = false,
                level = 2,
                icon = R.drawable.trash,
                role = SkMenus.DELETE,
            ) {
                SkAllAccounts.removePicture(this)
                refreshPage()
            }
        }
        addRoleGroup(SkArea.BARS, R.string.sk_group_roles_bars)
    }

    private fun buildMainScreen() {
        addSubgroup(R.string.sk_group_list_rows, 1)
        addTextSlot(SkSlot.LIST_NAME, 2)
        addTextSlot(SkSlot.LIST_DETAIL, 2)
        addColorRow(SkSlot.LIST_BACKGROUND, 2)
        addColorRow(SkSlot.LIST_BORDER, 2)
        addDimenRow(SkDimen.LIST_BORDER_WIDTH, 2)
        addDimenRow(SkDimen.LIST_CORNER_RADIUS, 2)
        addListPreview(2)
        // The Contacts letter headings, in the sister address book's shape.
        addSubgroup(R.string.sk_group_contact_sections, 1)
        addSwitchRow(
            R.string.sk_contact_sections,
            R.string.sk_contact_sections_desc,
            SkContacts.sectionsEnabled(this),
            2,
            icon = R.drawable.list,
        ) {
            SkContacts.setSectionsEnabled(this, it)
        }
        addTextSlot(SkSlot.CONTACT_SECTION, 2)
        addColorRow(SkSlot.CONTACT_SECTION_UNDERLINE, 2)
        addDimenRow(SkDimen.CONTACT_SECTION_UNDERLINE_WIDTH, 2)
        addColorRow(SkSlot.CONTACT_SECTION_DIVIDER, 2)
        addDimenRow(SkDimen.CONTACT_SECTION_DIVIDER_WIDTH, 2)
        addDimenRow(SkDimen.CONTACT_SECTION_PADDING, 2)

        // The contact rows: the number line under the name, the avatar spanning both, the line
        // closing each row off.
        addSubgroup(R.string.sk_group_contact_rows, 1)
        addSwitchRow(
            R.string.sk_contact_initials,
            R.string.sk_contact_initials_desc,
            SkContacts.initialsEnabled(this),
            2,
            icon = R.drawable.user_circle,
            role = SkMenus.CONTACT,
        ) {
            SkContacts.setInitialsEnabled(this, it)
        }
        addTextSlot(SkSlot.CONTACT_NUMBER, 2)
        addDimenRow(SkDimen.CONTACT_AVATAR_SIZE, 2)
        addDimenRow(SkDimen.CONTACT_ROW_PADDING, 2)
        addColorRow(SkSlot.CONTACT_ROW_DIVIDER, 2)
        addDimenRow(SkDimen.CONTACT_ROW_DIVIDER_WIDTH, 2)

        // The Favorites tab: a tile grid, draggable, in the sister address book's shape.
        addSubgroup(R.string.sk_group_favourites, 1)
        addTextSlot(SkSlot.FAVOURITE_NAME, 2)
        addDimenRow(SkDimen.FAVOURITE_COLUMNS, 2)
        addDimenRow(SkDimen.FAVOURITE_AVATAR_SIZE, 2)
        addDimenRow(SkDimen.FAVOURITE_TILE_PADDING, 2)

        addSubgroup(R.string.sk_group_avatar, 1)
        addColorRow(SkSlot.AVATAR_BACKGROUND, 2)
        addTextSlot(SkSlot.AVATAR_TEXT, 2)
        addDimenRow(SkDimen.AVATAR_CORNER_RADIUS, 2)
        addSubgroup(R.string.sk_group_fab, 1)
        addColorRow(SkSlot.FAB_BACKGROUND, 2)
        addColorRow(SkSlot.FAB_ICON, 2)
        addRoleGroup(SkArea.LISTS, R.string.sk_group_roles_lists)
        addRoleGroup(SkArea.AVATARS, R.string.sk_group_roles_avatars)
    }

    private fun buildCalls() {
        // Without this grant an incoming call can only drop a banner when the screen is already on,
        // so it belongs at the top of the section rather than buried under the colours.
        addCallScreenOverlayRow(1)
        addDeclineMessagesRow(1)
        addBlockedNumbersRow(1)
        // Who an unknown Czech number is, asked of tellows.cz while it rings. The number leaves
        // the phone for that, which is why it has a switch. See SkOnlineId.
        addSwitchRow(
            R.string.sk_online_id_title,
            R.string.sk_online_id_desc,
            SkOnlineId.enabled(this),
            1,
            icon = R.drawable.magnifying_glass,
            role = SkMenus.CONTACT,
        ) {
            SkOnlineId.setEnabled(this, it)
        }
        addColorRow(SkSlot.CALL_BACKGROUND, 1)
        addTextSlot(SkSlot.CALL_NAME, 1)
        addTextSlot(SkSlot.CALL_STATUS, 1)

        // What a call-history record says: which day it is filed under, in which calendar, and
        // how its time and length are written. Every knob for the reading is in this one group.
        addSubgroup(R.string.sk_group_call_reading, 1)
        addSwitchRow(
            R.string.sk_call_day_headers,
            R.string.sk_call_day_headers_desc,
            SkCallLog.dayHeadersEnabled(this),
            2,
            icon = R.drawable.calendar,
        ) {
            SkCallLog.setDayHeadersEnabled(this, it)
        }
        addChoiceRow(
            R.string.sk_call_date_format,
            SkCallLog.DateFormatOption.entries.map { getString(it.labelRes) },
            SkCallLog.dateFormat(this).ordinal,
            2,
        ) { picked ->
            SkCallLog.setDateFormat(this, SkCallLog.DateFormatOption.entries[picked])
        }
        addSwitchRow(
            R.string.sk_call_relative_days,
            R.string.sk_call_relative_days_desc,
            SkCallLog.relativeDaysEnabled(this),
            2,
            icon = R.drawable.calendar_blank,
        ) {
            SkCallLog.setRelativeDaysEnabled(this, it)
        }
        addChoiceRow(
            R.string.sk_call_time_format,
            SkCallLog.TimeFormatOption.entries.map { getString(it.labelRes) },
            SkCallLog.timeFormat(this).ordinal,
            2,
        ) { picked ->
            SkCallLog.setTimeFormat(this, SkCallLog.TimeFormatOption.entries[picked])
        }
        addSwitchRow(
            R.string.sk_call_show_duration,
            R.string.sk_call_show_duration_desc,
            SkCallLog.durationShown(this),
            2,
            icon = R.drawable.clock,
        ) {
            SkCallLog.setDurationShown(this, it)
        }
        addChoiceRow(
            R.string.sk_call_duration_format,
            SkCallLog.DurationFormatOption.entries.map { getString(it.labelRes) },
            SkCallLog.durationFormat(this).ordinal,
            2,
        ) { picked ->
            SkCallLog.setDurationFormat(this, SkCallLog.DurationFormatOption.entries[picked])
        }
        addSwitchRow(
            R.string.sk_call_show_direction,
            R.string.sk_call_show_direction_desc,
            SkCallLog.directionShown(this),
            2,
            icon = R.drawable.incoming_call,
            role = SkMenus.CALL,
        ) {
            SkCallLog.setDirectionShown(this, it)
        }

        // How the day headline is drawn: its own text slot, the rule under it, the band above it.
        addSubgroup(R.string.sk_group_call_day, 1)
        addTextSlot(SkSlot.CALL_DAY, 2)
        addColorRow(SkSlot.CALL_DAY_UNDERLINE, 2)
        addDimenRow(SkDimen.CALL_DAY_UNDERLINE_WIDTH, 2)
        addColorRow(SkSlot.CALL_DAY_DIVIDER, 2)
        addDimenRow(SkDimen.CALL_DAY_DIVIDER_WIDTH, 2)

        // The records themselves: a font slot per line, the arrow colours, and the two spacings
        // that decide how tightly the list reads — between records, and within one record.
        addSubgroup(R.string.sk_group_call_history, 1)
        addTextSlot(SkSlot.CALL_ROW_NAME, 2)
        addTextSlot(SkSlot.LIST_NUMBER, 2)
        addTextSlot(SkSlot.CALL_TIME, 2)
        addTextSlot(SkSlot.CALL_DURATION, 2)
        addColorRow(SkSlot.CALL_DIR_INCOMING, 2)
        addColorRow(SkSlot.CALL_DIR_OUTGOING, 2)
        addColorRow(SkSlot.CALL_DIR_MISSED, 2)
        addDimenRow(SkDimen.CALL_AVATAR_SIZE, 2)
        addDimenRow(SkDimen.CALL_ROW_PADDING, 2)
        addDimenRow(SkDimen.CALL_LINE_SPACING, 2)
        addColorRow(SkSlot.CALL_ROW_DIVIDER, 2)
        addDimenRow(SkDimen.CALL_ROW_DIVIDER_WIDTH, 2)
        addCallRowPreview(2)

        addSubgroup(R.string.sk_group_call_buttons, 1)
        addColorRow(SkSlot.CALL_ANSWER, 2)
        addColorRow(SkSlot.CALL_HANGUP, 2)
        addRoleGroup(SkArea.IN_CALL, R.string.sk_group_roles_in_call)
        addRoleGroup(SkArea.DIAL, R.string.sk_group_roles_dial)
        addRoleGroup(SkArea.SCREEN_CALL, R.string.sk_group_roles_call_items)
    }

    private fun buildConversations() {
        addSubgroup(R.string.sk_group_bubble_outgoing, 1)
        addColorRow(SkSlot.BUBBLE_OUTGOING, 2)
        addTextSlot(SkSlot.BUBBLE_OUTGOING_TEXT, 2)
        addSubgroup(R.string.sk_group_bubble_incoming, 1)
        addColorRow(SkSlot.BUBBLE_INCOMING, 2)
        addTextSlot(SkSlot.BUBBLE_INCOMING_TEXT, 2)
        addSubgroup(R.string.sk_group_bubble_shape, 1)
        addDimenRow(SkDimen.BUBBLE_BORDER_WIDTH, 2)
        addDimenRow(SkDimen.BUBBLE_CORNER_RADIUS, 2)
        addBubblePreview(2)
        addRoleGroup(SkArea.BUBBLES, R.string.sk_group_roles_bubbles)
        addRoleGroup(SkArea.SCREEN_CHAT, R.string.sk_group_roles_chat_items)
    }

    private fun buildButtons() {
        addColorRow(SkSlot.BUTTON_BACKGROUND, 1)
        addTextSlot(SkSlot.BUTTON_TEXT, 1)
        addColorRow(SkSlot.BUTTON_BORDER, 1)
        addDimenRow(SkDimen.BUTTON_BORDER_WIDTH, 1)
        addDimenRow(SkDimen.BUTTON_CORNER_RADIUS, 1)
        addButtonPreview(1)
        addRoleRows(SkArea.BUTTONS, 1)
    }

    private fun buildSwitches() {
        addRoleRows(SkArea.SWITCHES, 1) { it.key == "switch_on" }
        addColorRow(SkSlot.SWITCH_OFF, 1)
        addRoleRows(SkArea.SWITCHES, 1) { it.key != "switch_on" }
    }

    private fun buildMenus() {
        addRoleRows(SkArea.MENUS, 1)
        // The icon of a menu row is coloured by what the row does (SkMenus).
        addSubgroup(R.string.sk_group_menu_icons, 1)
        addColorRow(SkSlot.MENU_CALL, 2)
        addColorRow(SkSlot.MENU_MESSAGE, 2)
        addColorRow(SkSlot.MENU_CONTACT, 2)
        addColorRow(SkSlot.MENU_SHARE, 2)
        addColorRow(SkSlot.MENU_VIEW, 2)
        addColorRow(SkSlot.MENU_BLOCK, 2)
        addColorRow(SkSlot.MENU_DELETE, 2)
        addRoleGroup(SkArea.SCREEN_MENUS, R.string.sk_group_roles_upstream_menus)
    }

    private fun buildDialogs() {
        addRoleRows(SkArea.DIALOGS, 1)
        addRoleGroup(SkArea.SCREEN_DIALOGS, R.string.sk_group_roles_upstream_dialogs)
    }

    private fun buildText() = addRoleRows(SkArea.TEXT, 1)

    private fun buildSurfaces() = addRoleRows(SkArea.SURFACES, 1)

    private fun buildFields() = addRoleRows(SkArea.FIELDS, 1)

    private fun buildStatus() = addRoleRows(SkArea.STATUS, 1)

    private fun buildMarks() = addRoleRows(SkArea.MARKS, 1)

    private fun buildSheets() = addRoleRows(SkArea.SHEETS, 1)

    private fun buildFlashes() = addRoleRows(SkArea.FLASH, 1)

    private fun buildPage() {
        addRoleRows(SkArea.PAGE, 1) { !it.key.startsWith("section_") }
        addSubgroup(R.string.sk_group_section_icons, 1)
        addRoleRows(SkArea.PAGE, 2) { it.key.startsWith("section_") }
    }

    private fun buildAssistant() = addRoleRows(SkArea.ASSISTANT, 1)

    private fun buildMeetings() = addRoleRows(SkArea.MEETINGS, 1)

    /**
     * One swatch row for every role of [area]: its colour of the moment, a tap opens the picker,
     * and the picker's Default takes the stored colour away again. Every role of [SkRoles] is on
     * this page exactly once, in the section its item belongs to.
     */
    private fun addRoleRows(area: SkArea, level: Int, only: (SkRole) -> Boolean = { true }) {
        for (role in SkRoles.inArea(area).filter(only)) {
            val row = ItemSkColorBinding.inflate(LayoutInflater.from(this), body, false)
            row.skColorLabel.text = SkRoleLabels.of(this, role)
            rowIcon(row.skColorLabel, R.drawable.sk_ic_drop)
            row.skColorLabel.setTextColor(SkRoles.color(this, "page_label"))
            row.skColorPreview.background = swatch(SkRoles.color(this, role))
            row.root.setOnClickListener {
                SkColorPickerDialog(this, SkRoles.color(this, role), showDefault = true) { wasPositive, color ->
                    if (wasPositive) SkRoles.set(this, role, color) else SkRoles.clear(this, role)
                    refreshPage()
                }
            }
            indentRow(row.root, level)
            body.addView(row.root)
        }
    }

    /** The roles of [area] under a sub-heading of their own, inside a section that holds more. */
    private fun addRoleGroup(area: SkArea, labelRes: Int) {
        addSubgroup(labelRes, 1)
        addRoleRows(area, 2)
    }

    private fun buildDiagnostics() {
        // ---- Diagnostics — every log behind its own switch, all of them off until asked for ----
        addIntroNote(R.string.sk_diag_note, 1)
        for (kind in SkDiagKind.entries) {
            addSwitchRow(kind.labelRes, kind.descRes, SkDiag.enabled(this, kind), 1, kind.icon, kind.role) {
                SkDiag.setEnabled(this, kind, it)
            }
        }
        addSubgroup(R.string.sk_group_diag_data, 1)
        addDiagStoredRow(2)
        addDiagKeepDaysRow(2)
        addActionRow(
            R.string.sk_diag_export,
            R.string.sk_diag_export_desc,
            main = false,
            opens = true,
            level = 2,
            icon = R.drawable.download_simple,
            role = SkMenus.SHARE,
        ) { diagExportPicker.launch(diagExportStart()) }
        addActionRow(
            R.string.sk_diag_share,
            R.string.sk_diag_share_desc,
            main = false,
            opens = true,
            level = 2,
            icon = R.drawable.share_network,
            role = SkMenus.SHARE,
        ) { shareDiagnostics() }
        // Acts in place after a confirmation, so no chevron.
        addActionRow(
            R.string.sk_diag_purge,
            R.string.sk_diag_purge_desc,
            main = false,
            opens = false,
            level = 2,
            icon = R.drawable.trash,
            role = SkMenus.DELETE,
        ) { purgeDiagnostics() }
        addDiagWhereRow(2)
    }

    private fun buildMaintenance() {
        // Acts in place after a confirmation, so no chevron; it takes colours away, so it is red.
        addActionRow(
            R.string.sk_reset_all_colours,
            R.string.sk_reset_all_colours_desc,
            main = false,
            opens = false,
            level = 1,
            icon = R.drawable.sk_ic_drop,
            role = SkMenus.DELETE,
        ) {
            SkConfirmDialog(
                this,
                getString(R.string.sk_reset_all_colours),
                getString(R.string.sk_reset_all_colours_warning),
                getString(R.string.sk_reset_all_colours),
                destructive = true,
            ) {
                SkRoles.resetAll(this)
                refreshPage()
            }
        }
        // Acts in place, so no chevron.
        addActionRow(
            R.string.sk_reset_section_order,
            R.string.sk_reset_section_order_desc,
            main = false,
            opens = false,
            level = 1,
            icon = R.drawable.arrow_clockwise,
        ) {
            SkPageState.resetOrder(this)
            buildRows()
        }
    }

    /**
     * The link to the app's own settings. Both pages live in one task and the main activity is
     * single-task, so asking it for its settings page brings it forward over this one.
     */
    private fun openAppSettings() {
        startActivity(
            Intent(this, org.linphone.ui.main.MainActivity::class.java)
                .putExtra(SkSisterApps.EXTRA_OPEN_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    /**
     * Which apps may place calls through this one — a selection from every app on the phone, the
     * three sister apps let in by default. See [SkCallDoor] and [SkAppPickerDialog].
     */
    private fun addCallDoorRow(level: Int) {
        val row = addActionRow(
            R.string.sk_call_door_apps,
            R.string.sk_call_door_apps_desc,
            main = true,
            opens = true,
            level = level,
            icon = R.drawable.phone_plus,
            role = SkMenus.CALL,
        ) { SkAppPickerDialog(this) { buildRows() }.show() }
        val names = SkAppPickerDialog.describe(this)
        setActionStatus(
            row,
            if (names.isEmpty()) {
                getString(R.string.sk_call_door_apps_none)
            } else {
                getString(R.string.sk_call_door_apps_status, names)
            },
            warn = names.isEmpty(),
        )
    }

    // ------------------------------------------------------------------ sections, folding, index

    /** The chip index: one small outlined chip per section; a tap jumps, a long press drags. */
    private fun addSectionIndex(ordered: List<Section>) {
        val index = SkSectionIndex.build(
            this,
            ordered.map {
                SkSectionIndex.Item(it.key, getString(it.labelRes), it.icon, SkRoles.color(this, it.tone))
            },
            SkRoles.color(this, "page_chip_frame"),
            SkRoles.color(this, "page_chip_text"),
            onJump = { jumpToSection(it) },
            onReordered = {
                SkPageState.setOrder(this, it)
                buildRows()
            },
        )
        val density = resources.displayMetrics.density
        val wrap = LinearLayout(this).apply {
            setPadding(
                (SECTION_INSET_DP * density).toInt(),
                (6 * density).toInt(),
                (16 * density).toInt(),
                (2 * density).toInt(),
            )
            addView(index, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT))
        }
        binding.skHolder.addView(wrap)
    }

    /**
     * A folding section: the settled heading with a › (folded) or ⌄ (open) at its right, the thin
     * line above it kept either way, and its rows in an inner box that folds away. Folded unless
     * [SkPageState] says otherwise; the rows of a folded section are not built until it is first
     * opened, which is what keeps this long page quick to draw.
     */
    private fun addSection(section: Section) {
        val row = ItemSkSectionBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkRoles.color(this, "page_heading")
        row.skSectionLabel.setText(section.labelRes)
        row.skSectionLabel.setTextColor(accent)
        // The same icon in the same colour as the section's chip; the underline stays as wide
        // as the text, so it starts where the icon's gutter ends.
        SkLabelIcon.set(
            row.skSectionLabel,
            androidx.appcompat.content.res.AppCompatResources.getDrawable(this, section.icon),
            SkRoles.color(this, section.tone),
        ) { gutter ->
            (row.skSectionRule.layoutParams as android.view.ViewGroup.MarginLayoutParams).marginStart = gutter
            row.skSectionRule.requestLayout()
        }
        row.skSectionRule.setBackgroundColor(SkRoles.color(this, "page_heading_rule"))
        // The line between sections, a darker accent — it stays whether the section is folded or open.
        row.skSectionSpacer.setBackgroundColor(SkRoles.color(this, "page_divider"))
        row.skSectionChevron.setTextColor(accent)
        row.skSectionHeader.setOnClickListener { toggleSection(section) }
        binding.skHolder.addView(row.root)
        sectionWrappers[section.key] = row.root
        sectionBodies[section.key] = row.skSectionBody
        sectionChevrons[section.key] = row.skSectionChevron
        showSection(section, SkPageState.isOpen(this, section.key))
    }

    private fun toggleSection(section: Section) {
        val open = !SkPageState.isOpen(this, section.key)
        SkPageState.setOpen(this, section.key, open)
        showSection(section, open)
    }

    private fun showSection(section: Section, open: Boolean) {
        val inner = sectionBodies[section.key] ?: return
        if (open && builtSections.add(section.key)) {
            body = inner
            section.build()
            body = binding.skHolder
            updatePreviews()
        }
        inner.visibility = if (open) View.VISIBLE else View.GONE
        sectionChevrons[section.key]?.text = if (open) CHEVRON_OPEN else CHEVRON
    }

    /** A tap on an index chip: unfold the section and scroll to it. */
    private fun jumpToSection(key: String) {
        val section = sections().firstOrNull { it.key == key } ?: return
        val wrapper = sectionWrappers[key] ?: return
        SkPageState.setOpen(this, key, true)
        showSection(section, true)
        binding.skHolder.post { binding.skScroll.smoothScrollTo(0, wrapper.top) }
    }

    /**
     * The icon every row carries before its label, coloured by what the row does — the same
     * settable roles as the menu icons ([SkMenus]). Sized by its label ([SkLabelIcon]); what
     * stands [under] the label — the explanation of the row — is moved in by the icon's gutter,
     * so it stays aligned with the label's text.
     */
    private fun rowIcon(label: TextView, iconRes: Int, role: String = SkMenus.VIEW, vararg under: View) {
        val starts = under.map { it.paddingStart }
        SkLabelIcon.set(
            label,
            androidx.appcompat.content.res.AppCompatResources.getDrawable(this, iconRes),
            SkTheme.color(this, SkMenus.slotOf(role)),
        ) { gutter ->
            under.forEachIndexed { i, view ->
                view.setPaddingRelative(starts[i] + gutter, view.paddingTop, view.paddingEnd, view.paddingBottom)
            }
        }
    }

    /**
     * The 2 dp dim-gold rule down the left edge of a row that carries an explanation, 10 dp
     * before its text. Drawn over the row rather than laid out in it, so the text stays where the
     * indent put it. Call after [indentRow].
     */
    private fun ruled(row: View) {
        val density = resources.displayMetrics.density
        row.foreground = LeftRule(
            row.paddingStart - (RULE_GAP_DP + RULE_WIDTH_DP) * density,
            RULE_WIDTH_DP * density,
            row.paddingTop.toFloat(),
            SkRoles.color(this, "page_rule"),
        )
    }

    // ------------------------------------------------------------------ diagnostics

    /** A note belonging to a whole section rather than to one row: dim gold, nothing to press. */
    private fun addIntroNote(textRes: Int, level: Int) {
        val row = ItemSkNoteBinding.inflate(LayoutInflater.from(this), body, false)
        row.skNoteLabel.setText(textRes)
        row.skNoteLabel.textSize = NOTE_TEXT_SP
        row.skNoteLabel.setTextColor(SkRoles.color(this, "page_note"))
        row.skNoteLabel.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        row.skNoteValue.visibility = View.GONE
        indentRow(row.root, level)
        body.addView(row.root)
    }

    /** What is on the phone. Counting lists the directory, so it is filled in from a thread. */
    private fun addDiagStoredRow(level: Int) {
        val row = ItemSkNoteBinding.inflate(LayoutInflater.from(this), body, false)
        row.skNoteLabel.setText(R.string.sk_diag_stored)
        rowIcon(row.skNoteLabel, R.drawable.file_text)
        row.skNoteLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skNoteValue.setTextColor(SkRoles.color(this, "page_note"))
        row.skNoteValue.text = PENDING_VALUE
        Thread({
            val described = runCatching { SkDiag.inventory(this).describe(this) }.getOrNull()
            if (described != null) {
                runOnUiThread {
                    if (!isFinishing && row.root.isAttachedToWindow) {
                        row.skNoteValue.text = described
                    }
                }
            }
        }, "sk-diag-inventory").start()
        indentRow(row.root, level)
        body.addView(row.root)
    }

    /** How long recorded days are kept. 0 — the default — keeps everything. */
    private fun addDiagKeepDaysRow(level: Int) {
        val row = ItemSkDimenBinding.inflate(LayoutInflater.from(this), body, false)
        row.skDimenLabel.setText(R.string.sk_diag_keep_days)
        rowIcon(row.skDimenLabel, R.drawable.clock)
        row.skDimenLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skDimenValue.setTextColor(SkRoles.color(this, "page_note"))
        tintSeekBar(row.skDimenSeekbar, SkRoles.color(this, "page_slider"))
        row.skDimenSeekbar.max = MAX_KEEP_DAYS
        row.skDimenSeekbar.progress = SkDiag.keepDays(this)
        row.skDimenValue.text = keepDaysLabel(SkDiag.keepDays(this))
        row.skDimenSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                SkDiag.setKeepDays(this@SkUiActivity, progress)
                row.skDimenValue.text = keepDaysLabel(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        indentRow(row.root, level)
        body.addView(row.root)
    }

    /** The path to type into adb — the files are readable with no permission at all. */
    private fun addDiagWhereRow(level: Int) {
        val row = ItemSkNoteBinding.inflate(LayoutInflater.from(this), body, false)
        row.skNoteLabel.setText(R.string.sk_diag_where)
        rowIcon(row.skNoteLabel, R.drawable.info)
        row.skNoteLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skNoteValue.setTextColor(SkRoles.color(this, "page_note"))
        row.skNoteValue.text = SkDiag.directoryPath(this)
        indentRow(row.root, level)
        body.addView(row.root)
    }

    private fun keepDaysLabel(days: Int): String =
        if (days <= 0) {
            getString(R.string.sk_diag_keep_forever)
        } else {
            getString(R.string.sk_diag_keep_days_value, days)
        }

    /** The picker opens at "/sdcard/tmp"; null simply means the system picks its own start. */
    private fun diagExportStart(): Uri? = runCatching {
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, EXPORT_START_DOCUMENT)
    }.getOrNull()

    private fun exportDiagnostics(tree: Uri) {
        Thread({
            val message = try {
                SkDiag.export(this, tree)
            } catch (e: Exception) {
                getString(R.string.sk_diag_export_failed, e.message ?: e.toString())
            }
            runOnUiThread {
                if (!isFinishing) {
                    SkFlash.show(this, message)
                }
            }
        }, "sk-diag-export").start()
    }

    private fun shareDiagnostics() {
        val intent = SkDiag.shareIntent(this)
        if (intent == null) {
            SkFlash.show(this, R.string.sk_diag_stored_empty)
            return
        }
        startActivity(Intent.createChooser(intent, getString(R.string.sk_diag_share)))
    }

    /** Deleting is forever, so the warning says exactly what goes — files, size and days. */
    private fun purgeDiagnostics() {
        val inventory = SkDiag.inventory(this)
        if (inventory.empty) {
            SkFlash.show(this, R.string.sk_diag_stored_empty)
            return
        }
        SkConfirmDialog(
            this,
            getString(R.string.sk_diag_purge),
            getString(R.string.sk_diag_purge_warning, inventory.describe(this)),
            getString(R.string.sk_diag_purge_confirm),
            destructive = true,
        ) {
            val deleted = SkDiag.purge(this)
            SkFlash.show(this, getString(R.string.sk_diag_purged, deleted.files))
            buildRows()
        }
    }

    /** The subgroup layout carries its own kxkb indent (54dp) — no indentRow here. */
    private fun addSubgroup(labelRes: Int, @Suppress("UNUSED_PARAMETER") level: Int) {
        val row = ItemSkSubgroupBinding.inflate(LayoutInflater.from(this), body, false)
        val accent = SkRoles.color(this, "page_heading")
        row.skSubgroupLabel.setText(labelRes)
        row.skSubgroupLabel.setTextColor(accent)
        row.skSubgroupRule.setBackgroundColor(accent)
        body.addView(row.root)
    }

    /**
     * A pressable row: the label inside an **outlined** pill — never filled, and never wider than
     * the label it carries — with its explanation beneath, aligned with that label. [main] is the
     * one action its section is about (here: the Export / Import panel) and gets the 3dp frame and
     * bold label; every other action gets the thin 1.25dp frame. [opens] adds the › that promises
     * another screen — a row that acts in place after a confirmation carries none.
     *
     * The pill alone takes the touch, so the explanation below it is text and nothing more.
     * Returns the binding, for the callers that fill the status line under the description.
     */
    private fun addActionRow(
        titleRes: Int,
        descRes: Int,
        main: Boolean,
        opens: Boolean,
        level: Int,
        icon: Int = R.drawable.arrow_square_out,
        role: String = SkMenus.VIEW,
        onClick: () -> Unit,
    ): ItemSkActionBinding {
        val row = ItemSkActionBinding.inflate(LayoutInflater.from(this), body, false)
        // An action that takes something away lends its red to frame, label and ›.
        val danger = role == SkMenus.DELETE
        val accent = if (danger) SkTheme.color(this, SkSlot.MENU_DELETE) else SkRoles.color(this, "pill_frame")
        row.skActionTitle.setText(titleRes)
        row.skActionTitle.setTextColor(if (danger) accent else SkRoles.color(this, "pill_text"))
        row.skActionTitle.setTypeface(null, if (main) Typeface.BOLD else Typeface.NORMAL)
        row.skActionChevron.text = CHEVRON
        row.skActionChevron.setTextColor(accent)
        row.skActionChevron.visibility = if (opens) View.VISIBLE else View.GONE
        rowIcon(row.skActionTitle, icon, role, row.skActionDesc, row.skActionStatus)
        if (descRes != 0) {
            row.skActionDesc.setText(descRes)
            row.skActionDesc.setTextColor(SkRoles.color(this, "page_note"))
        } else {
            row.skActionDesc.visibility = View.GONE
        }
        // Shown only by the rows that have something to report under the description.
        row.skActionStatus.visibility = View.GONE
        row.skActionPill.background = pillFrame(accent, main)
        row.skActionPill.setOnClickListener { onClick() }
        indentRow(row.root, level)
        if (descRes != 0) ruled(row.root)
        body.addView(row.root)
        return row
    }

    /** The status line under an action row's description, in the secondary colour or warning red. */
    private fun setActionStatus(row: ItemSkActionBinding, status: CharSequence, warn: Boolean) {
        row.skActionStatus.text = status
        row.skActionStatus.visibility = View.VISIBLE
        row.skActionStatus.setTextColor(
            if (warn) SkTheme.color(this, SkSlot.MENU_DELETE) else SkRoles.color(this, "page_note"),
        )
    }

    /**
     * The house pill: fully round, the frame in [color], the page showing through — never filled.
     * [main] draws the thick frame kept for the main action of a block. The ripple rides on the
     * pill itself, so only the pill answers a touch.
     */
    private fun pillFrame(color: Int, main: Boolean): RippleDrawable {
        val density = resources.displayMetrics.density
        val frame = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0)
            cornerRadius = PILL_RADIUS_DP * density
            setStroke(
                (((if (main) PILL_FRAME_MAIN_DP else PILL_FRAME_DP) * density) + 0.5f).toInt()
                    .coerceAtLeast(1),
                color,
            )
        }
        return RippleDrawable(
            ColorStateList.valueOf((color and 0x00FFFFFF) or PILL_RIPPLE_ALPHA),
            frame,
            null,
        )
    }

    private fun addEximportRow(level: Int) {
        // The one thing this section is for — the thick frame, and the › of a panel that opens.
        val row = addActionRow(
            R.string.sk_section_eximport,
            R.string.sk_eim_row_desc,
            main = true,
            opens = true,
            level = level,
            icon = R.drawable.download_simple,
            role = SkMenus.SHARE,
        ) { openEximport() }
        // Queried on page open: the latest export in the settable directory. Red until it is set.
        val (status, warn) = SkEximport.lastExportStatus(this)
        setActionStatus(row, status, warn)
    }

    private fun openEximport() {
        eximPanel = SkEximportPanel(
            this,
            pickDirectory = { eximDirPicker.launch(null) },
            createExportFile = { name -> eximExportTarget.launch(name) },
            openImportFile = {
                eximImportSource.launch(
                    arrayOf("application/zip", "application/octet-stream", "*/*"),
                )
            },
        ).also { it.show() }
    }

    /**
     * The 保存復元 master switch — **ON by default** since contract v2, so this app is on the batch
     * with nothing to configure, and answers a restore on a phone that has just been wiped. It
     * stays a switch rather than being removed because it is the only way to close this app off
     * entirely, and a feature that can be turned on but never off is one 白い熊 cannot retreat from.
     */
    private fun addAutomationSwitchRow(level: Int) {
        val row = ItemSkSwitchBinding.inflate(LayoutInflater.from(this), body, false)
        val accent = SkRoles.color(this, "page_heading")
        row.skSwitchTitle.setText(R.string.sk_auto_title)
        rowIcon(row.skSwitchTitle, R.drawable.wrench, SkMenus.SHARE, row.skSwitchDesc)
        row.skSwitchTitle.setTextColor(SkRoles.color(this, "page_label"))
        row.skSwitchDesc.setText(R.string.sk_auto_desc)
        row.skSwitchDesc.setTextColor(SkRoles.color(this, "page_note"))
        SkStyler.styleSwitch(row.skSwitchToggle)
        row.skSwitchToggle.isChecked = SkAutomation.enabled(this)
        row.skSwitchToggle.setOnCheckedChangeListener { _, checked ->
            SkAutomation.setEnabled(this, checked)
        }
        // The switch itself is not clickable — the whole row is its hit area.
        row.root.setOnClickListener { row.skSwitchToggle.toggle() }
        indentRow(row.root, level)
        ruled(row.root)
        body.addView(row.root)
    }

    /**
     * 「Use authorization token?」 — **OFF by default** since contract v2.
     *
     * Off, any sister app may drive the automation; on, a caller must also present the token below.
     * The data door checks the caller's package name, uid and signing certificate either way, which
     * is what makes the token affordable to switch off: a pasted secret cannot survive the wipe
     * that 応用管理's restore exists to recover from.
     */
    private fun addAutomationRequireTokenRow(level: Int) {
        val row = ItemSkSwitchBinding.inflate(LayoutInflater.from(this), body, false)
        val accent = SkRoles.color(this, "page_heading")
        row.skSwitchTitle.setText(R.string.sk_auto_require_token_title)
        rowIcon(row.skSwitchTitle, R.drawable.lock_key, SkMenus.VIEW, row.skSwitchDesc)
        row.skSwitchTitle.setTextColor(SkRoles.color(this, "page_label"))
        row.skSwitchDesc.setText(R.string.sk_auto_require_token_desc)
        row.skSwitchDesc.setTextColor(SkRoles.color(this, "page_note"))
        SkStyler.styleSwitch(row.skSwitchToggle)
        row.skSwitchToggle.isChecked = SkAutomation.requireToken(this)
        row.skSwitchToggle.setOnCheckedChangeListener { _, checked ->
            SkAutomation.setRequireToken(this, checked)
            // The token row appears and disappears with this switch, so the page is rebuilt —
            // posted rather than immediate, so the holder is not emptied inside the click dispatch
            // of one of its own children.
            binding.skHolder.post { buildRows() }
        }
        row.root.setOnClickListener { row.skSwitchToggle.toggle() }
        indentRow(row.root, level)
        ruled(row.root)
        body.addView(row.root)
    }

    /** Tap to copy the full token; "Regenerate" replaces it after a warning. */
    private fun addAutomationTokenRow(level: Int) {
        val row = ItemSkTokenBinding.inflate(LayoutInflater.from(this), body, false)
        row.skTokenTitle.setText(R.string.sk_auto_token_title)
        rowIcon(row.skTokenTitle, R.drawable.password)
        row.skTokenTitle.setTextColor(SkRoles.color(this, "page_label"))
        row.skTokenValue.setTextColor(SkRoles.color(this, "page_heading"))
        row.skTokenValue.text = SkAutomation.abbreviate(SkAutomation.token(this))
        row.skTokenRegenerate.setText(R.string.sk_auto_token_regenerate)
        row.skTokenRegenerate.setTextColor(SkTheme.color(this, SkSlot.MENU_DELETE))
        // No bare-text buttons: it is a pill like every other action, and red because it destroys
        // the token in place — a destructive action lends its colour to the frame, never a fill.
        row.skTokenRegenerate.background = pillFrame(SkTheme.color(this, SkSlot.MENU_DELETE), main = false)
        row.root.setOnClickListener {
            // Deliberately not toasting the value itself — the row abbreviates it for a reason.
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(getString(R.string.sk_auto_token_title), SkAutomation.token(this)),
            )
            SkFlash.show(this, R.string.sk_auto_token_copied)
        }
        row.skTokenRegenerate.setOnClickListener {
            SkConfirmDialog(
                this,
                getString(R.string.sk_auto_token_regenerate),
                getString(R.string.sk_auto_token_regen_warning),
                destructive = true,
            ) {
                row.skTokenValue.text = SkAutomation.abbreviate(SkAutomation.regenerateToken(this))
                SkFlash.show(this, R.string.sk_auto_token_regenerated)
            }
        }
        indentRow(row.root, level)
        body.addView(row.root)
    }

    /**
     * All-files access (API 30+): what lets an automation export write to the absolute directory
     * the caller names, instead of only the SAF directory picked above.
     */
    private fun addAllFilesAccessRow(level: Int) {
        val granted = Environment.isExternalStorageManager()
        val row = addActionRow(
            R.string.sk_auto_allfiles_title,
            R.string.sk_auto_allfiles_desc,
            main = false,
            opens = true,
            level = level,
            icon = R.drawable.file,
        ) { openAllFilesAccessSettings() }
        setActionStatus(
            row,
            getString(
                if (granted) R.string.sk_auto_allfiles_granted else R.string.sk_auto_allfiles_needed,
            ),
            warn = !granted,
        )
    }

    private fun openAllFilesAccessSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                SkFlash.show(this, e2.message ?: e2.toString())
            }
        }
    }

    /** Panel dismissed — refresh the last-export status line (unless the chain closed us). */
    fun onEximportPanelClosed() {
        eximPanel = null
        if (!isFinishing) {
            buildRows()
        }
    }

    /**
     * "Display over other apps" — the licence [SkRinger] needs to open the full call screen itself
     * when a call arrives with the screen already on. Built like [addAllFilesAccessRow]: it reports
     * the current state and opens the settings screen, and onResume rebuilds the page so the state
     * is right again on the way back. Re-read on every build because the grant can be withdrawn.
     */
    private fun addCallScreenOverlayRow(level: Int) {
        val granted = SkRinger.canRaiseCallScreen(this)
        val row = addActionRow(
            R.string.sk_call_overlay_title,
            R.string.sk_call_overlay_desc,
            main = false,
            opens = true,
            level = level,
            icon = R.drawable.phone_call,
            role = SkMenus.CALL,
        ) { SkRinger.openCallScreenPermissionSettings(this) }
        setActionStatus(
            row,
            getString(
                if (granted) R.string.sk_call_overlay_granted else R.string.sk_call_overlay_needed,
            ),
            warn = !granted,
        )
    }

    /**
     * The lines offered when a call is refused with a message — the general list, and a list of
     * its own for any caller that has earned one. Kept here rather than on the call screen: the
     * picker there is for using them in a hurry, and a list is not maintained in a hurry.
     */
    private fun addDeclineMessagesRow(level: Int) {
        val row = addActionRow(
            R.string.sk_call_decline_messages_title,
            R.string.sk_call_decline_messages_desc,
            main = false,
            opens = true,
            level = level,
            icon = R.drawable.chat_teardrop_text,
            role = SkMenus.MESSAGE,
        ) { openDeclineMessages() }
        setActionStatus(
            row,
            getString(
                R.string.sk_call_decline_messages_status,
                SkDeclineMessages.general(this).size,
                SkDeclineMessages.callersWithOwnMessages(this).size,
            ),
            warn = false,
        )
    }

    /**
     * The blocked numbers — the register a caller is put in from the incoming-call screen or the
     * history, and the one place they are taken out of it again in bulk. See [SkBlocked].
     */
    private fun addBlockedNumbersRow(level: Int) {
        val row = addActionRow(
            R.string.sk_blocked_numbers_title,
            R.string.sk_blocked_numbers_desc,
            main = false,
            opens = true,
            level = level,
            icon = R.drawable.sk_prohibit,
            role = SkMenus.BLOCK,
        ) { SkBlockedNumbersDialog(this) { buildRows() }.show() }
        setActionStatus(
            row,
            getString(R.string.sk_blocked_numbers_status, SkBlocked.count(this)),
            warn = false,
        )
    }

    /** Pick which list to edit: the general one, or one belonging to a particular caller. */
    private fun openDeclineMessages() {
        val callers = SkDeclineMessages.callersWithOwnMessages(this)
        val options = listOf(getString(R.string.sk_call_decline_editor_title)) + callers
        SkChoiceDialog(
            this,
            getString(R.string.sk_call_decline_messages_title),
            options,
            -1,
        ) { index ->
            if (index == 0) {
                SkDeclineMessagesEditor(
                    this,
                    "",
                    getString(R.string.sk_call_decline_editor_title),
                ) { buildRows() }.show()
            } else {
                val address = callers[index - 1]
                SkDeclineMessagesEditor(
                    this,
                    address,
                    getString(R.string.sk_call_decline_editor_contact_title, address),
                ) { buildRows() }.show()
            }
        }
    }

    private fun addColorRow(slot: SkSlot, level: Int) {
        val row = ItemSkColorBinding.inflate(LayoutInflater.from(this), body, false)
        row.skColorLabel.setText(slot.labelRes)
        rowIcon(row.skColorLabel, R.drawable.sk_ic_drop)
        row.skColorLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skColorPreview.background = swatch(SkTheme.color(this, slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, level)
        body.addView(row.root)
    }

    private fun addTextSlot(slot: SkSlot, level: Int) {
        val row = ItemSkTextBinding.inflate(LayoutInflater.from(this), body, false)
        val textColor = SkRoles.color(this, "page_label")
        val secondary = SkRoles.color(this, "page_note")
        val accent = SkRoles.color(this, "page_heading")

        row.skTextLabel.setText(slot.labelRes)
        rowIcon(row.skTextLabel, R.drawable.sk_ic_drop)
        rowIcon(row.skTextFontTitle, R.drawable.sk_ic_text)
        rowIcon(row.skTextWeightTitle, R.drawable.sk_ic_text)
        rowIcon(row.skTextSizeTitle, R.drawable.sk_ic_sliders)
        row.skTextLabel.setTextColor(textColor)
        row.skTextColorPreview.background = swatch(SkTheme.color(this, slot))
        row.skTextColorRow.setOnClickListener { openColorPicker(slot) }

        row.skTextFontTitle.setTextColor(textColor)
        row.skTextFontValue.setTextColor(secondary)
        row.skTextFontValue.text = SkFonts.fontDisplayName(this, SkTheme.fontFamily(this, slot))
        row.skTextFontRow.setOnClickListener { openFontPicker(slot, row) }

        row.skTextWeightTitle.setTextColor(textColor)
        row.skTextWeightValue.setTextColor(secondary)
        row.skTextWeightValue.setText(
            SkFonts.WeightOption.fromValue(SkTheme.fontWeight(this, slot)).labelRes,
        )
        row.skTextWeightRow.setOnClickListener { openWeightPicker(slot, row) }

        row.skTextSizeTitle.setTextColor(textColor)
        row.skTextSizeValue.setTextColor(secondary)
        tintSeekBar(row.skTextSizeSeekbar, accent)
        row.skTextSizeSeekbar.max = SkTheme.MAX_FONT_SIZE_SP
        row.skTextSizeSeekbar.progress = SkTheme.fontSize(this, slot)
        row.skTextSizeValue.text = sizeLabel(SkTheme.fontSize(this, slot))
        row.skTextSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                SkTheme.setFontSize(this@SkUiActivity, slot, progress)
                row.skTextSizeValue.text = sizeLabel(progress)
                SkFonts.showSample(row.skTextSample, slot)
                updatePreviews()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {
                SkStyler.apply(this@SkUiActivity)
            }
        })

        SkFonts.showSample(row.skTextSample, slot)

        // The slot's own header row sits at [level]; its controls one full step deeper.
        indentRow(row.skTextColorRow, level)
        indentRow(row.skTextFontRow, level + 1)
        indentRow(row.skTextWeightRow, level + 1)
        indentRow(row.skTextSizeRow, level + 1)
        indentRow(row.skTextSample, level + 1)
        body.addView(row.root)
    }

    /**
     * A generic on/off row. [onChange] stores the value; the previews are redrawn straight after,
     * so a toggle is never applied blind.
     */
    private fun addSwitchRow(
        titleRes: Int,
        descRes: Int,
        checked: Boolean,
        level: Int,
        icon: Int = R.drawable.gear,
        role: String = SkMenus.VIEW,
        onChange: (Boolean) -> Unit,
    ) {
        val row = ItemSkSwitchBinding.inflate(LayoutInflater.from(this), body, false)
        val accent = SkRoles.color(this, "page_heading")
        row.skSwitchTitle.setText(titleRes)
        rowIcon(row.skSwitchTitle, icon, role, row.skSwitchDesc)
        row.skSwitchTitle.setTextColor(SkRoles.color(this, "page_label"))
        row.skSwitchDesc.setText(descRes)
        row.skSwitchDesc.setTextColor(SkRoles.color(this, "page_note"))
        SkStyler.styleSwitch(row.skSwitchToggle)
        row.skSwitchToggle.isChecked = checked
        row.skSwitchToggle.setOnCheckedChangeListener { _, value ->
            onChange(value)
            updatePreviews()
        }
        row.root.setOnClickListener { row.skSwitchToggle.toggle() }
        indentRow(row.root, level)
        ruled(row.root)
        body.addView(row.root)
    }

    /** A row naming a setting and the option currently picked for it; tapping opens the list. */
    private fun addChoiceRow(
        labelRes: Int,
        options: List<String>,
        selectedIndex: Int,
        level: Int,
        onPick: (Int) -> Unit,
    ) {
        val row = ItemSkChoiceBinding.inflate(LayoutInflater.from(this), body, false)
        row.skChoiceLabel.setText(labelRes)
        rowIcon(row.skChoiceLabel, R.drawable.list)
        row.skChoiceLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skChoiceValue.setTextColor(SkRoles.color(this, "page_note"))
        row.skChoiceValue.text = options.getOrElse(selectedIndex) { "" }
        row.root.setOnClickListener {
            SkChoiceDialog(this, getString(labelRes), options, selectedIndex) { picked ->
                onPick(picked)
                row.skChoiceValue.text = options.getOrElse(picked) { "" }
                updatePreviews()
            }
        }
        indentRow(row.root, level)
        body.addView(row.root)
    }

    private fun addDimenRow(dimen: SkDimen, level: Int) {
        val row = ItemSkDimenBinding.inflate(LayoutInflater.from(this), body, false)
        row.skDimenLabel.setText(dimen.labelRes)
        rowIcon(row.skDimenLabel, R.drawable.sk_ic_sliders)
        row.skDimenLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skDimenValue.setTextColor(SkRoles.color(this, "page_note"))
        tintSeekBar(row.skDimenSeekbar, SkRoles.color(this, "page_slider"))
        row.skDimenSeekbar.max = dimen.maxDp
        row.skDimenSeekbar.progress = SkTheme.dimenDp(this, dimen)
        row.skDimenValue.text = dimenLabel(dimen, SkTheme.dimenDp(this, dimen))
        row.skDimenSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = progress.coerceAtLeast(dimen.minValue)
                SkTheme.setDimenDp(this@SkUiActivity, dimen, value)
                row.skDimenValue.text = dimenLabel(dimen, value)
                updatePreviews()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        indentRow(row.root, level)
        body.addView(row.root)
    }

    // ------------------------------------------------------------------ live previews

    private fun addListPreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), body, false)
        listPreview = row.skPreviewBox
        indentRow(row.root, level)
        body.addView(row.root)
        updatePreviews()
    }

    /**
     * The call-history sample: a day headline and one record, drawn through the same two
     * [SkStyler] entry points the real list uses, so the page shows exactly what will land.
     */
    private fun addCallRowPreview(level: Int) {
        val row = ItemSkCallPreviewBinding.inflate(LayoutInflater.from(this), body, false)
        callPreview = row
        indentRow(row.root, level)
        body.addView(row.root)
        updatePreviews()
    }

    private fun addBubblePreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), body, false)
        bubblePreview = row.skPreviewBox
        indentRow(row.root, level)
        body.addView(row.root)
        updatePreviews()
    }

    private fun addButtonPreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), body, false)
        buttonPreview = row.skPreviewBox
        indentRow(row.root, level)
        body.addView(row.root)
        updatePreviews()
    }

    /** Every slider/color change redraws the previews immediately — nothing is applied blind. */
    private fun updatePreviews() {
        listPreview?.let { preview ->
            preview.text = getString(R.string.sk_preview_list)
            preview.setTextColor(SkTheme.color(this, SkSlot.LIST_NAME))
            SkFonts.applyFont(preview, SkSlot.LIST_NAME)
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(SkTheme.color(this@SkUiActivity, SkSlot.LIST_BACKGROUND))
                cornerRadius = SkTheme.dimenPx(this@SkUiActivity, SkDimen.LIST_CORNER_RADIUS).toFloat()
                setStroke(
                    SkTheme.dimenPx(this@SkUiActivity, SkDimen.LIST_BORDER_WIDTH),
                    SkTheme.color(this@SkUiActivity, SkSlot.LIST_BORDER),
                )
            }
        }
        bubblePreview?.let { preview ->
            preview.text = getString(R.string.sk_preview_bubble)
            preview.setTextColor(SkTheme.color(this, SkSlot.BUBBLE_OUTGOING_TEXT))
            SkFonts.applyFont(preview, SkSlot.BUBBLE_OUTGOING_TEXT)
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(SkTheme.color(this@SkUiActivity, SkSlot.BUBBLE_OUTGOING))
                cornerRadius =
                    SkTheme.dimenPx(this@SkUiActivity, SkDimen.BUBBLE_CORNER_RADIUS).toFloat()
                setStroke(
                    SkTheme.dimenPx(this@SkUiActivity, SkDimen.BUBBLE_BORDER_WIDTH),
                    SkRoles.color(this@SkUiActivity, "page_swatch_frame"),
                )
            }
        }
        callPreview?.let { preview ->
            // A day two back and a call at 15:36 that ran 11:01 — old enough that the headline
            // shows a real date rather than "Yesterday", and long enough to exercise both units.
            val sampleSecs = LocalDate.now()
                .minusDays(SAMPLE_DAYS_BACK)
                .atTime(SAMPLE_HOUR, SAMPLE_MINUTE)
                .atZone(ZoneId.systemDefault())
                .toEpochSecond()

            preview.skPreviewDayText.text = SkCallLog.dayText(this, sampleSecs)
            preview.skPreviewName.text = getString(R.string.sk_preview_call_name)
            preview.skPreviewNumber.text = getString(R.string.sk_preview_call_number)
            preview.skPreviewTime.text = SkCallLog.timeText(this, sampleSecs)

            val duration = SkCallLog.durationText(this, SAMPLE_DURATION_SECS, connected = true)
            preview.skPreviewDuration.text = duration
            val durationShown = if (duration.isEmpty()) View.GONE else View.VISIBLE
            preview.skPreviewDuration.visibility = durationShown
            preview.skPreviewSeparator.visibility = durationShown

            SkStyler.styleDayHeader(
                preview.root,
                preview.skPreviewDayDivider,
                preview.skPreviewDayText,
                preview.skPreviewDayUnderline,
            )
            SkStyler.styleCallHistoryRow(
                row = preview.skPreviewRow,
                avatar = null,
                name = preview.skPreviewName,
                number = preview.skPreviewNumber,
                time = preview.skPreviewTime,
                durationSeparator = preview.skPreviewSeparator,
                duration = preview.skPreviewDuration,
                directionIcon = preview.skPreviewArrow,
                direction = SkCallLog.Direction.INCOMING,
                rowDivider = preview.skPreviewRowDivider,
            )
        }
        buttonPreview?.let { preview ->
            preview.text = getString(R.string.sk_preview_button)
            preview.setTextColor(SkTheme.color(this, SkSlot.BUTTON_TEXT))
            SkFonts.applyFont(preview, SkSlot.BUTTON_TEXT)
            preview.background = SkStyler.pillBackground(this)
        }
    }

    // ------------------------------------------------------------------ pickers

    private fun openColorPicker(slot: SkSlot) {
        SkColorPickerDialog(this, SkTheme.color(this, slot), showDefault = true) { wasPositive, color ->
            if (wasPositive) {
                SkTheme.setColor(this, slot, color)
            } else {
                SkTheme.clearColor(this, slot)
            }
            refreshPage()
        }
    }

    private fun openFontPicker(slot: SkSlot, row: ItemSkTextBinding) {
        SkFontPickerDialog(
            this,
            onAddFont = {
                pendingFontSlot = slot
                openFontDocument.launch(arrayOf("*/*"))
            },
            onPick = { fileName ->
                SkTheme.setFontFamily(this, slot, fileName)
                row.skTextFontValue.text = SkFonts.fontDisplayName(this, fileName)
                SkFonts.showSample(row.skTextSample, slot)
                updatePreviews()
                SkStyler.apply(this)
            },
        )
    }

    private fun openWeightPicker(slot: SkSlot, row: ItemSkTextBinding) {
        val options = SkFonts.WeightOption.entries
        SkChoiceDialog(
            this,
            getString(R.string.sk_weight),
            options.map { getString(it.labelRes) },
            options.indexOf(SkFonts.WeightOption.fromValue(SkTheme.fontWeight(this, slot))),
        ) { which ->
            SkTheme.setFontWeight(this, slot, options[which].value)
            row.skTextWeightValue.setText(options[which].labelRes)
            SkFonts.showSample(row.skTextSample, slot)
            updatePreviews()
            SkStyler.apply(this)
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A count reads as a bare number; everything else is a dp length. */
    private fun dimenLabel(dimen: SkDimen, value: Int): String =
        if (dimen.isCount) value.toString() else getString(R.string.sk_dp_value, value)

    private fun sizeLabel(sizeSp: Int): String =
        if (sizeSp <= 0) getString(R.string.sk_default) else getString(R.string.sk_sp_value, sizeSp)

    /**
     * Absolute indentation on the kxkb ladder: heading 36dp → subgroup 54dp → L1 rows 72dp →
     * L2 rows 90dp (18dp steps from a 54dp base), so which level a row belongs to is instant.
     */

    /**
     * One row per SIP account: its name, and at the right the badge it carries — drawn as the
     * badge itself, in its colours. Tapping the row opens the picker. Under a numbered account
     * come the two colours of its badge, each with its swatch, so two lines can be told apart at
     * a glance in the call history.
     *
     * The accounts are the Core's, so they are read on its thread and the rows are filled in when
     * it answers; the box they go into is placed now, which is what keeps them under their heading.
     */
    private fun addRinNumberRows(level: Int) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(box)

        coreContext.postOnCoreThread { core ->
            // In the order of the tab strip (白い熊's own, SkAccountOrder), not the Core's: a row
            // here has to be recognisably the tab on the main screen. Two accounts whose names
            // quote the same phone number were mistaken for each other on 2026-11-17, and the
            // numbers went to the wrong pair.
            val ordered = SkAccountOrder.sorted(this, core.accountList.toList()) {
                it.params.identityAddress?.asStringUriOnly().orEmpty()
            }
            val pictures = HashMap<String, Bitmap>()
            val accounts = ordered.map { account ->
                val identity = account.params.identityAddress
                val uri = identity?.asStringUriOnly().orEmpty()
                try {
                    account.params.pictureUri?.takeIf { it.isNotEmpty() }
                        ?.let { ImageUtils.getBitmap(this, it, true) }
                        ?.let { pictures[uri] = it }
                } catch (e: Exception) {
                    // No picture, then: the row keeps the space and shows the name.
                }
                uri to (identity?.displayName?.takeIf { it.isNotEmpty() } ?: identity?.username ?: uri)
            }.filter { it.first.isNotEmpty() }

            coreContext.postOnMainThread {
                if (isFinishing || isDestroyed) return@postOnMainThread
                rinPictures = pictures
                fillRinNumberRows(box, accounts, level)
            }
        }
    }

    /** The accounts' own pictures, as their tabs show them, by identity. */
    private var rinPictures: Map<String, Bitmap> = emptyMap()

    private fun fillRinNumberRows(box: LinearLayout, accounts: List<Pair<String, String>>, level: Int) {
        box.removeAllViews()
        val density = resources.displayMetrics.density
        val refill = { fillRinNumberRows(box, accounts, level) }

        for ((identity, name) in accounts) {
            val number = SkAccountNumbers.numberOf(this, identity)
            val row = ItemSkChoiceBinding.inflate(LayoutInflater.from(this), box, false)
            row.skChoiceLabel.text = name
            row.skChoiceLabel.setTextColor(SkRoles.color(this, "page_label"))
            row.skChoiceLabel.setTypeface(null, Typeface.BOLD)
            // The account's picture before its name; an account without one gets the house 人
            // mark its tab shows, so a row looks like its tab either way.
            val pictureSize = (40 * density).toInt()
            (row.root as ViewGroup).addView(
                ImageView(this).apply {
                    val picture = rinPictures[identity]
                    if (picture != null) {
                        setImageBitmap(picture)
                    } else {
                        setImageResource(R.drawable.ic_unknown_contact)
                    }
                    layoutParams = LinearLayout.LayoutParams(pictureSize, pictureSize).apply {
                        marginEnd = (12 * density).toInt()
                    }
                },
                0,
            )
            row.root.setPadding(
                row.root.paddingLeft,
                (5 * density).toInt(),
                row.root.paddingRight,
                (5 * density).toInt(),
            )

            if (number == null) {
                row.skChoiceValue.text = getString(R.string.sk_rin_number_none)
                row.skChoiceValue.setTextColor(SkRoles.color(this, "page_note"))
            } else {
                val (ground, text) = SkAccountNumbers.colors(this, number)
                row.skChoiceValue.text = "${SkAccountNumbers.MARK}$number"
                row.skChoiceValue.textSize = 18f
                row.skChoiceValue.setTypeface(null, Typeface.BOLD)
                row.skChoiceValue.setTextColor(text)
                row.skChoiceValue.includeFontPadding = false
                row.skChoiceValue.setPadding(
                    (12 * density).toInt(),
                    (5 * density).toInt(),
                    (12 * density).toInt(),
                    (5 * density).toInt(),
                )
                row.skChoiceValue.background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(ground)
                }
            }

            row.root.setOnClickListener {
                val takenBy = accounts.mapNotNull { (other, otherName) ->
                    if (other == identity) return@mapNotNull null
                    SkAccountNumbers.numberOf(this, other)?.let { it to otherName }
                }.toMap()
                SkRinNumberDialog(this, name, number, takenBy) { picked ->
                    if (!SkAccountNumbers.set(this, identity, picked)) {
                        SkFlash.show(this, R.string.sk_rin_number_taken)
                    }
                    refill()
                }
            }
            indentRow(row.root, level)
            box.addView(row.root)

            if (number != null) {
                val badge = "${SkAccountNumbers.MARK}$number"
                val (ground, text) = SkAccountNumbers.colors(this, number)
                addRinColorRow(box, getString(R.string.sk_rin_badge_ground, badge), ground, level + 1) { color ->
                    SkAccountNumbers.setGround(this, number, color)
                    refill()
                }
                addRinColorRow(box, getString(R.string.sk_rin_badge_text, badge), text, level + 1) { color ->
                    SkAccountNumbers.setText(this, number, color)
                    refill()
                }
            }
        }
    }

    /** A colour row for one side of a badge; "Default" in the picker hands back null. */
    private fun addRinColorRow(
        box: LinearLayout,
        label: String,
        color: Int,
        level: Int,
        onPick: (Int?) -> Unit,
    ) {
        val row = ItemSkColorBinding.inflate(LayoutInflater.from(this), box, false)
        row.skColorLabel.text = label
        rowIcon(row.skColorLabel, R.drawable.sk_ic_drop)
        row.skColorLabel.setTextColor(SkRoles.color(this, "page_label"))
        row.skColorPreview.background = swatch(color)
        row.root.setOnClickListener {
            SkColorPickerDialog(this, color, showDefault = true) { wasPositive, picked ->
                onPick(if (wasPositive) picked else null)
            }
        }
        indentRow(row.root, level)
        box.addView(row.root)
    }

    private fun indentRow(view: View, level: Int) {
        val base = (BASE_INDENT_DP * resources.displayMetrics.density).toInt()
        view.setPaddingRelative(
            base + level * indentStepPx,
            view.paddingTop,
            view.paddingEnd,
            view.paddingBottom,
        )
    }

    private fun tintSeekBar(seekBar: SeekBar, color: Int) {
        seekBar.progressTintList = ColorStateList.valueOf(color)
        seekBar.thumbTintList = ColorStateList.valueOf(color)
    }

    private fun swatch(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 6 * resources.displayMetrics.density
        setColor(color)
        setStroke(
            (1.5f * resources.displayMetrics.density).toInt(),
            SkRoles.color(this@SkUiActivity, "page_swatch_frame"),
        )
    }

    companion object {
        private const val BASE_INDENT_DP = 54
        private const val INDENT_STEP_DP = 18

        // Diagnostics: the note's size, the slider's reach, and where its export picker opens.
        private const val NOTE_TEXT_SP = 13f
        private const val PENDING_VALUE = "…"
        private const val MAX_KEEP_DAYS = 90
        private const val WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val EXPORT_START_DOCUMENT = "primary:tmp"

        // The pill: fully round, 3dp around the main action of a block, 1.25dp around every other,
        // ripple at 30% of the frame colour. The › promises a screen behind the row.
        private const val PILL_RADIUS_DP = 50f
        private const val PILL_FRAME_MAIN_DP = 3f
        private const val PILL_FRAME_DP = 1.25f
        private const val PILL_RIPPLE_ALPHA = 0x4D000000
        private const val CHEVRON = "\u203A"
        private const val CHEVRON_OPEN = "\u2304"

        // Sections: the heading's inset.
        private const val SECTION_INSET_DP = 36

        // The seven section colours, as role keys (SkRoles, area PAGE).
        private const val TONE_BASE = "section_base"
        private const val TONE_CONTROLS = "section_controls"
        private const val TONE_WORDS = "section_words"
        private const val TONE_FRAME = "section_frame"
        private const val TONE_REACH = "section_reach"
        private const val TONE_WARNING = "section_warning"
        private const val TONE_FLASH = "section_flash"

        // The dim-gold rule left of a row that carries an explanation.
        private const val RULE_WIDTH_DP = 2f
        private const val RULE_GAP_DP = 10f

        // The call-history sample: two days back, 15:36, an 11:01 call.
        private const val SAMPLE_DAYS_BACK = 2L
        private const val SAMPLE_HOUR = 15
        private const val SAMPLE_MINUTE = 36
        private const val SAMPLE_DURATION_SECS = 661
    }
}
