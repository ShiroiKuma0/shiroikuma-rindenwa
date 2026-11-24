package org.linphone.shiroikuma

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
    val accent = SkRoles.color(activity, "dialog_frame")
    val background = SkRoles.color(activity, "dialog_ground")
    val textColor = SkRoles.color(activity, "dialog_text")

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
        setTextColor(SkRoles.color(activity, "dialog_title"))
        setTypeface(typeface, Typeface.BOLD)
    }

    fun body(value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 14f
        setTextColor(textColor)
        setPadding(0, dp(10), 0, 0)
    }

    /**
     * The theme's pill, never filled. The [main] action of a dialog is told from the others by
     * weight alone: a 3 dp frame and a bold label against 1.25 dp and normal. [ink] is the accent
     * unless the pill is one that takes something away, which lends it red.
     */
    fun pill(label: String, main: Boolean = false, ink: Int? = null, onClick: () -> Unit): TextView =
        AppCompatTextView(activity).apply {
            text = label
            textSize = 14f
            if (main) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink ?: SkRoles.color(activity, "dialog_button_text"))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            // See-through, like every pill: the dialog's own ground shows inside the frame.
            background = SkMenus.pill(
                activity,
                ink ?: SkRoles.color(activity, "dialog_button_frame"),
                if (main) 3f else 1.25f,
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

    /**
     * One tappable row of a list, drawn as what it is: a full-width outlined pill with its icon at
     * the left, coloured by what the row does ([role], see [SkMenus]), a › when it [opens]
     * something and a check when it is the [checked] one of a choice. A long press is the way to
     * remove it. Plain text lines gave no sign that they could be pressed.
     */
    fun line(
        value: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        iconRes: Int = R.drawable.chat_teardrop_text,
        role: String = SkMenus.MESSAGE,
        opens: Boolean = false,
        checked: Boolean = false,
    ): TextView =
        AppCompatTextView(activity).apply {
            text = value
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
            SkMenus.style(this, role, opens, checked)
            (layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = 0
                marginEnd = 0
            }
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
    /** The confirmation takes something away: its pill is red, frame and label. */
    destructive: Boolean = false,
    onConfirm: () -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        box.addView(chrome.body(message))
        val ink = if (destructive) SkTheme.color(activity, SkSlot.MENU_DELETE) else null
        val bar = chrome.bar()
        lateinit var dialog: AlertDialog
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        bar.addView(
            chrome.pill(confirmLabel ?: activity.getString(R.string.sk_ok), main = true, ink = ink) {
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
            // A list of pure choices carries no icons; the chosen one carries the check.
            box.addView(
                chrome.line(
                    label,
                    onClick = {
                        dialog.dismiss()
                        onPick(index)
                    },
                    iconRes = 0,
                    role = SkMenus.VIEW,
                    checked = index == selectedIndex,
                ),
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
            chrome.pill(activity.getString(R.string.sk_call_decline_send), main = true) {
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
            destructive = true,
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
            chrome.pill(activity.getString(R.string.sk_ok), main = true) {
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
                    iconRes = R.drawable.pencil_simple,
                    opens = true,
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
            chrome.pill(activity.getString(R.string.sk_ok), main = true) {
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
            chrome.pill(activity.getString(R.string.sk_ok), main = true) {
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

/**
 * shiroikuma fork — choosing an account's 臨 number.
 *
 * The ten numbers are shown as what they will be: the badges themselves, each in the colours of
 * its number, in two rows of five. The one this account carries is ringed; one another account
 * carries is dimmed, with that account's name under it, and cannot be taken. It replaced a bare
 * list of eleven lines of text, which 白い熊 rightly called terrible (2026-11-17).
 */
class SkRinNumberDialog(
    activity: Activity,
    title: String,
    current: Int?,
    takenBy: Map<Int, String>,
    onPick: (number: Int?) -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(title))
        box.addView(
            chrome.body(activity.getString(R.string.sk_rin_number_explanation)).apply {
                setTextColor(SkRoles.color(activity, "dialog_note"))
                textSize = 13f
            },
        )

        lateinit var dialog: AlertDialog
        for (rowStart in listOf(0, 5)) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, chrome.dp(if (rowStart == 0) 16 else 6), 0, 0)
            }
            for (number in rowStart until rowStart + 5) {
                val owner = takenBy[number]
                val selected = number == current
                val (ground, text) = SkAccountNumbers.colors(activity, number)

                val chip = TextView(activity).apply {
                    this.text = "${SkAccountNumbers.MARK}$number"
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(text)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    background = GradientDrawable().apply {
                        cornerRadius = 8 * chrome.density
                        setColor(ground)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        chrome.dp(44),
                    )
                }
                // The ring round the chosen one: the main-action frame, 3 dp, standing clear of
                // the chip so the chip's own colours stay readable.
                val ring = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(chrome.dp(5), chrome.dp(5), chrome.dp(5), chrome.dp(5))
                    background = GradientDrawable().apply {
                        cornerRadius = 13 * chrome.density
                        setColor(Color.TRANSPARENT)
                        setStroke(chrome.dp(3), if (selected) chrome.accent else Color.TRANSPARENT)
                    }
                    addView(chip)
                }
                val caption = TextView(activity).apply {
                    this.text = owner.orEmpty()
                    textSize = 10f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setTextColor(SkRoles.color(activity, "dialog_note"))
                }
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(chrome.dp(2), 0, chrome.dp(2), 0)
                    addView(ring)
                    addView(caption)
                    if (owner != null && !selected) {
                        ring.alpha = 0.38f
                    } else {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            onPick(number)
                        }
                    }
                }
                row.addView(cell)
            }
            box.addView(row)
        }

        val bar = chrome.bar()
        if (current != null) {
            bar.addView(
                chrome.pill(activity.getString(R.string.sk_rin_number_clear)) {
                    dialog.dismiss()
                    onPick(null)
                },
            )
        }
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        box.addView(bar)
        dialog = chrome.show(box)
    }
}

/**
 * shiroikuma fork — “Block this caller?”, on the incoming-call screen and in the history alike.
 *
 * Two pills: Cancel in the theme's own look, and Block in red — outlined like every pill, the red
 * lent to its frame and its label. Nothing is blocked until the red one is touched.
 *
 * It can be taken down from outside ([dismiss]): the call screen it stands on goes away when the
 * caller gives up, and a dialog left behind on a finished activity leaks its window.
 */
class SkBlockConfirmDialog(
    activity: Activity,
    who: String,
    onBlock: () -> Unit,
) {
    private val dialog: AlertDialog

    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_block_confirm_title)))
        box.addView(chrome.body(activity.getString(R.string.sk_block_confirm_message, who)))
        val bar = chrome.bar()
        lateinit var shown: AlertDialog
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { shown.dismiss() })
        bar.addView(
            chrome.pill(
                activity.getString(R.string.sk_block),
                main = true,
                ink = SkTheme.color(activity, SkSlot.MENU_DELETE),
            ) {
                shown.dismiss()
                onBlock()
            },
        )
        box.addView(bar)
        shown = chrome.show(box)
        dialog = shown
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }
}

