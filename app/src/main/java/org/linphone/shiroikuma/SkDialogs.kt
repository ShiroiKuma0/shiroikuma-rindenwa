package org.linphone.shiroikuma

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the small black-yellow dialogs the UI page needs.
 *
 * Material's own dialogs tint their surface and button backgrounds, which destroys the
 * black-yellow look, so — exactly as in [SkEximportPanel] — the window stays transparent and the
 * bordered black box plus pill buttons are hand-drawn.
 */
private class SkDialogChrome(val activity: Activity) {
    val density = activity.resources.displayMetrics.density
    val accent = SkTheme.color(activity, SkSlot.ACCENT)
    val background = SkTheme.color(activity, SkSlot.BACKGROUND)
    val textColor = SkTheme.color(activity, SkSlot.TEXT)

    fun dp(value: Int): Int = (value * density).toInt()

    fun box(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(20), dp(22), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = 16 * density
            setColor(this@SkDialogChrome.background)
            setStroke(dp(2), accent)
        }
    }

    fun title(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 19f
        setTextColor(accent)
        setTypeface(typeface, Typeface.BOLD)
    }

    fun body(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 14f
        setTextColor(textColor)
        setPadding(0, dp(10), 0, 0)
    }

    fun pill(label: String, onClick: () -> Unit): TextView =
        AppCompatTextView(activity).apply {
            text = label
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
                GradientDrawable().apply {
                    setColor(this@SkDialogChrome.background)
                    cornerRadius = 50 * density
                    setStroke((1.5f * density).toInt(), accent)
                },
                null,
            )
            setPadding(dp(18), dp(8), dp(18), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8) }
            setOnClickListener { onClick() }
        }

    fun bar(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(14), 0, 0)
    }

    /** A quiet heading inside a dialog — "For everyone", "For 白い熊 爲右衞門". */
    fun section(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 13f
        setTextColor(accent)
        alpha = 0.7f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(14), 0, dp(4))
    }

    /** One tappable line of a list; a long press is the way to remove it. */
    fun line(value: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): TextView =
        TextView(activity).apply {
            text = value
            textSize = 16f
            setTextColor(textColor)
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(9), dp(4), dp(9))
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        }

    /** A single-line text field in the house look: black box, yellow border, yellow ink. */
    fun field(hint: String): EditText = EditText(activity).apply {
        this.hint = hint
        textSize = 16f
        setTextColor(textColor)
        setHintTextColor((textColor and 0x00FFFFFF) or 0x66000000)
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        background = GradientDrawable().apply {
            cornerRadius = 10 * density
            setColor(this@SkDialogChrome.background)
            setStroke((1.5f * density).toInt(), accent)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(14) }
    }

    fun show(content: LinearLayout): AlertDialog =
        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(content) })
            .create()
            .also {
                it.show()
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
}

/** A yes/no confirmation in the house look; [onConfirm] runs only on the positive pill. */
class SkConfirmDialog(
    activity: Activity,
    title: String,
    message: String,
    confirmLabel: String? = null,
    onConfirm: () -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        box.addView(chrome.body(message))
        val bar = chrome.bar()
        lateinit var dialog: AlertDialog
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        bar.addView(
            chrome.pill(confirmLabel ?: activity.getString(R.string.sk_ok)) {
                dialog.dismiss()
                onConfirm()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
    }
}

/** A single-choice list in the house look — used for the per-slot font weight. */
class SkChoiceDialog(
    activity: Activity,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (index: Int) -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        lateinit var dialog: AlertDialog
        options.forEachIndexed { index, label ->
            box.addView(
                TextView(activity).apply {
                    text = if (index == selectedIndex) "• $label" else label
                    textSize = 16f
                    setTextColor(if (index == selectedIndex) chrome.accent else chrome.textColor)
                    isClickable = true
                    isFocusable = true
                    setPadding(chrome.dp(4), chrome.dp(9), chrome.dp(4), chrome.dp(9))
                    setOnClickListener {
                        dialog.dismiss()
                        onPick(index)
                    }
                },
            )
        }
        val bar = chrome.bar()
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        box.addView(bar)
        dialog = chrome.show(box)
    }
}

