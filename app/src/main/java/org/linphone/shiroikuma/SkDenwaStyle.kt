package org.linphone.shiroikuma

import android.view.ViewGroup
import android.text.style.ForegroundColorSpan
import android.text.Spanned
import android.text.SpannableString
import android.view.View
import java.io.File
import android.graphics.Typeface
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.util.TypedValue
import android.widget.ImageView
import androidx.core.view.doOnNextLayout
import android.text.TextUtils
import androidx.constraintlayout.widget.ConstraintLayout
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryListCellBinding

/**
 * shiroikuma-rindenwa fork — the call-history row follows the sister dialer's theme.
 *
 * 電話 (shiroikuma-denwa) publishes how its call log is painted through a small provider —
 * `content://shiroikuma.denwa.calllogstyle/style`, a cursor of `key` / `value` rows — which the
 * address book already reads so its own call list matches. This reads the same thing, so the SIM
 * history and the SIP history are two tabs of one list: a colour changed on 電話's theme screen
 * changes here too, with nothing to set twice. That includes the colours only this app draws —
 * the 臨N badge and the failure reason — which 電話 keeps under “臨電話 rows” for that reason.
 *
 * Where 電話 answers, it wins over this app's own UI page for the row; where it does not — not
 * installed, an older build that does not let this app in (1.11.1+104 is the first that does), a
 * key it does not serve — the row keeps what [SkStyler] gave it. Every key is optional.
 *
 * Followed: every colour, the fonts of each line (family, weight, size — an imported font file is
 * fetched from the same provider and kept in the cache), the base text size, the picture size, the
 * rules between records and under the day headline, and how the time, the duration and the date
 * are written. Not followed: `format_numbers` — the number on line 2 keeps this app's own grouping.
 */
object SkDenwaStyle {
    private const val TAG = "[SK Denwa Style]"

    private val AUTHORITIES = listOf(
        "shiroikuma.denwa.calllogstyle",
        "shiroikuma.denwa.debug.calllogstyle"
    )

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var values: Map<String, String> = emptyMap()

    /** The imported font files 電話 names, loaded. Built off the main thread, with [values]. */
    @Volatile
    private var typefaces: Map<String, Typeface> = emptyMap()

    private val FONT_SLOTS = listOf("name", "number", "subtitle", "duration", "date", "day_date", "country")

    /** Bumped when a reload brought something different, so a list on screen can redraw. */
    val version = MutableLiveData(0)

    fun reload(context: Context) {
        val app = context.applicationContext
        executor.execute {
            val read = try {
                read(app)
            } catch (e: Exception) {
                Log.w("$TAG Reading the dialer's style failed: $e")
                null
            } ?: emptyMap()
            if (read != values) {
                typefaces = loadTypefaces(app, read)
                values = read
                version.postValue((version.value ?: 0) + 1)
            }
        }
    }

    private fun read(context: Context): Map<String, String>? {
        for (authority in AUTHORITIES) {
            val cursor = context.contentResolver.query(
                Uri.parse("content://$authority/style"),
                null,
                null,
                null,
                null
            ) ?: continue
            cursor.use {
                val key = it.getColumnIndex("key")
                val value = it.getColumnIndex("value")
                if (key < 0 || value < 0) return null
                val map = HashMap<String, String>()
                while (it.moveToNext()) {
                    map[it.getString(key) ?: continue] = it.getString(value).orEmpty()
                }
                return map
            }
        }
        return null
    }

