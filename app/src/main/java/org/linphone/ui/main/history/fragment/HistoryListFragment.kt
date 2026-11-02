/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.history.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.Address
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryListFragmentBinding
import org.linphone.shiroikuma.SkDeclineMessageDialog
import org.linphone.shiroikuma.SkDeclineMessages
import org.linphone.shiroikuma.SkDialpadViewModel
import org.linphone.shiroikuma.SkKeepMessageDialog
import org.linphone.shiroikuma.SkReplyFailedDialog
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.fragment.AbstractMainFragment
import org.linphone.ui.main.history.adapter.HistoryListAdapter
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.ui.main.history.viewmodel.HistoryListViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

@UiThread
class HistoryListFragment : AbstractMainFragment() {
    companion object {
        private const val TAG = "[History List Fragment]"
    }

    private lateinit var binding: HistoryListFragmentBinding

    private lateinit var listViewModel: HistoryListViewModel

    /** shiroikuma fork: the dial panel that slides up over this list. See SkDialpadViewModel. */
    private lateinit var dialpadViewModel: SkDialpadViewModel

    private var dialpadBehavior: BottomSheetBehavior<View>? = null

    private lateinit var adapter: HistoryListAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private val numberOrAddressClickListener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            coreContext.postOnCoreThread {
                val address = model.address
                if (address != null) {
                    Log.i("$TAG Starting call to [${address.asStringUriOnly()}]")
                    coreContext.startAudioCall(address)
                }
            }
        }

        override fun onLongPress(model: ContactNumberOrAddressModel) { }
    }

    /**
     * shiroikuma fork: the dial panel is a hideable bottom sheet over the call history, and every
     * digit typed into it becomes the list's filter — so the number being dialled and the calls
     * already made to it are on screen at the same time.
     */
    private fun setUpDialpad() {
        val behavior = BottomSheetBehavior.from(binding.dialpadPanel.root)
        behavior.isHideable = true
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        // Dragged shut rather than dismissed by the chevron.
                        dialpadViewModel.onHiddenBySheet()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) { }
            }
        )
        dialpadBehavior = behavior

        dialpadViewModel.visibilityRequestEvent.observe(viewLifecycleOwner) {
            it.consume { visible ->
                behavior.state = if (visible) {
                    BottomSheetBehavior.STATE_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_HIDDEN
                }
            }
        }

        dialpadViewModel.digits.observe(viewLifecycleOwner) { digits ->
            listViewModel.applyFilter(digits)
        }

        // While the panel is up, back puts it away rather than leaving the Calls tab.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    dialpadViewModel.hide()
                }
            }.also { callback ->
                dialpadViewModel.isVisible.observe(viewLifecycleOwner) { visible ->
                    callback.isEnabled = visible
                }
            }
        )
    }

    /**
     * shiroikuma fork: hand a written reply to the sister messenger and say what came of it.
     * Nothing holds a screen up here — the list is still in front — so the keep question and any
     * refusal are plain dialogs over it.
     */
    private fun sendWrittenReply(
        target: SkDeclineMessages.ReplyTarget,
        message: String,
        typed: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SkDeclineMessages.send(requireContext(), target, message)
            }
            when (result) {
                is SkDeclineMessages.SendResult.Refused -> {
                    Log.e("$TAG The written reply was refused: [${result.reason}]")
                    SkReplyFailedDialog(requireActivity(), result.reason, message) { }.show()
                }
                is SkDeclineMessages.SendResult.Accepted -> {
                    if (typed &&
                        SkDeclineMessages.isNew(requireContext(), target.callerKey, message)
                    ) {
                        SkKeepMessageDialog(requireActivity(), target.callerKey, message) { }.show()
                    }
                }
            }
        }
    }

    override fun onDefaultAccountChanged() {
        Log.i(
            "$TAG Default account changed, updating avatar in top bar & re-computing call logs"
        )
        listViewModel.filter()
    }

    override fun onSlidingPaneClosed() {
        listViewModel.currentlyDisplayedItemId = ""
        adapter.resetSelection()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.startCallFragment ||
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment
        ) {
            // Holds fragment in place while new fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = HistoryListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HistoryListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[HistoryListViewModel::class.java]
        if (!listViewModel.keepCallsFilterOnViewRecreation) {
            listViewModel.showAllCalls()
        }
        listViewModel.keepCallsFilterOnViewRecreation = false

        dialpadViewModel = ViewModelProvider(this)[SkDialpadViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel
        binding.dialpad = dialpadViewModel
        observeToastEvents(listViewModel)

        setUpDialpad()

        binding.historyList.setHasFixedSize(true)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.historyList.addItemDecoration(headerItemDecoration)

        adapter.callLogLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = HistoryMenuDialogFragment(
                    model.friendExists,
                    { // onDismiss
                        adapter.resetActivated()
                    },
                    { // onAddToContact
                        val addressToAdd = model.displayedAddress
                        Log.i(
                            "$TAG Navigating to new contact with pre-filled value [$addressToAdd]"
                        )

                        sharedViewModel.sipAddressToAddToNewContact = addressToAdd
                        sharedViewModel.displayNameToSetToNewContact = model.avatarModel.contactName.orEmpty()
                        sharedViewModel.navigateToContactsEvent.value = Event(true)
                        sharedViewModel.showNewContactEvent.value = Event(true)
                    },
                    { // onGoToContact
                        val friendRefKey = model.friendRefKey
                        if (!friendRefKey.isNullOrEmpty()) {
                            Log.i("$TAG Navigating to contact with ref key [$friendRefKey]")

                            sharedViewModel.navigateToContactsEvent.value = Event(true)
                            sharedViewModel.showContactEvent.value = Event(friendRefKey)
                        } else {
                            Log.w(
                                "$TAG Can't navigate to existing friend, ref key is null or empty"
                            )
                        }
                    },
                    { // onCopyNumberOrAddressToClipboard
                        copyNumberOrAddressToClipboard(model.address)
                    },
                    { // onDeleteCallLog
                        showDeleteConfirmationDialog(model)
                    }
                )
                modalBottomSheet.show(parentFragmentManager, HistoryMenuDialogFragment.TAG)
                bottomSheetDialog = modalBottomSheet
            }
        }

        adapter.callLogClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val uri = model.id.orEmpty()
                Log.i("$TAG Show details for call log with ID [$uri]")
                listViewModel.currentlyDisplayedItemId = uri

                if (uri.isNotEmpty()) {
                    val navController = binding.historyNavContainer.findNavController()
                    val action =
                        HistoryFragmentDirections.actionGlobalHistoryFragment(uri)
                    navController.navigate(action)
                }
            }
        }

        // shiroikuma fork: write back to a missed call from the list. Same picker as the
        // incoming-call screen, without the declining — the call is long over.
        adapter.callLogReplyClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                coreContext.postOnCoreThread {
                    val target = SkDeclineMessages.targetOf(
                        model.skCallLog,
                        model.avatarModel.contactName.orEmpty()
                    )
                    coreContext.postOnMainThread {
                        if (target == null) {
                            Log.e("$TAG No number to write back to on that record")
                            (requireActivity() as GenericActivity).showRedToast(
                                getString(R.string.sk_call_decline_no_did),
                                R.drawable.warning_circle
                            )
                        } else {
                            SkDeclineMessageDialog(
                                requireActivity(),
                                target.name,
                                target.callerKey,
                                target.did
                            ) { message, typed ->
                                sendWrittenReply(target, message, typed)
                            }.show()
                        }
                    }
                }
            }
        }

        adapter.callLogCallBackClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                coreContext.postOnCoreThread { core ->
                    val conferenceInfo = core.findConferenceInformationFromUri(model.address)
                    if (conferenceInfo != null) {
                        Log.i(
                            "$TAG Going to waiting room for conference [${conferenceInfo.subject}]"
                        )
                        sharedViewModel.goToMeetingWaitingRoomEvent.postValue(
                            Event(model.address.asStringUriOnly())
                        )
                    } else {
                        Log.i("$TAG Starting call to [${model.address.asStringUriOnly()}]")
                        coreContext.startAudioCall(model.address)
                    }
                }
            }
        }

        adapter.callFriendClickedEvent.observe(viewLifecycleOwner) {
            it.consume { friend ->
                coreContext.postOnCoreThread {
                    val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
                    if (singleAvailableAddress != null) {
                        Log.i(
                            "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], using it"
                        )
                        coreContext.startAudioCall(singleAvailableAddress)
                    } else {
                        val list = friend.getListOfSipAddressesAndPhoneNumbers(numberOrAddressClickListener)
                        Log.i(
                            "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                        )
                        coreContext.postOnMainThread {
                            showNumbersOrAddressesDialog(list)
                        }
                    }
                }
            }
        }

        adapter.callAddressClickedEvent.observe(viewLifecycleOwner) {
            it.consume { address ->
                Log.i("$TAG Starting call to [${address.asStringUriOnly()}]")
                coreContext.startAudioCall(address)
            }
        }

        listViewModel.callLogs.observe(viewLifecycleOwner) {
            if (listViewModel.scrollToTopOnNextUpdate) {
                listViewModel.scrollToTopOnNextUpdate = false
                adapter.submitList(it) {
                    binding.historyList.scrollToPosition(0)
                }
            } else {
                adapter.submitList(it)
            }

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.historyList.adapter != adapter) {
                binding.historyList.adapter = adapter
            }

            coreContext.postOnMainThreadDelayed( {
                // Delay update to give the adapter enough time to update the list first
                if (listViewModel.currentlyDisplayedItemId.isNotEmpty()) {
                    val index = it.orEmpty().indexOfFirst { callLog ->
                        callLog.callLogModel?.id == listViewModel.currentlyDisplayedItemId
                    }
                    Log.i("$TAG Found call log with ID [${listViewModel.currentlyDisplayedItemId}] at index [$index]")
                    adapter.notifyItemHasBeenSelected(index)
                } else {
                    Log.i("$TAG No currently selected item ID")
                }
            }, 200)

            Log.i("$TAG Call logs ready with [${it.size}] items")
            listViewModel.fetchInProgress.value = false
        }

        listViewModel.historyInsertedEvent.observe(viewLifecycleOwner) {
            it.consume {
                // Scroll to top to display latest call log
                binding.historyList.scrollToPosition(0)
            }
        }

        listViewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG All call logs have been deleted")
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.call_history_deleted_toast),
                    R.drawable.check
                )
            }
        }

        sharedViewModel.forceRefreshCallLogsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Re-compute call log history")
                listViewModel.filter()
            }
        }

        sharedViewModel.goToMeetingWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                if (findNavController().currentDestination?.id == R.id.historyListFragment) {
                    Log.i("$TAG Navigating to meeting waiting room fragment with URI [$uri]")
                    val action =
                        HistoryListFragmentDirections.actionHistoryListFragmentToMeetingWaitingRoomFragment(
                            uri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        binding.setDeleteAllClickListener {
            showDeleteAllConfirmationDialog()
        }

        // shiroikuma fork: the FAB raises the dial panel over this list instead of navigating
        // to upstream's separate "new call" screen. Nothing is left behind, so there is nothing
        // to come back from.
        binding.setStartCallClickListener {
            dialpadViewModel.show()
        }

        // AbstractMainFragment related

        listViewModel.title.value = getString(R.string.bottom_navigation_calls_label)
        setViewModel(listViewModel)
        initViews(
            binding.slidingPaneLayout,
            binding.topBar,
            binding.bottomNavBar,
            R.id.historyListFragment
        )
    }

    override fun onDestroyView() {
        if (::listViewModel.isInitialized) {
            listViewModel.keepCallsFilterOnViewRecreation = requireActivity().isChangingConfigurations
        }

        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    // notifyDataSetChanged is the point below: every visible row has to re-read the settings.
    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()

        Log.i("$TAG Fragment is resumed, resetting missed calls count")
        sharedViewModel.resetMissedCallsCountEvent.value = Event(true)
        sharedViewModel.refreshDrawerMenuAccountsListEvent.value = Event(false)

        if (shouldRefreshDataInOnResume()) {
            Log.i("$TAG Keep app alive setting is enabled, refreshing view just in case")
            listViewModel.filter()
        }

        // shiroikuma fork: how a record reads — the day headlines, the time and duration formats,
        // the fonts and the colours — is all settable on the UI page, and the rows already on
        // screen were bound under the settings as they were then. Re-bind them, and let the
        // headline decoration measure itself again in case it has just been turned on or off.
        if (binding.historyList.adapter === adapter) {
            adapter.notifyDataSetChanged()
            binding.historyList.invalidateItemDecorations()
        }
    }

    private fun copyNumberOrAddressToClipboard(address: Address?) {
        if (address != null) {
            val username = address.username.orEmpty()
            if (username.isNotEmpty() && (username.startsWith("+") || username.isDigitsOnly())) {
                Log.i("$TAG Adding phone number [$username] into clipboard")
                if (AppUtils.copyToClipboard(requireContext(), AppUtils.getString(R.string.phone_number), username)) {
                    (requireActivity() as GenericActivity).showGreenToast(
                        getString(R.string.phone_number_copied_to_clipboard_toast),
                        R.drawable.check
                    )
                }
            } else {
                val sipUri = address.asStringUriOnly()
                Log.i("$TAG Adding SIP address [$sipUri] into clipboard")
                if (AppUtils.copyToClipboard(requireContext(), AppUtils.getString(R.string.sip_address), sipUri)) {
                    (requireActivity() as GenericActivity).showGreenToast(
                        getString(R.string.sip_address_copied_to_clipboard_toast),
                        R.drawable.check
                    )
                }
            }
        }
    }

    private fun showDeleteAllConfirmationDialog() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getRemoveAllCallLogsConfirmationDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG Removing all call entries from database")
                listViewModel.removeAllCallLogs()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(callLogModel: CallLogModel) {
        val dialogModel = ConfirmationDialogModel()
        val dialog = DialogUtils.getRemoveCallLogConfirmationDialog(
            requireActivity(),
            dialogModel
        )

        dialogModel.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        dialogModel.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Deleting call log with ref key or call ID [${callLogModel.id}]")
                callLogModel.delete()
                listViewModel.filter()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showNumbersOrAddressesDialog(list: List<ContactNumberOrAddressModel>) {
        val numberOrAddressModel = NumberOrAddressPickerDialogModel(list)
        val dialog =
            DialogUtils.getNumberOrAddressPickerDialog(
                requireActivity(),
                numberOrAddressModel
            )

        numberOrAddressModel.dismissEvent.observe(viewLifecycleOwner) { event ->
            event.consume {
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
