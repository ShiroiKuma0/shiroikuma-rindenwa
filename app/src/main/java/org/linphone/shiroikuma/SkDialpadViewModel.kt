package org.linphone.shiroikuma

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.ui.main.history.model.NumpadModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

/**
 * shiroikuma-rindenwa fork — the state behind the dial panel that slides up over the call history.
 *
 * **What it replaces.** Upstream's FAB navigated to a whole "new call" screen: its own search
 * field, its own contact list, a grey numpad in a sheet underneath. Leaving the history to dial a
 * number is backwards, because the number 白い熊 wants is nearly always one the history already
 * holds. So the panel is a sheet over the list instead, and the digits typed into it are pushed
 * straight into the list's own filter — the call you are looking for walks up to meet you while
 * you type it.
 *
 * **Why a ViewModel rather than a field on the fragment.** The digits have to survive a rotation,
 * and on the Mate XT a rotation is a thing that happens by unfolding. Scoped to the history
 * fragment, so closing Calls forgets the half-typed number, which is right.
 *
 * The panel's own [NumpadModel] is upstream's, unchanged: it already carries the DTMF tones, the
 * long-press rules (0 gives "+", 1 calls the voicemail box) and the system's
 * "play tones while dialling" setting. Only where its callbacks lead is ours.
 */
class SkDialpadViewModel
    @UiThread
    constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Sk Dialpad]"
    }

    /** What has been typed so far. The history list filters on exactly this string. */
    val digits = MutableLiveData<String>()

    /** Whether the sheet is up. Drives the FAB, which would otherwise sit on top of the panel. */
    val isVisible = MutableLiveData<Boolean>()

    /** Raised when [hide] or [show] is called, so the fragment can move the sheet behavior. */
    val visibilityRequestEvent = MutableLiveData<Event<Boolean>>()

    val numpadModel: NumpadModel

    init {
        digits.value = ""
        isVisible.value = false

        numpadModel = NumpadModel(
            false,
            { digit -> // onDigitClicked
                append(digit)
            },
            { // onVoicemailClicked — long press on 1
                coreContext.postOnCoreThread {
                    val voicemail = LinphoneUtils.getDefaultAccount()?.params?.voicemailAddress
                    if (voicemail != null) {
                        Log.i("$TAG Calling the voicemail box [${voicemail.asStringUriOnly()}]")
                        coreContext.startCall(voicemail)
                    } else {
                        Log.w("$TAG No voicemail address on the current account, nothing to call")
                    }
                }
            },
            { // onBackspaceClicked
                val current = digits.value.orEmpty()
                if (current.isNotEmpty()) {
                    set(current.substring(0, current.length - 1))
                }
            },
            { // onCallClicked
                call()
            },
            { // onTransferCallClicked — there is no call to transfer from the history screen
            },
            { // onClearClicked — long press on backspace
                set("")
            },
        )
    }

    @UiThread
    fun show() {
        isVisible.value = true
        visibilityRequestEvent.value = Event(true)
    }

    /**
     * Put the sheet away and give the history back in full. Clearing the digits is the point:
     * a filter left behind by a panel that is no longer on screen is a list that lies.
     */
    @UiThread
    fun hide() {
        set("")
        isVisible.value = false
        visibilityRequestEvent.value = Event(false)
    }

    /** Called by the fragment when the sheet is dragged shut rather than dismissed by a button. */
    @UiThread
    fun onHiddenBySheet() {
        if (isVisible.value == true) {
            set("")
            isVisible.value = false
        }
    }

    @UiThread
    fun call() {
        val typed = digits.value.orEmpty()
        if (typed.isEmpty()) {
            Log.w("$TAG Call pressed with nothing typed, ignoring it")
            return
        }

        coreContext.postOnCoreThread { core ->
            val address = core.interpretUrl(typed, LinphoneUtils.applyInternationalPrefix())
            if (address == null) {
                Log.e("$TAG Can't read [$typed] as a number or a SIP address")
                return@postOnCoreThread
            }
            Log.i("$TAG Calling [${address.asStringUriOnly()}]")
            coreContext.startAudioCall(address)
        }
    }

    @UiThread
    private fun append(digit: String) {
        set(digits.value.orEmpty() + digit)
    }

    @UiThread
    private fun set(value: String) {
        if (digits.value != value) {
            digits.value = value
        }
    }
}
