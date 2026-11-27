package org.linphone.shiroikuma

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — the small panel that announces a call which comes in during a call
 * in another app (白い熊, 2026-11-27).
 *
 * Such a call neither rings nor takes the screen ([SkRinger.isQuiet]). What says that it is
 * there is this: a card at the top of the display, over whatever app is in front — who calls,
 * the number, the line it rings on, and three pills: Hide, Decline, Answer. A touch on the card
 * itself opens the full call screen. It stands for as long as the call rings and goes by itself
 * when the call is answered, declined or given up.
 *
 * It is the one overlay window the app draws, on the "Display over other apps" grant the call
 * screen already needs ([SkRinger.canRaiseCallScreen]). Without the grant there is no panel, and
 * the call's notification heads up instead, as upstream's does.
 *
 * Built in code and coloured by role, like every surface of the fork's own: the dialog's ground
 * and frame, the call screen's name, number, line and pills.
 */
object SkIncomingPanel {
    private const val TAG = "[Sk Incoming Panel]"
    private const val MAX_WIDTH_DP = 460
    private const val SIDE_MARGIN_DP = 10
    private const val TOP_GAP_DP = 6

    /** What the card says. Gathered on the core thread, drawn on the main one. */
    private data class Party(
        val name: String,
        val number: String,
        val badge: String,
        val badgeGround: Int,
        val badgeText: Int,
        val account: String,
    )

    // All three are touched on the main thread only.
    private var view: View? = null
    private var shownCall: Call? = null
    private var nameView: AppCompatTextView? = null

    /** A name found online arrives while the call rings; the card takes it. */
    private val onlineIdListener: () -> Unit = {
        coreContext.postOnCoreThread {
            val call = shownCall
            if (call != null) {
                val party = partyOf(call)
                coreContext.postOnMainThread {
                    if (shownCall === call) nameView?.text = party.name
                }
            }
        }
    }

    /** Announce [call] in the panel. Nothing happens without the overlay grant. */
    @WorkerThread
    fun show(call: Call) {
        val context = coreContext.context
        if (!SkRinger.canRaiseCallScreen(context)) {
            Log.w("$TAG 'Display over other apps' isn't granted, the notification announces the call alone")
            return
        }
        val party = partyOf(call)
        coreContext.postOnMainThread {
            try {
                attach(context, call, party)
            } catch (e: Exception) {
                Log.e("$TAG Couldn't show the panel: $e")
                view = null
                shownCall = null
                nameView = null
            }
        }
    }

    /** Take the panel away — the one for [call], or with null whichever stands. */
    @AnyThread
    fun dismiss(call: Call?) {
        coreContext.postOnMainThread {
            if (call == null || shownCall === call) detach()
        }
    }

    @WorkerThread
    private fun partyOf(call: Call): Party {
        val context = coreContext.context
        val address = call.callLog.remoteAddress
        val user = address.username.orEmpty()
        val friend = coreContext.contactsManager.findContactByAddress(address)
        val callback = SkOdorikCallback.isCallback(call)
        var name = friend?.name.orEmpty().ifEmpty {
            SkNumbers.unknownCallerName(LinphoneUtils.getDisplayName(address), address.username)
        }
        if (friend == null && !callback) {
            SkOnlineId.nameOf(context, SkOnlineId.keyOf(user)).takeIf { it.isNotEmpty() }?.let { name = it }
        }
        name = SkOdorikCallback.label(call, name).orEmpty()

        val read = SkNumbers.format(user)
        val number = when {
            user.isEmpty() -> address.domain.orEmpty()
            read != user || user.none { it.isLetter() } -> read
            else -> "$user@${address.domain.orEmpty()}"
        }

        val identity = SkCallStamps.identityOf(call)
        val (ground, text) = SkAccountNumbers.colors(context, SkAccountNumbers.numberOf(context, identity))
        return Party(
            name = name,
            number = if (number == name) "" else number,
            badge = SkAccountNumbers.badgeOf(context, identity),
            badgeGround = ground,
            badgeText = text,
            account = SkCallStamps.accountNameOf(call),
        )
    }

