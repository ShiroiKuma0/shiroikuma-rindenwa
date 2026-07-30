package org.linphone.shiroikuma

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the Export/Import panel (Kōjiki flow, Arcanechat button bar).
 *
 * One panel serves both directions: a settable SAF export directory with a "last export" status
 * line, a select-all + per-category checkbox list (sub-options indented under their parent and
 * following its toggle), and a pill-button bar — Cancel alone on the left, Import and Export
 * together on the right. Failures are toasts and leave the panel open; success shows a
 * yellow-bordered info dialog whose acknowledgement closes the whole chain (info dialog → panel →
 * UI page).
 *
 * Material's dialog surface/tinting doesn't render the black-yellow look (olive surface,
 * tinted-away button backgrounds), so we own the whole surface: the window is transparent and the
 * bordered black box + pill buttons are hand-drawn (the Kōjiki approach).
 */
class SkEximportPanel(
    private val activity: SkUiActivity,
    private val pickDirectory: () -> Unit,
    private val createExportFile: (suggestedName: String) -> Unit,
    private val openImportFile: () -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private val accent = SkTheme.color(activity, SkSlot.ACCENT)
    private val background = SkTheme.color(activity, SkSlot.BACKGROUND)
    private val textColor = SkTheme.color(activity, SkSlot.TEXT)

    private val dialog: AlertDialog
    private lateinit var dirValue: TextView
    private lateinit var statusView: TextView
    private val catBoxes = LinkedHashMap<SkEximport.Cat, CheckBox>()
    private var pendingExportCats: Set<SkEximport.Cat> = emptySet()
    private var pendingImportCats: Set<SkEximport.Cat> = emptySet()
    private var pendingExportName: String = ""
    private var progressDialog: AlertDialog? = null
    private var progressCount: TextView? = null
    private var progressStage: TextView? = null
    private var workThread: Thread? = null

    @Volatile
    private var workCancelled = false

    /** Deletes the partial output file when an export is cancelled or fails. */
    @Volatile
    private var cancelCleanup: (() -> Unit)? = null

    init {
        dialog = AlertDialog.Builder(activity)
            .setView(buildContent())
            .create()
        dialog.setOnDismissListener { activity.onEximportPanelClosed() }
    }

    fun show() {
        dialog.show()
        // The bordered black box IS the surface — the window itself stays fully transparent.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        refreshStatus()
    }

    fun dismiss() = dialog.dismiss()

    // ------------------------------------------------------------- view

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun text(value: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    /** The hand-drawn dialog surface: black box, yellow 2dp border. */
    private fun borderedBox(cornerDp: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = cornerDp * density
        setColor(background)
        setStroke(dp(2), accent)
    }

    private fun buildContent(): View {
        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(14))
            background = borderedBox(8)
        }

        holder.addView(
            text(activity.getString(R.string.sk_eim_title), 18f, accent, bold = true).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        holder.addView(
            text(activity.getString(R.string.sk_eim_desc), 13f, textColor).apply {
                alpha = 0.85f
                setPadding(0, dp(8), 0, dp(12))
            },
        )

        // Directory box — its own bordered, tappable frame.
        val dirBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(this@SkEximportPanel.background)
                setStroke((1.5f * density).toInt(), accent)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { pickDirectory() }
        }
        dirBox.addView(
            text(activity.getString(R.string.sk_eim_dir), 12f, accent).apply { alpha = 0.8f },
        )
        dirValue = text("", 15f, textColor, bold = true).apply { setPadding(0, dp(2), 0, 0) }
        dirBox.addView(dirValue)
        holder.addView(dirBox)

        statusView = text("", 13f, textColor).apply { setPadding(0, dp(8), 0, 0) }
        holder.addView(statusView)

        holder.addView(divider())

        // Select all + the category list (one checkbox list serves export AND import).
        val selectAll = checkBox(activity.getString(R.string.sk_eim_select_all), bold = true)
        holder.addView(selectAll)
        for (cat in SkEximport.Cat.topLevel()) {
            val parentBox = checkBox(activity.getString(cat.labelRes))
            catBoxes[cat] = parentBox
            holder.addView(parentBox)

            val children = SkEximport.Cat.childrenOf(cat)
            for (child in children) {
                // Sub-options sit indented under their parent and follow its toggle.
                val childBox = checkBox(activity.getString(child.labelRes), indent = true)
                catBoxes[child] = childBox
                holder.addView(childBox)
            }
            if (children.isNotEmpty()) {
                parentBox.setOnCheckedChangeListener { _, checked ->
                    children.forEach { catBoxes[it]?.isChecked = checked }
                    syncSelectAll(selectAll)
                }
                children.forEach { child ->
                    catBoxes[child]?.setOnCheckedChangeListener { _, _ -> syncSelectAll(selectAll) }
                }
            } else {
                parentBox.setOnCheckedChangeListener { _, _ -> syncSelectAll(selectAll) }
            }
        }
        // Seeded from Cat.default — the same flag the automation contract's fourth field reports,
        // so this sheet and a picker drawn from LIST_CATEGORIES start from one answer.
        catBoxes.forEach { (cat, box) -> box.isChecked = cat.default }
        selectAll.isChecked = catBoxes.values.all { it.isChecked }
        bindSelectAll(selectAll)

        // Arcanechat button bar: Cancel alone on the left, Import + Export on the right.
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        bar.addView(pillButton(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        bar.addView(
            Space(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            },
        )
        bar.addView(pillButton(activity.getString(R.string.sk_eim_import)) { onImportClicked() })
        bar.addView(pillButton(activity.getString(R.string.sk_eim_export)) { onExportClicked() })
        holder.addView(bar)

        return ScrollView(activity).apply { addView(holder) }
    }

    private fun syncSelectAll(selectAll: CheckBox) {
        selectAll.setOnCheckedChangeListener(null)
        selectAll.isChecked = catBoxes.values.all { it.isChecked }
        bindSelectAll(selectAll)
    }

    private fun bindSelectAll(selectAll: CheckBox) {
        selectAll.setOnCheckedChangeListener { _, checked ->
            catBoxes.values.forEach { it.isChecked = checked }
        }
    }

    private fun checkBox(label: String, bold: Boolean = false, indent: Boolean = false): CheckBox =
        CheckBox(activity).apply {
            text = label
            textSize = 15f
            setTextColor(textColor)
            buttonTintList = ColorStateList.valueOf(accent)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(2), 0, dp(2))
            if (indent) {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(24) }
            }
        }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(accent)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(12)
            bottomMargin = dp(6)
        }
    }

    /**
     * Arcanechat pill as a plain TextView (no Button — Material tints button backgrounds away):
     * black fill, round accent border, accent text, accent ripple.
     */
    private fun pillButton(label: String, onClick: () -> Unit): TextView =
        AppCompatTextView(activity).apply {
            text = label
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            val shape = GradientDrawable().apply {
                setColor(this@SkEximportPanel.background)
                cornerRadius = 50 * density
                setStroke((1.5f * density).toInt(), accent)
            }
            background = RippleDrawable(
                ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
                shape,
                null,
            )
            setPadding(dp(20), dp(8), dp(20), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8) }
            setOnClickListener { onClick() }
        }

    // ------------------------------------------------------------- status

    /** The activity persisted a newly picked export directory — re-query the status lines. */
    fun onDirPicked() = refreshStatus()

    private fun refreshStatus() {
        val name = SkEximport.exportDir(activity)?.name
            ?: SkEximport.dirUri(activity)?.lastPathSegment
        dirValue.text = name ?: activity.getString(R.string.sk_eim_dir_unset)
        dirValue.setTextColor(if (name == null) WARN_COLOR else textColor)
        val (message, warn) = SkEximport.lastExportStatus(activity)
        statusView.text = message
        statusView.setTextColor(if (warn) WARN_COLOR else textColor)
        statusView.alpha = if (warn) 1f else 0.8f
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------- progress dialog

    /** Black-yellow live progress: title, a big n/total counter, the current category. */
    private fun showProgress(titleRes: Int) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(20), dp(26), dp(20))
            background = borderedBox(16)
        }
        box.addView(text(activity.getString(titleRes), 19f, accent, bold = true))
        progressCount = text("", 21f, accent, bold = true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(10), 0, dp(4))
        }
        box.addView(progressCount)
        progressStage = text("", 13f, textColor).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0.85f
        }
        box.addView(progressStage)

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }
        bar.addView(pillButton(activity.getString(R.string.sk_cancel)) { cancelWork() })
        box.addView(bar)

        progressDialog = AlertDialog.Builder(activity)
            .setView(box)
            .setCancelable(false)
            .create()
            .also {
                it.show()
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    private fun onWorkProgress(done: Int, total: Int, stage: String) {
        activity.runOnUiThread {
            progressCount?.text = activity.getString(R.string.sk_eim_progress, done, total)
            progressStage?.text = stage
        }
    }

    private fun dismissProgress() {
        progressDialog?.dismiss()
        progressDialog = null
        progressCount = null
        progressStage = null
    }

    /** Cancel the ongoing export/import: interrupt the worker; it cleans up and stays silent. */
    private fun cancelWork() {
        workCancelled = true
        workThread?.interrupt()
        dismissProgress()
    }

    private fun selectedCats(): Set<SkEximport.Cat> =
        catBoxes.filterValues { it.isChecked }.keys

    // ------------------------------------------------------------- export

    private fun onExportClicked() {
        val cats = selectedCats()
        if (cats.isEmpty()) {
            toast(activity.getString(R.string.sk_eim_none_selected))
            return
        }
        val dir = SkEximport.exportDir(activity)
        if (dir == null) {
            // No directory set: fall back to a SAF save-as with the generated filename.
            pendingExportCats = cats
            pendingExportName = SkEximport.exportFileName()
            createExportFile(pendingExportName)
            return
        }
        val name = SkEximport.exportFileName()
        runExport(cats, name) {
            val file = dir.createFile("application/zip", name)
                ?: throw java.io.IOException("cannot create $name")
            cancelCleanup = { runCatching { file.delete() } }
            activity.contentResolver.openOutputStream(file.uri)
                ?: throw java.io.IOException("cannot open $name")
        }
    }

    /** SAF save-as target picked (no-directory fallback). */
    fun onExportTarget(uri: Uri?) {
        if (uri == null) return
        val cats = pendingExportCats
        if (cats.isEmpty()) return
        val target = androidx.documentfile.provider.DocumentFile.fromSingleUri(activity, uri)
        val name = target?.name ?: pendingExportName
        runExport(cats, name) {
            cancelCleanup = { runCatching { target?.delete() } }
            activity.contentResolver.openOutputStream(uri)
                ?: throw java.io.IOException("cannot open $name")
        }
    }

    private fun runExport(
        cats: Set<SkEximport.Cat>,
        name: String,
        openStream: () -> java.io.OutputStream,
    ) {
        workCancelled = false
        cancelCleanup = null
        showProgress(R.string.sk_eim_exporting)
        workThread = Thread {
            try {
                val summary = openStream().use {
                    SkEximport.export(activity, cats, it, ::onWorkProgress)
                }
                cancelCleanup = null
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    dismissProgress()
                    refreshStatus()
                    showExportDone(name, summary)
                }
            } catch (e: Exception) {
                // Cancelled or failed: either way the partial output file is garbage.
                cancelCleanup?.invoke()
                cancelCleanup = null
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    dismissProgress()
                    refreshStatus()
                    if (!workCancelled && e !is InterruptedException) {
                        toast(activity.getString(R.string.sk_eim_export_fail, e.message ?: e.toString()))
                    }
                }
            }
        }.also { it.start() }
    }

    // ------------------------------------------------------------- import

    private fun onImportClicked() {
        val cats = selectedCats()
        if (cats.isEmpty()) {
            toast(activity.getString(R.string.sk_eim_none_selected))
            return
        }
        pendingImportCats = cats
        openImportFile()
    }

    /** Import source picked. */
    fun onImportSource(uri: Uri?) {
        if (uri == null) return
        val cats = pendingImportCats
        if (cats.isEmpty()) return
        workCancelled = false
        showProgress(R.string.sk_eim_importing)
        workThread = Thread {
            try {
                val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw java.io.IOException("cannot read the selected file")
                if (SkEximport.categoriesIn(bytes).isEmpty()) {
                    activity.runOnUiThread {
                        if (activity.isFinishing) return@runOnUiThread
                        dismissProgress()
                        toast(activity.getString(R.string.sk_eim_import_none))
                    }
                    return@Thread
                }
                val summary = SkEximport.import(activity, bytes, cats, ::onWorkProgress)
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    dismissProgress()
                    showImportDone(summary)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    dismissProgress()
                    if (!workCancelled && e !is InterruptedException) {
                        toast(activity.getString(R.string.sk_eim_import_fail, e.message ?: e.toString()))
                    }
                }
            }
        }.also { it.start() }
    }

    // ------------------------------------------------------------- info dialogs

    /**
     * The black-yellow info dialog: hand-drawn bordered box (yellow border), accent text, pill
     * buttons right-aligned, transparent window. Buttons receive the dialog so they can close the
     * chain.
     */
    private fun infoDialog(
        title: String,
        body: String,
        buttons: List<Pair<String, (AlertDialog) -> Unit>>,
    ) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = borderedBox(16)
        }
        box.addView(text(title, 19f, accent, bold = true))
        box.addView(text(body, 14f, accent).apply { setPadding(0, dp(10), 0, 0) })

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }
        val info = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(box) })
            .setCancelable(false)
            .create()
        for ((label, onClick) in buttons) {
            bar.addView(pillButton(label) { onClick(info) })
        }
        box.addView(bar)
        info.show()
        info.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    /** OK closes the whole chain: info dialog → panel → the UI settings page. */
    private fun showExportDone(name: String, summary: String) {
        infoDialog(
            activity.getString(R.string.sk_eim_export_done_title),
            activity.getString(R.string.sk_eim_export_done_body, name, summary),
            listOf(activity.getString(R.string.sk_ok) to { info: AlertDialog -> closeChain(info) }),
        )
    }

    /** "Later" closes the whole chain; "Restart now" restarts the app. */
    private fun showImportDone(summary: String) {
        infoDialog(
            activity.getString(R.string.sk_eim_import_done_title),
            activity.getString(R.string.sk_eim_import_done_body, summary),
            listOf(
                activity.getString(R.string.sk_eim_restart_later) to { info: AlertDialog ->
                    closeChain(info)
                },
                activity.getString(R.string.sk_eim_restart_now) to { _: AlertDialog ->
                    restartApp()
                },
            ),
        )
    }

    private fun closeChain(info: AlertDialog) {
        info.dismiss()
        dialog.dismiss()
        activity.finish()
    }

    private fun restartApp() {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        if (launch?.component != null) {
            activity.applicationContext.startActivity(Intent.makeRestartActivityTask(launch.component))
        }
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val WARN_COLOR = 0xFFFF5252.toInt()
    }
}