/**
 * shiroikuma fork — the blocked numbers, as a list that can be tended: the register behind
 * [SkBlocked], opened from the 臨電話 UI page.
 *
 * Newest first. A tap on a line offers to unblock it; “+ Add” takes a number or a SIP address
 * typed by hand, for a caller who has not rung yet.
 */
class SkBlockedNumbersDialog(
    private val activity: Activity,
    private val onClosed: () -> Unit = { },
) {
    fun show() {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_blocked_numbers_title)))

        lateinit var dialog: AlertDialog
        val entries = SkBlocked.entries(activity)
        if (entries.isEmpty()) {
            box.addView(chrome.body(activity.getString(R.string.sk_blocked_numbers_empty)))
        }
        for (entry in entries) {
            val label = if (entry.name.isNotEmpty() && entry.name != entry.shown) {
                "${entry.shown}\n${entry.name}"
            } else {
                entry.shown
            }
            box.addView(
                chrome.line(
                    label,
                    onClick = {
                        dialog.dismiss()
                        openActions(entry)
                    },
                    iconRes = R.drawable.sk_prohibit,
                    role = SkMenus.BLOCK,
                    opens = true,
                ),
            )
        }

        val bar = chrome.bar()
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_call_decline_add)) {
                dialog.dismiss()
                add()
            },
        )
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_ok), main = true) {
                dialog.dismiss()
                onClosed()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
        dialog.setOnCancelListener { onClosed() }
    }

    private fun openActions(entry: SkBlocked.Entry) {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(entry.shown))
        val facts = listOfNotNull(
            entry.name.takeIf { it.isNotEmpty() && it != entry.shown },
            entry.at.takeIf { it > 0 }?.let {
                activity.getString(
                    R.string.sk_blocked_numbers_since,
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(it)),
                )
            },
        )
        if (facts.isNotEmpty()) box.addView(chrome.body(facts.joinToString("\n")))

        lateinit var dialog: AlertDialog
        val bar = chrome.bar()
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_cancel)) {
                dialog.dismiss()
                show()
            },
        )
        bar.addView(
            chrome.pill(activity.getString(R.string.sk_unblock), main = true) {
                SkBlocked.remove(activity, entry.key)
                dialog.dismiss()
                show()
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
        dialog.setOnCancelListener { show() }
    }

    private fun add() {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_blocked_numbers_add_title)))
        val field = chrome.field(activity.getString(R.string.sk_blocked_numbers_add_hint))
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
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
            chrome.pill(
                activity.getString(R.string.sk_block),
                main = true,
                ink = SkTheme.color(activity, SkSlot.MENU_DELETE),
            ) {
                val typed = field.text.toString().trim()
                val key = SkBlocked.keyOf(typed)
                if (key == null) {
                    // Neither a number nor user@domain: say so on the spot and keep the field.
                    field.error = activity.getString(R.string.sk_blocked_numbers_add_invalid)
                } else {
                    SkBlocked.add(activity, key, typed)
                    dialog.dismiss()
                    show()
                }
            },
        )
        box.addView(bar)
        dialog = chrome.show(box)
        dialog.setOnCancelListener { show() }
    }
}