/**
 * shiroikuma fork — the picker behind the Message button, on the incoming-call panel and on a
 * missed call in the history alike.
 *
 * It offers this caller's own lines first, then the ones kept for everyone, then an empty field
 * for something new. Tapping a line hands it straight back: nothing is confirmed, because
 * 白い熊 tapping a line IS the decision (白い熊, 2026-11-02). A long press removes a stored line,
 * which is the one destructive thing here and so does ask.
 *
 * Whether the line was typed rather than picked travels back with it, because only a typed one is
 * worth asking about keeping — and that question comes *after* the send, never before it.
 *
 * Keeping the lists in order is not this dialog's job; that is the 臨電話 UI page, as in the
 * sister dialer and the sister messenger. See [SkDeclineMessages].
 */
class SkDeclineMessageDialog(
    private val activity: Activity,
    private val callerName: String,
    private val callerAddress: String,
    private val sendingLine: String,
    private val onPick: (message: String, typed: Boolean) -> Unit,
) {
    fun show() {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_call_decline_title)))
        if (sendingLine.isNotEmpty()) {
            // Which of 白い熊's lines the SMS will leave from — the DID the call came in on.
            box.addView(
                chrome.body(activity.getString(R.string.sk_call_decline_from_line, sendingLine)),
            )
        }

        lateinit var dialog: AlertDialog

        fun lines(messages: List<String>, remove: (String) -> Unit) {
            for (message in messages) {
                box.addView(
                    chrome.line(
                        message,
                        onClick = {
                            dialog.dismiss()
                            onPick(message, false)
                        },
                        onLongClick = {
                            dialog.dismiss()
                            confirmRemoval(message) { remove(message) }
                        },
                    ),
                )
            }
        }

        val forCaller = SkDeclineMessages.forCaller(activity, callerAddress)
        if (forCaller.isNotEmpty()) {
            box.addView(
                chrome.section(
                    activity.getString(R.string.sk_call_decline_for_contact, callerName),
                ),
            )
            lines(forCaller) { message ->
                SkDeclineMessages.setForCaller(
                    activity,
                    callerAddress,
                    SkDeclineMessages.forCaller(activity, callerAddress).filterNot { it == message },
                )
            }
        }

        val general = SkDeclineMessages.general(activity)
        if (general.isNotEmpty()) {
            box.addView(chrome.section(activity.getString(R.string.sk_call_decline_general)))
            lines(general) { message ->
                SkDeclineMessages.setGeneral(
                    activity,
                    SkDeclineMessages.general(activity).filterNot { it == message },
                )
            }
        }

        val field = chrome.field(activity.getString(R.string.sk_call_decline_hint))
        box.addView(field)

        val bar = chrome.bar()
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_call_decline_send)) {
                val typed = field.text.toString().trim()
                if (typed.isEmpty()) return@pill
                dialog.dismiss()
                onPick(typed, true)
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
    }

    private fun confirmRemoval(message: String, remove: () -> Unit) {
        SkConfirmDialog(
            activity,
            activity.getString(R.string.sk_call_decline_title),
            message,
            activity.getString(R.string.sk_delete),
        ) {
            remove()
            show()
        }
    }
}

/**
 * shiroikuma fork — "keep this one?", asked once the reply is already on its way.
 *
 * The send happens first and this only decides whether the line is worth having again, so every
 * answer — including dismissing the dialog — leaves the message sent. [onDone] always runs: on the
 * call screen it is what releases the hold keeping that screen alive, so it must not be skippable.
 */
class SkKeepMessageDialog(
    private val activity: Activity,
    private val callerAddress: String,
    private val message: String,
    private val onDone: () -> Unit,
) {
    fun show() {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_call_decline_save_title)))
        box.addView(chrome.body(message))

        lateinit var dialog: AlertDialog
        var answered = false

        fun answer(store: () -> Unit) {
            answered = true
            store()
            dialog.dismiss()
            onDone()
        }

        // Three labelled choices do not fit side by side, so this one bar stacks instead.
        val bar = chrome.bar()
        bar.orientation = LinearLayout.VERTICAL

        fun stacked(pill: TextView): TextView = pill.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = chrome.dp(8)
        }
        bar.addView(
            stacked(chrome.pill(activity.getString(R.string.sk_call_decline_save_no)) { answer { } }),
        )
        if (callerAddress.isNotEmpty()) {
            bar.addView(
                stacked(
                    chrome.pill(activity.getString(R.string.sk_call_decline_save_contact)) {
                        answer { SkDeclineMessages.addForCaller(activity, callerAddress, message) }
                    },
                ),
            )
        }
        bar.addView(
            stacked(
                chrome.pill(activity.getString(R.string.sk_call_decline_save_everyone)) {
                    answer { SkDeclineMessages.addToGeneral(activity, message) }
                },
            ),
        )
        box.addView(bar)
        dialog = chrome.show(box)
        // Swiped away or dismissed by a back press: nothing kept, but the hold still has to go.
        dialog.setOnDismissListener {
            if (!answered) {
                onDone()
            }
        }
    }
}