    /**
     * Fetch the font files [read] names — `content://<authority>/font/<file name>` — into this
     * app's cache and load them. A file that cannot be had simply leaves its line on the family
     * it already has.
     */
    private fun loadTypefaces(context: Context, read: Map<String, String>): Map<String, Typeface> {
        val names = FONT_SLOTS.mapNotNull { read["font.$it.family"] }
            .filter { it.isNotEmpty() && !it.startsWith("@") }
            .toSet()
        if (names.isEmpty()) return emptyMap()

        val dir = File(context.cacheDir, "denwa-fonts").apply { mkdirs() }
        val loaded = HashMap<String, Typeface>()
        for (name in names) {
            if (name.contains('/') || name.contains("..")) continue
            for (authority in AUTHORITIES) {
                try {
                    val uri = Uri.parse("content://$authority/font").buildUpon().appendPath(name).build()
                    val file = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } ?: continue
                    loaded[name] = Typeface.createFromFile(file)
                    break
                } catch (e: Exception) {
                    // Not this authority, or not a font: try the next, then give up on the name.
                }
            }
        }
        return loaded
    }

    /** True once 電話 has answered — its values then win for the call history. */
    val active: Boolean
        get() = values.isNotEmpty()

    /** An ordinal 電話 serves under [key] (`time_format`, `duration_format`), or null. */
    fun ordinal(key: String): Int? = values[key]?.toIntOrNull()

    /** A flag 電話 serves under [key] (`imperial_date`), or null when it says nothing. */
    fun flag(key: String): Boolean? = values[key]?.let { it == "1" }

    /** A thickness or size 電話 serves under [key], in pixels, or null. */
    fun dpPx(context: Context, key: String): Int? = values[key]?.toFloatOrNull()?.let {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, it, context.resources.displayMetrics).toInt()
    }

    /** The day headline's text size in pixels: its own, or the base size times 電話's scale. */
    fun dayHeaderSizePx(context: Context): Float? {
        sizeSp("day_date")?.let {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, context.resources.displayMetrics)
        }
        val base = values["text_size_px"]?.toFloatOrNull()?.takeIf { it > 0f } ?: return null
        val scale = values["day_header_scale"]?.toFloatOrNull()?.takeIf { it > 0f } ?: return null
        return base * scale
    }

    /** The typeface 電話 sets for [slot], or null when it sets neither a family nor a weight. */
    fun typeface(slot: String, bold: Boolean = false): Typeface? {
        val family = values["font.$slot.family"].orEmpty()
        val weight = values["font.$slot.weight"]?.toIntOrNull() ?: 0
        if (family.isEmpty() && weight <= 0) return null
        val base = when {
            family.isEmpty() -> Typeface.DEFAULT
            family == "@monospace" -> Typeface.MONOSPACE
            else -> typefaces[family] ?: Typeface.DEFAULT
        }
        return when {
            weight > 0 -> Typeface.create(base, weight.coerceIn(1, 1000), false)
            bold -> Typeface.create(base, Typeface.BOLD)
            else -> base
        }
    }

    /** A colour 電話 serves under [key] (a decimal ARGB int), or null. */
    fun color(key: String): Int? = values[key]?.toLongOrNull()?.toInt()

    private fun sizeSp(slot: String): Float? =
        values["font.$slot.size"]?.toFloatOrNull()?.takeIf { it > 0f }

    private fun paint(view: TextView?, colorKey: String, slot: String?, scale: Float = 1f) {
        view ?: return
        color(colorKey)?.let { view.setTextColor(it) }
        slot ?: return
        // The line's own size where 電話 sets one, its base row size (times [scale]) otherwise.
        val own = sizeSp(slot)
        val base = values["text_size_px"]?.toFloatOrNull()?.takeIf { it > 0f }
        when {
            own != null -> view.setTextSize(TypedValue.COMPLEX_UNIT_SP, own)
            base != null -> view.setTextSize(TypedValue.COMPLEX_UNIT_PX, base * scale)
        }
        typeface(slot)?.let { view.typeface = it }
    }

    private fun tint(view: ImageView?, colorKey: String) {
        view ?: return
        color(colorKey)?.let { view.imageTintList = ColorStateList.valueOf(it) }
    }

    /**
     * The picture is a square 90 % as tall as the whole row — all three lines — and not a fixed
     * size (白い熊, 2026-11-17, in the dialer's chat; the same in both apps).
     *
     * The row's height is worked out from its three lines and its padding, never from the row as
     * laid out: a picture taller than the lines is itself what stretches the row, and sizing it
     * from that height would only ever make it grow.
     */
    fun fitPicture(row: HistoryListCellBinding) {
        val root = row.root
        val fit = Runnable {
            val lines = listOf(row.callLogDisplayName, row.numberLabel, row.dateTime)
            if (lines.any { it.height == 0 }) return@Runnable
            val text = lines.sumOf { line ->
                line.height + ((line.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0)
            }
            val size = ((text + root.paddingTop + root.paddingBottom) * 0.9f).toInt()
            val avatar = row.avatar.avatar
            val params = avatar.layoutParams
            if (size > 0 && (params.width != size || params.height != size)) {
                params.width = size
                params.height = size
                avatar.layoutParams = params
            }
        }
        if (root.isLaidOut && !root.isLayoutRequested) fit.run() else root.post(fit)
    }

    /**
     * The handset, the bubble and the ⋮ at the end of line 2 get their width written out: each
     * as wide as that line is tall, the ⋮ half of it.
     *
     * The layout asks for the same through a ratio, but a ratio is the weakest wish in the row:
     * on the narrow screen a long "<number> · <label>" took the width, the handset shrank to a
     * speck and the bubble to nothing (白い熊, 2026-11-20). A written width stands, and the
     * number is what gives way.
     */
    fun fitActions(row: HistoryListCellBinding) {
        val root = row.root
        // A recycled row starts over: [keepWholeNumber] may have taken these from it.
        row.numberLabel.ellipsize = TextUtils.TruncateAt.END
        row.numberLabel.textScaleX = 1f
        val fit = Runnable {
            val line = row.numberLabel.height
            if (line <= 0) return@Runnable
            var changed = false
            listOf(row.call to line, row.message to line, row.rowMenu to line / 2).forEach { (icon, width) ->
                val params = icon.layoutParams as? ConstraintLayout.LayoutParams ?: return@forEach
                if (params.width != width || params.dimensionRatio != null) {
                    params.width = width
                    params.dimensionRatio = null
                    icon.layoutParams = params
                    changed = true
                }
            }
            // The number is judged against the room the icons leave it, so after they are laid.
            if (changed) root.doOnNextLayout { root.post { keepWholeNumber(row) } } else keepWholeNumber(row)
        }
        if (root.isLaidOut && !root.isLayoutRequested) fit.run() else root.post(fit)
    }

    /**
     * Line 2 never cuts into the number (白い熊, 2026-11-20): on the narrow screen every row read
     * "+420-601-123-45…", one digit short, for the sake of an ellipsis wider than the digit.
     *
     * When "<number> · <label>" does not fit, the label goes and the number stands alone, with no
     * ellipsis. A number still too long for the room is drawn a little narrower — down to
     * [NUMBER_SQUEEZE], below which digits stop being readable and the end is cut off instead.
     */
    private fun keepWholeNumber(row: HistoryListCellBinding) {
        val label = row.numberLabel
        val layout = label.layout ?: return
        if (layout.lineCount == 0 || layout.getEllipsisCount(0) == 0) return
        val text = label.text ?: return
        val at = text.indexOf(" · ")
        val number = if (at >= 0) text.subSequence(0, at) else text
        val room = (label.width - label.paddingLeft - label.paddingRight).toFloat()
        val needed = label.paint.measureText(number, 0, number.length) / label.textScaleX
        label.ellipsize = null
        label.textScaleX = if (needed > room && needed > 0f) (room / needed).coerceAtLeast(NUMBER_SQUEEZE) else 1f
        label.text = number
    }

    private const val NUMBER_SQUEEZE = 0.85f

    /** Lay 電話's colours over a row [SkStyler] has just styled. A no-op when 電話 is silent. */
    fun applyToRow(row: HistoryListCellBinding, direction: SkCallLog.Direction?, unanswered: Boolean) {
        if (values.isEmpty()) return

        paint(row.callLogDisplayName, "color.name", "name")
        // Line 2 is drawn smaller than the name: 電話's number_scale (0.85 since 1.11.1+110).
        val numberScale = values["number_scale"]?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        paint(row.numberLabel, "color.number", "number", numberScale)
        paint(row.dateTime, "color.date", "date")
        // The duration and its dot have a slot of their own since 1.11.1+110; before that they
        // shared the subtitle's.
        val durationColor = if (color("color.duration") != null) "color.duration" else "color.subtitle"
        val durationSlot = if (values.keys.any { it.startsWith("font.duration.") }) "duration" else "subtitle"
        paint(row.durationSeparator, durationColor, durationSlot)
        paint(row.duration, durationColor, durationSlot)
        paint(row.skCountry, "color.country", "country")
        paint(row.skFailure, if (color("color.failure") != null) "color.failure" else "color.missed", null)
        // The failure reason stands where the duration would, at the duration's size.
        row.skFailure.setTextSize(TypedValue.COMPLEX_UNIT_PX, row.duration.textSize)
        // The badge is as tall as the text of its line, as 電話's SIM badge is.
        row.skBadge.setTextSize(TypedValue.COMPLEX_UNIT_PX, row.dateTime.textSize * 0.85f)

        val context = row.root.context
        dpPx(context, "thickness.divider")?.let { thickness ->
            val rule = row.separator
            if (thickness <= 0) {
                rule.visibility = View.GONE
            } else {
                rule.visibility = View.VISIBLE
                rule.layoutParams = rule.layoutParams.apply { height = thickness }
                color("color.divider")?.let { rule.setBackgroundColor(it) }
            }
        }

        // Line 2 is "<number> · <label>": the number and its dot in one colour, the label after
        // it in another (color.number_label, since 電話 1.11.1+112).
        color("color.number_label")?.let { labelColor ->
            val text = row.numberLabel.text?.toString().orEmpty()
            val at = text.indexOf(" · ")
            if (at >= 0) {
                row.numberLabel.text = SpannableString(text).apply {
                    setSpan(
                        ForegroundColorSpan(labelColor),
                        at + 3,
                        text.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        tint(row.rowMenu, "color.name")
        tint(row.message, "color.action_message")
        tint(row.call, "color.action_call")
        // The arrow, and with it the time: 電話 colours both alike for a call that was missed,
        // for one placed and answered, and — its own key since 1.11.1+109 — for one placed and
        // not answered. An incoming call that was taken has its arrow's colour for the arrow only.
        val both = when {
            direction == SkCallLog.Direction.MISSED -> "color.missed"
            direction == SkCallLog.Direction.OUTGOING && unanswered && color("color.unanswered") != null ->
                "color.unanswered"
            direction == SkCallLog.Direction.OUTGOING -> "color.outgoing"
            else -> null
        }
        if (both != null) {
            tint(row.callStatus, both)
            color(both)?.let { row.dateTime.setTextColor(it) }
        } else if (direction == SkCallLog.Direction.INCOMING) {
            tint(row.callStatus, "color.incoming")
            // A call that was taken has no colour of its own to give its time, and 電話's plain
            // date colour can be anything — white, on 2026-11-19. The time then reads as the
            // duration standing right after it does, so the line is one colour (白い熊: yellow).
            // Read off the duration's own view, not looked up again: whichever of the two
            // themes painted it, that is the colour the line has (+032 looked 電話's key up and
            // the time stayed white).
            row.dateTime.setTextColor(row.duration.currentTextColor)
        }
    }
}
