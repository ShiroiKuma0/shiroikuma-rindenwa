package org.linphone.shiroikuma

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.ZoneId
import org.linphone.R
import org.linphone.databinding.ActivitySkUiBinding
import org.linphone.databinding.ItemSkCallPreviewBinding
import org.linphone.databinding.ItemSkChoiceBinding
import org.linphone.databinding.ItemSkColorBinding
import org.linphone.databinding.ItemSkDimenBinding
import org.linphone.databinding.ItemSkPreviewBoxBinding
import org.linphone.databinding.ItemSkSectionBinding
import org.linphone.databinding.ItemSkSubgroupBinding
import org.linphone.databinding.ItemSkSwitchBinding
import org.linphone.databinding.ItemSkTextBinding
import org.linphone.databinding.ItemSkTokenBinding
import org.linphone.databinding.ItemSkValueBinding

/**
 * shiroikuma-rindenwa fork — the 白い熊 臨電話 UI page.
 *
 * Programmatically built in the kxkb page format: big bold text-wide underlined section headings
 * separated by 1px hairlines, deeply indented items (each sub-level a further full step), tight
 * rows, and a live preview for everything — colors (RGBA slider picker with recent-color boxes),
 * external fonts rendered in their own glyphs, weight/size sliders, and 0-capable border/roundness
 * sliders.
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

    private val openFontDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val slot = pendingFontSlot
            pendingFontSlot = null
            if (uri == null || slot == null) return@registerForActivityResult
            val imported = SkFonts.importFont(this, uri)
            if (imported == null) {
                Toast.makeText(this, R.string.sk_font_invalid, Toast.LENGTH_LONG).show()
            } else {
                SkTheme.setFontFamily(this, slot, imported)
                refreshPage()
            }
        }

    /** Same black-yellow overlay the rest of the app gets, so our own chrome matches it. */
    override fun getTheme(): android.content.res.Resources.Theme {
        val theme = super.getTheme()
        theme.applyStyle(R.style.Theme_SkBlackYellow, true)
        return theme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkUiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setTitle(R.string.sk_ui_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        SkStyler.apply(this)
        buildRows()
    }

    private fun refreshPage() {
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

        // ---- Export / Import — always first, per the Kōjiki page ----
        addSection(R.string.sk_section_eximport)
        addEximportRow(1)
        // The 保存復元 automation lives where backup lives — inside this section, below its rows.
        addAutomationSwitchRow(1)
        addAutomationTokenRow(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addAllFilesAccessRow(1)
        }

        addSection(SkSection.FOUNDATION)
        addColorRow(SkSlot.BACKGROUND, 1)
        addTextSlot(SkSlot.TEXT, 1)
        addTextSlot(SkSlot.TEXT_SECONDARY, 1)
        addColorRow(SkSlot.ACCENT, 1)

        addSection(SkSection.TOP_BAR)
        addColorRow(SkSlot.TOOLBAR_BACKGROUND, 1)
        addTextSlot(SkSlot.TOOLBAR_TITLE, 1)
        addColorRow(SkSlot.TOOLBAR_ICON, 1)

        addSection(SkSection.MAIN_SCREEN)
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

        addSection(SkSection.CALLS)
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

        addSection(SkSection.CONVERSATIONS)
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

        addSection(SkSection.CONTROLS)
        addColorRow(SkSlot.BUTTON_BACKGROUND, 1)
        addTextSlot(SkSlot.BUTTON_TEXT, 1)
        addColorRow(SkSlot.BUTTON_BORDER, 1)
        addDimenRow(SkDimen.BUTTON_BORDER_WIDTH, 1)
        addDimenRow(SkDimen.BUTTON_CORNER_RADIUS, 1)
        addButtonPreview(1)
    }

    private fun addSection(section: SkSection) = addSection(section.labelRes)

    private fun addSection(labelRes: Int) {
        val row = ItemSkSectionBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkTheme.color(this, SkSlot.ACCENT)
        row.skSectionLabel.setText(labelRes)
        row.skSectionLabel.setTextColor(accent)
        row.skSectionRule.setBackgroundColor(accent)
        row.skSectionSpacer.setBackgroundColor(accent)
        // The full-width hairline separates sections — the first one has nothing above it.
        if (binding.skHolder.childCount == 0) {
            row.skSectionSpacer.visibility = View.GONE
        }
        binding.skHolder.addView(row.root)
    }

    /** The subgroup layout carries its own kxkb indent (54dp) — no indentRow here. */
    private fun addSubgroup(labelRes: Int, @Suppress("UNUSED_PARAMETER") level: Int) {
        val row = ItemSkSubgroupBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkTheme.color(this, SkSlot.ACCENT)
        row.skSubgroupLabel.setText(labelRes)
        row.skSubgroupLabel.setTextColor(accent)
        row.skSubgroupRule.setBackgroundColor(accent)
        binding.skHolder.addView(row.root)
    }

    private fun addEximportRow(level: Int) {
        val row = ItemSkValueBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skValueTitle.setText(R.string.sk_section_eximport)
        row.skValueTitle.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skValueDesc.setText(R.string.sk_eim_row_desc)
        row.skValueDesc.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        // Queried on page open: the latest export in the settable directory. Red until it is set.
        val (status, warn) = SkEximport.lastExportStatus(this)
        row.skValueStatus.text = status
        row.skValueStatus.setTextColor(
            if (warn) EXIM_WARN_COLOR else SkTheme.color(this, SkSlot.TEXT_SECONDARY),
        )
        row.root.setOnClickListener { openEximport() }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
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
     * The 保存復元 master switch — OFF until 白い熊 turns it on; nothing in
     * [SkStateExportReceiver] is reachable before that.
     */
    private fun addAutomationSwitchRow(level: Int) {
        val row = ItemSkSwitchBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkTheme.color(this, SkSlot.ACCENT)
        row.skSwitchTitle.setText(R.string.sk_auto_title)
        row.skSwitchTitle.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skSwitchDesc.setText(R.string.sk_auto_desc)
        row.skSwitchDesc.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        row.skSwitchToggle.thumbTintList = ColorStateList.valueOf(accent)
        row.skSwitchToggle.trackTintList = ColorStateList.valueOf(accent)
        row.skSwitchToggle.isChecked = SkAutomation.enabled(this)
        row.skSwitchToggle.setOnCheckedChangeListener { _, checked ->
            SkAutomation.setEnabled(this, checked)
        }
        // The switch itself is not clickable — the whole row is its hit area.
        row.root.setOnClickListener { row.skSwitchToggle.toggle() }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    /** Tap to copy the full token; "Regenerate" replaces it after a warning. */
    private fun addAutomationTokenRow(level: Int) {
        val row = ItemSkTokenBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skTokenTitle.setText(R.string.sk_auto_token_title)
        row.skTokenTitle.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skTokenValue.setTextColor(SkTheme.color(this, SkSlot.ACCENT))
        row.skTokenValue.text = SkAutomation.abbreviate(SkAutomation.token(this))
        row.skTokenRegenerate.setText(R.string.sk_auto_token_regenerate)
        row.skTokenRegenerate.setTextColor(EXIM_WARN_COLOR)
        row.root.setOnClickListener {
            // Deliberately not toasting the value itself — the row abbreviates it for a reason.
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(getString(R.string.sk_auto_token_title), SkAutomation.token(this)),
            )
            Toast.makeText(this, R.string.sk_auto_token_copied, Toast.LENGTH_SHORT).show()
        }
        row.skTokenRegenerate.setOnClickListener {
            SkConfirmDialog(
                this,
                getString(R.string.sk_auto_token_regenerate),
                getString(R.string.sk_auto_token_regen_warning),
            ) {
                row.skTokenValue.text = SkAutomation.abbreviate(SkAutomation.regenerateToken(this))
                Toast.makeText(this, R.string.sk_auto_token_regenerated, Toast.LENGTH_LONG).show()
            }
        }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    /**
     * All-files access (API 30+): what lets an automation export write to the absolute directory
     * the caller names, instead of only the SAF directory picked above.
     */
    private fun addAllFilesAccessRow(level: Int) {
        val row = ItemSkValueBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val granted = Environment.isExternalStorageManager()
        row.skValueTitle.setText(R.string.sk_auto_allfiles_title)
        row.skValueTitle.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skValueDesc.setText(R.string.sk_auto_allfiles_desc)
        row.skValueDesc.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        row.skValueStatus.setText(
            if (granted) R.string.sk_auto_allfiles_granted else R.string.sk_auto_allfiles_needed,
        )
        row.skValueStatus.setTextColor(
            if (granted) SkTheme.color(this, SkSlot.TEXT_SECONDARY) else EXIM_WARN_COLOR,
        )
        row.root.setOnClickListener { openAllFilesAccessSettings() }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
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
                Toast.makeText(this, e2.message ?: e2.toString(), Toast.LENGTH_LONG).show()
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

    private fun addColorRow(slot: SkSlot, level: Int) {
        val row = ItemSkColorBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skColorLabel.setText(slot.labelRes)
        row.skColorLabel.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skColorPreview.background = swatch(SkTheme.color(this, slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    private fun addTextSlot(slot: SkSlot, level: Int) {
        val row = ItemSkTextBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val textColor = SkTheme.color(this, SkSlot.TEXT)
        val secondary = SkTheme.color(this, SkSlot.TEXT_SECONDARY)
        val accent = SkTheme.color(this, SkSlot.ACCENT)

        row.skTextLabel.setText(slot.labelRes)
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
        binding.skHolder.addView(row.root)
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
        onChange: (Boolean) -> Unit,
    ) {
        val row = ItemSkSwitchBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkTheme.color(this, SkSlot.ACCENT)
        row.skSwitchTitle.setText(titleRes)
        row.skSwitchTitle.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skSwitchDesc.setText(descRes)
        row.skSwitchDesc.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        row.skSwitchToggle.thumbTintList = ColorStateList.valueOf(accent)
        row.skSwitchToggle.trackTintList = ColorStateList.valueOf(accent)
        row.skSwitchToggle.isChecked = checked
        row.skSwitchToggle.setOnCheckedChangeListener { _, value ->
            onChange(value)
            updatePreviews()
        }
        row.root.setOnClickListener { row.skSwitchToggle.toggle() }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    /** A row naming a setting and the option currently picked for it; tapping opens the list. */
    private fun addChoiceRow(
        labelRes: Int,
        options: List<String>,
        selectedIndex: Int,
        level: Int,
        onPick: (Int) -> Unit,
    ) {
        val row = ItemSkChoiceBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skChoiceLabel.setText(labelRes)
        row.skChoiceLabel.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skChoiceValue.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        row.skChoiceValue.text = options.getOrElse(selectedIndex) { "" }
        row.root.setOnClickListener {
            SkChoiceDialog(this, getString(labelRes), options, selectedIndex) { picked ->
                onPick(picked)
                row.skChoiceValue.text = options.getOrElse(picked) { "" }
                updatePreviews()
            }
        }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    private fun addDimenRow(dimen: SkDimen, level: Int) {
        val row = ItemSkDimenBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skDimenLabel.setText(dimen.labelRes)
        row.skDimenLabel.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skDimenValue.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        tintSeekBar(row.skDimenSeekbar, SkTheme.color(this, SkSlot.ACCENT))
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
        binding.skHolder.addView(row.root)
    }

    // ------------------------------------------------------------------ live previews

    private fun addListPreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        listPreview = row.skPreviewBox
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
        updatePreviews()
    }

    /**
     * The call-history sample: a day headline and one record, drawn through the same two
     * [SkStyler] entry points the real list uses, so the page shows exactly what will land.
     */
    private fun addCallRowPreview(level: Int) {
        val row = ItemSkCallPreviewBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        callPreview = row
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
        updatePreviews()
    }

    private fun addBubblePreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        bubblePreview = row.skPreviewBox
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
        updatePreviews()
    }

    private fun addButtonPreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        buttonPreview = row.skPreviewBox
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
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
                    SkTheme.color(this@SkUiActivity, SkSlot.ACCENT),
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
            SkTheme.color(this@SkUiActivity, SkSlot.ACCENT),
        )
    }

    companion object {
        private const val BASE_INDENT_DP = 54
        private const val INDENT_STEP_DP = 18
        private const val EXIM_WARN_COLOR = 0xFFFF5252.toInt()

        // The call-history sample: two days back, 15:36, an 11:01 call.
        private const val SAMPLE_DAYS_BACK = 2L
        private const val SAMPLE_HOUR = 15
        private const val SAMPLE_MINUTE = 36
        private const val SAMPLE_DURATION_SECS = 661
    }
}