/**
 * shiroikuma fork — Messēji would not take the reply.
 *
 * The call has been declined by the time this appears, and nothing was queued anywhere, so this
 * dialog is the only place the failure exists. It carries the reason Messēji gave — most often a
 * DID missing from 携帯's table — and the text itself, so it can be sent by hand.
 */
class SkReplyFailedDialog(
    private val activity: Activity,
    private val reason: String,
    private val message: String,
    private val onDone: () -> Unit,
) {
    fun show() {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_call_decline_failed_title)))
        box.addView(chrome.body(reason))
        box.addView(chrome.body(message))

        lateinit var dialog: AlertDialog
        var answered = false
        val bar = chrome.bar()
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_ok)) {
                answered = true
                dialog.dismiss()
                onDone()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
        dialog.setOnDismissListener {
            if (!answered) {
                onDone()
            }
        }
    }
}

/**
 * shiroikuma fork — where the decline messages are actually kept in order: the 臨電話 UI page,
 * as in the sister dialer and the sister messenger.
 *
 * One dialog serves both lists. Opened without an [address] it edits the general list; opened with
 * one it edits that caller's own. A tap on a line offers what can be done to it — edit, move,
 * delete — rather than doing something immediately, because this is the screen where mistakes are
 * expensive and the call screen is the one where taps have to be cheap.
 */
class SkDeclineMessagesEditor(
    private val activity: Activity,
    private val address: String = "",
    private val title: String,
    private val onClosed: () -> Unit = { },
) {
    private fun load(): List<String> = if (address.isEmpty()) {
        SkDeclineMessages.general(activity)
    } else {
        SkDeclineMessages.forCaller(activity, address)
    }

    private fun save(messages: List<String>) {
        if (address.isEmpty()) {
            SkDeclineMessages.setGeneral(activity, messages)
        } else {
            SkDeclineMessages.setForCaller(activity, address, messages)
        }
    }

    fun show() {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))

        lateinit var dialog: AlertDialog
        val messages = load()
        if (messages.isEmpty()) {
            box.addView(chrome.body(activity.getString(R.string.sk_call_decline_editor_empty)))
        }
        messages.forEachIndexed { index, message ->
            box.addView(
                chrome.line(
                    message,
                    onClick = {
                        dialog.dismiss()
                        openActions(index, message, messages)
                    },
                ),
            )
        }

        val bar = chrome.bar()
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_call_decline_add)) {
            dialog.dismiss()
            edit(null)
        }
        )
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_ok)) {
                dialog.dismiss()
                onClosed()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
    }

    private fun openActions(index: Int, message: String, messages: List<String>) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        options.add(activity.getString(R.string.sk_call_decline_edit) to { edit(index) })
        if (index > 0) {
            options.add(activity.getString(R.string.sk_call_decline_move_up) to { move(index, -1) })
        }
        if (index < messages.size - 1) {
            options.add(
                activity.getString(R.string.sk_call_decline_move_down) to { move(index, 1) },
            )
        }
        options.add(
            activity.getString(R.string.sk_delete) to {
                save(load().filterIndexed { i, _ -> i != index })
                show()
            },
        )

        SkChoiceDialog(activity, message, options.map { it.first }, -1) { picked ->
            options[picked].second()
        }
    }

    private fun move(index: Int, by: Int) {
        val list = load().toMutableList()
        val target = index + by
        if (target in list.indices) {
            val moved = list.removeAt(index)
            list.add(target, moved)
            save(list)
        }
        show()
    }

    /** [index] null adds a new line; otherwise the existing one is replaced in place. */
    private fun edit(index: Int?) {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        val existing = index?.let { load().getOrNull(it) }.orEmpty()
        val field = chrome.field(activity.getString(R.string.sk_call_decline_hint))
        field.setText(existing)
        box.addView(field)

        lateinit var dialog: AlertDialog
        val bar = chrome.bar()
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_cancel)) {
                dialog.dismiss()
                show()
            },
        )
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_ok)) {
                val text = field.text.toString().trim()
                if (text.isNotEmpty()) {
                    val list = load().toMutableList()
                    if (index == null) list.add(text) else list[index] = text
                    save(list)
                }
                dialog.dismiss()
                show()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
    }
}