/**
 * shiroikuma fork — the App language picker, in the look settled on 2026-11-24: plain rows, and
 * only the current language in a pill (full width, 3 dp, its name bold). Each row carries the
 * selection ring, the language's name in that language with its region beneath in dim gold, and
 * the tag in dim-gold monospace in a column of its own at the right — also for a right-to-left
 * language. “System default” comes first, with no region and no tag. A 1 px line separates
 * rows, but never touches the current row's pill. Choosing a row applies it and closes the list.
 */
class SkLanguageDialog(
    activity: Activity,
    current: String,
    onPick: (tag: String) -> Unit,
) {
    init {
        val chrome = SkDialogChrome(activity)
        val box = chrome.box()
        box.addView(chrome.title(activity.getString(R.string.sk_section_language)))
        box.addView(android.view.View(activity), LinearLayout.LayoutParams(1, chrome.dp(8)))
        lateinit var dialog: AlertDialog
        val note = SkRoles.color(activity, "dialog_note")
        val divider = SkRoles.color(activity, "dialog_divider")
        val on = SkRoles.color(activity, "mark_on")
        val off = SkRoles.color(activity, "mark_off")
        val tags = listOf("") + SkLanguage.shippedTags(activity).sortedBy { SkLanguage.ownName(it).lowercase() }

        fun ring(chosen: Boolean) = android.view.View(activity).apply {
            val outline = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(chrome.dp(2), if (chosen) on else off)
            }
            background = if (chosen) {
                val dot = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(on)
                }
                android.graphics.drawable.LayerDrawable(arrayOf(outline, dot)).apply {
                    val inset = chrome.dp(5)
                    setLayerInset(1, inset, inset, inset, inset)
                }
            } else {
                outline
            }
            layoutParams = LinearLayout.LayoutParams(chrome.dp(18), chrome.dp(18)).apply { marginEnd = chrome.dp(12) }
        }

        tags.forEachIndexed { index, tag ->
            val chosen = tag == current
            val beforeChosen = tags.getOrNull(index + 1) == current
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // The tag column stays at the right whatever the direction of the language.
                layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
                setPadding(chrome.dp(12), chrome.dp(7), chrome.dp(14), chrome.dp(7))
                isClickable = true
                isFocusable = true
                if (chosen) background = SkMenus.pill(activity, SkRoles.color(activity, "dialog_button_frame"), 3f)
                setOnClickListener {
                    dialog.dismiss()
                    if (!chosen) onPick(tag)
                }
            }
            row.addView(ring(chosen))
            val names = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            names.addView(
                TextView(activity).apply {
                    text = if (tag.isEmpty()) activity.getString(R.string.sk_language_system) else SkLanguage.ownName(tag)
                    textSize = 16f
                    setTextColor(chrome.textColor)
                    if (chosen) setTypeface(typeface, Typeface.BOLD)
                },
            )
            val region = if (tag.isEmpty()) "" else SkLanguage.ownRegion(tag)
            if (region.isNotEmpty()) {
                names.addView(
                    TextView(activity).apply {
                        text = region
                        textSize = 12f
                        setTextColor(note)
                    },
                )
            }
            row.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (tag.isNotEmpty()) {
                row.addView(
                    TextView(activity).apply {
                        text = tag
                        textSize = 12f
                        typeface = Typeface.MONOSPACE
                        setTextColor(note)
                        gravity = Gravity.END
                    },
                )
            }
            box.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            if (index < tags.lastIndex && !chosen && !beforeChosen) {
                box.addView(
                    android.view.View(activity).apply { setBackgroundColor(divider) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = chrome.dp(42)
                        marginEnd = chrome.dp(14)
                    },
                )
            }
        }
        val bar = chrome.bar()
        bar.addView(chrome.pill(activity.getString(R.string.sk_cancel)) { dialog.dismiss() })
        box.addView(bar)
        dialog = chrome.show(box)
    }
}
