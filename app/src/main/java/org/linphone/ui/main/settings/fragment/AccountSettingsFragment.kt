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
package org.linphone.ui.main.settings.fragment

import org.linphone.LinphoneApplication.Companion.coreContext
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.linphone.shiroikuma.SkRinNumberDialog
import org.linphone.shiroikuma.SkAccountNumbers
import org.linphone.shiroikuma.SkOdorikCallback
import androidx.core.widget.doAfterTextChanged
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountSettingsFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.utils.PasswordDialogModel
import org.linphone.ui.main.settings.viewmodel.AccountSettingsViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class AccountSettingsFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Account Settings Fragment]"
    }

    private lateinit var binding: AccountSettingsFragmentBinding

    private val args: AccountSettingsFragmentArgs by navArgs()

    private lateinit var viewModel: AccountSettingsViewModel

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountSettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[AccountSettingsViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        binding.setBackClickListener {
            goBack()
        }

        // shiroikuma fork: the account's 臨 number. A choice and not a typed field, so a number
        // another account already carries cannot be entered by mistake — it is refused.
        fun showRinNumber() {
            binding.skRinNumber.text = SkAccountNumbers.numberOf(requireContext(), identity)
                ?.let { "${SkAccountNumbers.MARK}$it" } ?: getString(R.string.sk_rin_number_none)
        }
        showRinNumber()
        binding.skRinNumber.setOnClickListener {
            val current = SkAccountNumbers.numberOf(requireContext(), identity)
            coreContext.postOnCoreThread { core ->
                val takenBy = core.accountList.mapNotNull { account ->
                    val address = account.params.identityAddress
                    val uri = address?.asStringUriOnly().orEmpty()
                    if (uri.isEmpty() || uri == identity) return@mapNotNull null
                    SkAccountNumbers.numberOf(requireContext(), uri)?.let {
                        it to (address?.displayName?.takeIf { n -> n.isNotEmpty() } ?: address?.username.orEmpty())
                    }
                }.toMap()
                coreContext.postOnMainThread {
                    if (!isAdded) return@postOnMainThread
                    SkRinNumberDialog(
                        requireActivity(),
                        getString(R.string.sk_rin_number_title),
                        current,
                        takenBy
                    ) { picked ->
                        if (!SkAccountNumbers.set(requireContext(), identity, picked)) {
                            (requireActivity() as GenericActivity).showRedToast(
                                getString(R.string.sk_rin_number_taken),
                                R.drawable.warning_circle
                            )
                        }
                        showRinNumber()
                    }
                }
            }
        }

        // shiroikuma fork: Odorik's callback — which phone it rings first, the line's password
        // for when the SIP stack kept only its hash, and whether the first leg answers itself.
        if (SkOdorikCallback.isOdorik(identity)) {
            val context = requireContext().applicationContext
            binding.skCallbackBox.visibility = View.VISIBLE
            binding.skCallbackCaller.setText(SkOdorikCallback.callerOf(context, identity))
            binding.skCallbackCaller.doAfterTextChanged {
                SkOdorikCallback.setCaller(context, identity, it?.toString().orEmpty())
            }
            binding.skCallbackPassword.setText(SkOdorikCallback.passwordOf(context, identity))
            binding.skCallbackPassword.doAfterTextChanged {
                SkOdorikCallback.setPassword(context, identity, it?.toString().orEmpty())
            }
            // Hidden until asked for: the eye shows it, and hides it again.
            var passwordShown = false
            binding.skCallbackPasswordEye.setOnClickListener {
                passwordShown = !passwordShown
                val field = binding.skCallbackPassword
                val cursor = field.selectionEnd
                val face = field.typeface
                field.inputType = InputType.TYPE_CLASS_TEXT or if (passwordShown) {
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                field.typeface = face
                field.setSelection(cursor.coerceIn(0, field.text?.length ?: 0))
                binding.skCallbackPasswordEye.setImageResource(
                    if (passwordShown) R.drawable.eye_slash else R.drawable.eye
                )
            }
            binding.skCallbackAnswer.isChecked = SkOdorikCallback.autoAnswer(context, identity)
            binding.skCallbackAnswer.setOnCheckedChangeListener { _, on ->
                SkOdorikCallback.setAutoAnswer(context, identity, on)
            }
        }

        binding.setUpdatePasswordClickListener {
            showUpdatePasswordDialog()
        }

        binding.setOutboundProxyTooltipClickListener {
            showOutboundProxyInfoDialog()
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                } else {
                    Log.e(
                        "$TAG Failed to find an account matching this identity address [$identity]"
                    )
                    val message = getString(R.string.account_failed_to_find_identity_toast)
                    val icon = R.drawable.warning_circle
                    (requireActivity() as GenericActivity).showRedToast(message, icon)
                    goBack()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.saveChanges()
        // It is possible some value have changed, causing some menu to appear or disappear
        sharedViewModel.forceUpdateAvailableNavigationItems.value = Event(true)
    }

    private fun showUpdatePasswordDialog() {
        val model = PasswordDialogModel()
        val dialog = DialogUtils.getUpdatePasswordDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume { password ->
                viewModel.updateAccountPassword(password)
                // shiroikuma fork: the callback's copy follows (the field's watcher stores it)
                if (SkOdorikCallback.isOdorik(args.accountIdentity)) {
                    binding.skCallbackPassword.setText(password)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showOutboundProxyInfoDialog() {
        val dialog = DialogUtils.getAccountOutboundProxyHelpDialog(requireActivity())
        dialog.show()
    }
}