    @UiThread
    private fun detach() {
        val old = view ?: return
        view = null
        shownCall = null
        nameView = null
        SkOnlineId.removeListener(onlineIdListener)
        try {
            (old.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(old)
        } catch (e: Exception) {
            Log.w("$TAG Couldn't remove the panel: $e")
        }
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    @UiThread
    private fun attach(context: Context, call: Call, party: Party) {
        detach()

        val resources = context.resources
        val density = resources.displayMetrics.density

        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        fun label(text: String, size: Float, role: String, bold: Boolean = false) =
            AppCompatTextView(context).apply {
                this.text = text
                textSize = size
                setTextColor(SkRoles.color(context, role))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(14f))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(SkRoles.color(context, "dialog_ground"))
                setStroke(dp(2f), SkRoles.color(context, "dialog_frame"))
            }
            isClickable = true
            setOnClickListener {
                detach()
                coreContext.showCallActivity()
            }
        }

        // What it is, and the line it rings on.
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            label(context.getString(R.string.call_audio_incoming), 13f, "call_party_status"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        top.addView(
            AppCompatTextView(context).apply {
                text = party.badge
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(party.badgeText)
                setPadding(dp(5f), dp(1f), dp(5f), dp(1f))
                background = GradientDrawable().apply {
                    cornerRadius = 4 * density
                    setColor(party.badgeGround)
                }
            },
        )
        if (party.account.isNotEmpty()) {
            top.addView(
                label(party.account, 13f, "call_account_label"),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6f) },
            )
        }
        card.addView(top)

        val name = label(party.name, 21f, "call_party_name", bold = true)
        card.addView(
            name,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4f) },
        )
        if (party.number.isNotEmpty()) {
            card.addView(label(party.number, 15f, "call_party_number"))
        }

        // Hide and Decline are the other actions: thin. Answer is the main one: thick.
        val pills = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        fun pill(textRes: Int, role: String, bold: Boolean, shape: () -> android.graphics.drawable.Drawable?, weight: Float, onTap: () -> Unit) {
            pills.addView(
                label(context.getString(textRes), 16f, role, bold).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10f), dp(9f), dp(10f), dp(9f))
                    background = shape()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onTap() }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
                    if (pills.childCount > 0) marginStart = dp(10f)
                },
            )
        }
        pill(
            R.string.sk_call_panel_hide,
            "dialog_button_text",
            false,
            {
                GradientDrawable().apply {
                    cornerRadius = 100 * density
                    setStroke(dp(1.25f), SkRoles.color(context, "dialog_button_frame"))
                }
            },
            0.8f,
        ) { detach() }
        pill(
            R.string.sk_call_decline,
            "sk_call_hangup",
            false,
            { SkShapes.build(context, R.drawable.sk_call_decline_pill) },
            1f,
        ) {
            detach()
            coreContext.postOnCoreThread {
                if (LinphoneUtils.isCallIncoming(call.state)) coreContext.terminateCall(call)
            }
        }
        pill(
            R.string.sk_call_answer,
            "call_control_label",
            true,
            { SkShapes.build(context, R.drawable.sk_call_answer_pill) },
            1.2f,
        ) {
            detach()
            coreContext.postOnCoreThread {
                if (LinphoneUtils.isCallIncoming(call.state)) coreContext.answerCall(call)
            }
        }
        card.addView(
            pills,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12f) },
        )

        // Laid out against the whole display, so the place under the status bar is ours to work
        // out: its height, and a little air.
        val barId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (barId != 0) resources.getDimensionPixelSize(barId) else dp(28f)
        val width =
            minOf(resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP.toFloat()), dp(MAX_WIDTH_DP.toFloat()))

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = bar + dp(TOP_GAP_DP.toFloat())
            title = "SkIncomingPanel"
        }

        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(card, params)
        view = card
        shownCall = call
        nameView = name
        SkOnlineId.addListener(onlineIdListener)
        Log.i("$TAG Panel shown for [${call.remoteAddress.asStringUriOnly()}]")
    }
}
