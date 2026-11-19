package org.linphone.shiroikuma

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import java.util.Locale
import org.linphone.R
import org.linphone.ui.main.history.model.CallLogModel

/**
 * shiroikuma-rindenwa fork — what a call was made through, opened by a tap on the 臨N badge of
 * its row in the call history. The sister dialer's SIM dialog, for a SIP call.
 *
 * The head is the badge drawn large beside the account the call went through — its user part on
 * one line that shrinks before it wraps, its domain beneath. Under a rule, in the order the call
 * happened, one line per fact its [SkCallStamp] holds, each with an icon coloured by what the
 * line is about. A fact that is not known is left out, never shown empty: a call from before the
 * stamps existed shows its head and its "Call" line and little more.
 *
 * Everything shown is the stamp's own — the badge in the colours it had that day, the account as
 * it was — whatever has been renumbered or repainted since.
 */
class SkCallStampDialog(private val activity: Activity, private val model: CallLogModel) {
    private companion object {
        const val BADGE_DP = 64
        const val ICON_DP = 20
        const val CAPTION_WIDTH_DP = 96
        const val NUMBER_MAX_SP = 22
        const val NUMBER_MIN_SP = 12
        const val MAX_QUALITY = 5f
    }

    private val density = activity.resources.displayMetrics.density
    private val accent = SkTheme.color(activity, SkSlot.ACCENT)
    private val background = SkTheme.color(activity, SkSlot.BACKGROUND)

    // The dim gold of notes, and the menus' darker accent for the rule.
    private val captionColor = blend(background, accent, 0.78f)
    private val ruleColor = blend(background, accent, 0.7f)
    private val valueColor = SkDenwaStyle.color("color.number") ?: SkTheme.color(activity, SkSlot.LIST_NUMBER)

    private val stamp = model.skStamp ?: SkCallStamp()
    private var dialog: AlertDialog? = null

    fun show() {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(this@SkCallStampDialog.background)
                setStroke(dp(2), accent)
            }
        }
        box.addView(head())
        box.addView(
            View(activity).apply { setBackgroundColor(ruleColor) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { bottomMargin = dp(8) },
        )
        addRows(box)
        box.addView(buttonBar())

        dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(box) })
            .create()
            .also {
                it.show()
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    // ---- head: the badge, the account, its domain ----

    private fun head(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(14))

        val (ground, text) = model.skBadgeColors(activity)
        addView(
            TextView(activity).apply {
                this.text = model.skBadge
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(text)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), 0, dp(4), 0)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 12, 30, 1, TypedValue.COMPLEX_UNIT_SP
                )
                this.background = GradientDrawable().apply {
                    cornerRadius = 10 * density
                    setColor(ground)
                }
            },
            LinearLayout.LayoutParams(dp(BADGE_DP), dp(BADGE_DP)).apply { marginEnd = dp(16) },
        )

        val (user, domain) = accountParts()
        val lines = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        lines.addView(
            TextView(activity).apply {
                this.text = user.ifEmpty { activity.getString(R.string.sk_stamp_account_unknown) }
                setTextColor(accent)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                maxLines = 1
                // One line whatever the width: the account shrinks before it breaks.
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, NUMBER_MIN_SP, NUMBER_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)),
        )
        if (domain.isNotEmpty()) {
            lines.addView(
                TextView(activity).apply {
                    this.text = domain
                    setTextColor(valueColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }
            )
        }
        addView(lines, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** `sip:user@domain` as its two halves; an identity without a user is all domain. */
    private fun accountParts(): Pair<String, String> {
        val bare = identity().substringAfter(':').substringBefore(';')
        return if ('@' in bare) bare.substringBefore('@') to bare.substringAfter('@') else bare to ""
    }

    private fun identity(): String = stamp.identity.ifEmpty { model.skIdentity }

    // ---- the lines ----

    private fun addRows(box: LinearLayout) {
        box.addView(
            row(icon(model.skArrowRes), directionColor(), R.string.sk_stamp_call, "${directionLabel()} · ${dateAndTime()}")
        )
        if (model.skIsCallback) {
            box.addView(
                row(icon(R.drawable.sk_call_callback), accent, R.string.sk_stamp_placed_as, getString(R.string.sk_stamp_callback))
            )
        }

        val answered = model.skConnected
        if (stamp.ringMs >= 0) {
            val rang = duration((stamp.ringMs / 1000L).toInt())
            val text = when {
                answered -> activity.getString(R.string.sk_stamp_rang_answered, rang)
                model.direction == SkCallLog.Direction.OUTGOING ->
                    activity.getString(R.string.sk_stamp_rang_unanswered, rang)
                else -> rang
            }
            box.addView(row(icon(R.drawable.clock), accent, R.string.sk_stamp_rang, text))
        }
        if (answered && model.skDurationSeconds > 0) {
            box.addView(
                row(icon(R.drawable.clock_countdown), role(SkSlot.MENU_CALL), R.string.sk_stamp_talked, duration(model.skDurationSeconds))
            )
        }
        endLabel()?.let {
            box.addView(row(icon(R.drawable.phone_disconnect), endColor(), R.string.sk_stamp_ended, it))
        }
        addCircumstances(box)
    }

    private fun addCircumstances(box: LinearLayout) {
        if (stamp.server.isNotEmpty() || stamp.transport.isNotEmpty()) {
            val value = listOf(stamp.server, stamp.transport).filter { it.isNotEmpty() }.joinToString(" · ")
            box.addView(row(icon(R.drawable.share_network), role(SkSlot.MENU_MESSAGE), R.string.sk_stamp_server, value))
        }
        if (stamp.codec.isNotEmpty()) {
            val rate = stamp.codecRate.takeIf { it > 0 }?.let {
                val khz = it / 1000f
                val number = if (khz == khz.toInt().toFloat()) khz.toInt().toString() else String.format(Locale.US, "%.1f", khz)
                activity.getString(R.string.sk_stamp_codec_rate, number)
            }
            box.addView(
                row(icon(R.drawable.waveform), accent, R.string.sk_stamp_codec, listOfNotNull(stamp.codec, rate).joinToString(" · "))
            )
        }
        encryptionLabel()?.let {
            val encrypted = stamp.encryption != SkCallStamp.ENC_NONE
            val glyph = if (encrypted) R.drawable.lock_simple else R.drawable.lock_simple_open
            val color = if (encrypted) role(SkSlot.MENU_CALL) else role(SkSlot.MENU_SHARE)
            box.addView(row(icon(glyph), color, R.string.sk_stamp_encryption, it))
        }
        networkLabel()?.let {
            box.addView(row(icon(R.drawable.globe_hemisphere_west), role(SkSlot.MENU_MESSAGE), R.string.sk_stamp_network, it))
        }
        routeLabel()?.let { (glyph, label) ->
            box.addView(row(icon(glyph), role(SkSlot.MENU_CONTACT), R.string.sk_stamp_audio, label))
        }
        if (stamp.quality >= 0f) {
            val glyph = when {
                stamp.quality >= 4f -> R.drawable.cell_signal_full
                stamp.quality >= 3f -> R.drawable.cell_signal_high
                stamp.quality >= 2f -> R.drawable.cell_signal_medium
                stamp.quality >= 1f -> R.drawable.cell_signal_low
                else -> R.drawable.cell_signal_none
            }
            val value = activity.getString(
                R.string.sk_stamp_quality_value,
                String.format(Locale.US, "%.1f", stamp.quality.coerceAtMost(MAX_QUALITY)),
            )
            box.addView(row(icon(glyph), accent, R.string.sk_stamp_quality, value))
        }
        if (model.skRecorded) {
            box.addView(
                row(icon(R.drawable.record_fill), role(SkSlot.MENU_DELETE), R.string.sk_stamp_recording, getString(R.string.sk_stamp_recorded))
            )
        }
        if (stamp.agent.isNotEmpty()) {
            box.addView(row(icon(R.drawable.info), accent, R.string.sk_stamp_agent, stamp.agent))
        }
    }

    private fun row(icon: Drawable?, tint: Int, captionRes: Int, value: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, dp(5))
        addView(
            ImageView(activity).apply {
                setImageDrawable(icon?.mutate())
                setColorFilter(tint)
            },
            LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(12) },
        )
        addView(
            TextView(activity).apply {
                setText(captionRes)
                setTextColor(captionColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
            LinearLayout.LayoutParams(dp(CAPTION_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            TextView(activity).apply {
                text = value
                setTextColor(valueColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun icon(res: Int): Drawable? = ContextCompat.getDrawable(activity, res)

    private fun role(slot: SkSlot): Int = SkTheme.color(activity, slot)

    private fun getString(res: Int): String = activity.getString(res)

    // ---- what each line says ----

    private fun directionLabel(): String = getString(
        when (model.direction) {
            SkCallLog.Direction.OUTGOING -> R.string.sk_stamp_outgoing
            SkCallLog.Direction.MISSED -> R.string.sk_stamp_missed
            SkCallLog.Direction.INCOMING -> R.string.sk_stamp_incoming
        }
    )

    /** The colours the row's own arrow has: the sister dialer's where it serves them. */
    private fun directionColor(): Int = when {
        model.direction == SkCallLog.Direction.MISSED ->
            SkDenwaStyle.color("color.missed") ?: role(SkSlot.CALL_DIR_MISSED)
        model.direction == SkCallLog.Direction.INCOMING ->
            SkDenwaStyle.color("color.incoming") ?: role(SkSlot.CALL_DIR_INCOMING)
        model.skUnanswered ->
            SkDenwaStyle.color("color.unanswered") ?: SkDenwaStyle.color("color.outgoing") ?: role(SkSlot.CALL_DIR_OUTGOING)
        else -> SkDenwaStyle.color("color.outgoing") ?: role(SkSlot.CALL_DIR_OUTGOING)
    }

    /** Always the date itself, never "Today": the dialog is about one moment. */
    private fun dateAndTime(): String {
        val millis = model.timestamp * 1000L
        val date = when (SkCallLog.dateFormat(activity)) {
            SkCallLog.DateFormatOption.IMPERIAL -> SkJapanese.imperialDate(millis)
            SkCallLog.DateFormatOption.COMMON_ERA -> SkJapanese.commonEraDate(millis)
            SkCallLog.DateFormatOption.SYSTEM ->
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))
        }
        return "$date ${SkCallLog.timeText(activity, model.timestamp)}"
    }

    private fun duration(seconds: Int): String = when (SkCallLog.durationFormat(activity)) {
        SkCallLog.DurationFormatOption.JAPANESE -> SkJapanese.duration(seconds)
        SkCallLog.DurationFormatOption.DIGITAL -> String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun endLabel(): String? {
        val label = when (stamp.end) {
            SkCallStamp.END_LOCAL -> R.string.sk_stamp_end_local
            SkCallStamp.END_REMOTE -> R.string.sk_stamp_end_remote
            SkCallStamp.END_CANCELLED -> R.string.sk_stamp_end_cancelled
            SkCallStamp.END_BUSY -> R.string.sk_call_status_busy
            SkCallStamp.END_DECLINED -> R.string.sk_stamp_end_declined
            SkCallStamp.END_NO_ANSWER -> R.string.sk_call_status_no_answer
            SkCallStamp.END_FAILED -> R.string.sk_call_status_failed
            SkCallStamp.END_MISSED -> R.string.sk_stamp_end_missed
            SkCallStamp.END_DECLINED_HERE -> R.string.sk_stamp_end_declined_here
            SkCallStamp.END_ELSEWHERE -> R.string.sk_stamp_end_elsewhere
            SkCallStamp.END_BLOCKED -> R.string.sk_stamp_end_blocked
            // A call nobody watched: the failure reason its row already shows, if it has one.
            else -> return model.skFailure.takeIf { it.isNotEmpty() }
        }.let(::getString)
        val sip = listOfNotNull(
            stamp.sipCode.takeIf { it > 0 }?.toString(),
            stamp.sipPhrase.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
        return if (sip.isEmpty()) label else "$label · $sip"
    }

    /** Green for a call that simply ended, the accent for one given up here, red for a failure. */
    private fun endColor(): Int = when (stamp.end) {
        SkCallStamp.END_LOCAL, SkCallStamp.END_REMOTE, SkCallStamp.END_ELSEWHERE -> role(SkSlot.MENU_CALL)
        SkCallStamp.END_CANCELLED, SkCallStamp.END_DECLINED_HERE -> accent
        else -> role(SkSlot.MENU_DELETE)
    }

    private fun encryptionLabel(): String? = when (stamp.encryption) {
        SkCallStamp.ENC_SRTP -> "SRTP"
        SkCallStamp.ENC_ZRTP -> "ZRTP"
        SkCallStamp.ENC_DTLS -> "DTLS-SRTP"
        SkCallStamp.ENC_NONE -> getString(R.string.sk_stamp_encryption_none)
        else -> null
    }

    private fun networkLabel(): String? {
        val kind = when (stamp.network) {
            SkCallStamp.NET_WIFI -> R.string.sk_stamp_network_wifi
            SkCallStamp.NET_MOBILE -> R.string.sk_stamp_network_mobile
            SkCallStamp.NET_ETHERNET -> R.string.sk_stamp_network_ethernet
            SkCallStamp.NET_OTHER -> R.string.sk_stamp_network_other
            else -> return null
        }.let(::getString)
        return listOfNotNull(
            kind,
            stamp.networkName.takeIf { it.isNotEmpty() },
            if (stamp.vpn) "VPN" else null,
        ).joinToString(" · ")
    }

    private fun routeLabel(): Pair<Int, String>? = when (stamp.route) {
        SkCallStamp.ROUTE_EARPIECE -> R.drawable.ear to R.string.sk_stamp_route_earpiece
        SkCallStamp.ROUTE_SPEAKER -> R.drawable.speaker_high to R.string.sk_stamp_route_speaker
        SkCallStamp.ROUTE_BLUETOOTH -> R.drawable.bluetooth to R.string.sk_stamp_route_bluetooth
        SkCallStamp.ROUTE_HEADSET -> R.drawable.headset to R.string.sk_stamp_route_headset
        SkCallStamp.ROUTE_HEARING_AID -> R.drawable.ear to R.string.sk_stamp_route_hearing_aid
        SkCallStamp.ROUTE_OTHER -> R.drawable.speaker_high to R.string.sk_stamp_route_other
        else -> null
    }?.let { (glyph, label) -> glyph to getString(label) }

    // ---- buttons ----

    private fun buttonBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(16), 0, 0)
        val address = identity().substringAfter(':').substringBefore(';')
        if (address.isNotEmpty()) {
            addView(
                pill(R.string.sk_stamp_copy, main = false) {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SIP address", address))
                }.also { (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(10) }
            )
        }
        addView(pill(R.string.sk_stamp_close, main = true) { dialog?.dismiss() })
    }

    /** The theme's pill: never filled; the main one told by its 3 dp frame and bold label. */
    private fun pill(labelRes: Int, main: Boolean, onClick: () -> Unit) = TextView(activity).apply {
        setText(labelRes)
        setTextColor(accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        if (main) setTypeface(typeface, Typeface.BOLD)
        background = SkMenus.pill(activity, accent, if (main) 3f else 1.25f)
        setPadding(dp(18), dp(9), dp(18), dp(9))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun dp(value: Int) = (value * density).toInt()

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return ((a + (b - a) * t).toInt() and 0xFF) shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}
