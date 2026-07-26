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
package org.linphone.ui.assistant.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantPermissionsFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.shiroikuma.SkStartup
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.assistant.viewmodel.PermissionsViewModel
import kotlin.getValue

@UiThread
class PermissionsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Permissions Fragment]"
    }

    private lateinit var binding: AssistantPermissionsFragmentBinding

    private val viewModel: PermissionsViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private var leaving = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (isGranted) {
                Log.i("Permission [$permissionName] is now granted")
            } else {
                Log.i("Permission [$permissionName] has been denied")
                allGranted = false
            }
        }

        if (!allGranted) {
            Log.w(
                "$TAG Not all permissions were granted, leaving anyway, they will be asked again later..."
            )
        }
        leave()
    }

    private val telecomManagerPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG MANAGE_OWN_CALLS permission has been granted")
        } else {
            Log.w("$TAG MANAGE_OWN_CALLS permission has been denied, leaving this fragment")
        }
    }

    /**
     * shiroikuma fork: one permission at a time, for the per-row taps. Unlike the "grant all"
     * launcher this does NOT leave the screen — you stay on the list and can grant the next one.
     */
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i("$TAG Single permission request answered, granted=[$isGranted]")
        // Repaint the list: granted rows hide themselves through the view model.
        areAllPermissionsGranted()
    }

    /**
     * shiroikuma fork: request [permission], or send 白い熊 to the app's system settings page when
     * Android will no longer show a dialog for it.
     *
     * Once a permission has been denied permanently, `requestPermission` returns instantly with no
     * UI — which is exactly the "OK does nothing" dead end. `shouldShowRequestPermissionRationale`
     * is false both before the first ask and after a permanent denial, so we only treat it as
     * permanent once we have actually asked at least once in this session.
     */
    private fun requestOrOpenSettings(permission: String) {
        val granted = ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            Log.i("$TAG Permission [$permission] is already granted, opening settings anyway")
            openAppSettings()
            return
        }
        if (askedOnce.contains(permission) &&
            !shouldShowRequestPermissionRationale(permission)
        ) {
            Log.w("$TAG Permission [$permission] looks permanently denied, opening app settings")
            openAppSettings()
            return
        }
        askedOnce.add(permission)
        singlePermissionLauncher.launch(permission)
    }

    /** The app's own settings page — the only route left for a permanently denied permission. */
    private fun openAppSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null)
                )
            )
        } catch (e: Exception) {
            Log.e("$TAG Failed to open app settings: $e")
        }
    }

    private val askedOnce = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantPermissionsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("InlinedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setSkipClickListener {
            Log.i("$TAG User clicked skip...")
            leave()
        }

        binding.setGrantAllClickListener {
            Log.i("$TAG Requesting all permissions")
            requestPermissionLauncher.launch(
                Compatibility.getAllRequiredPermissionsArray()
            )
        }

        // shiroikuma fork: upstream declares these four per-row listeners but never binds them to
        // any view, so the list was inert — the only control was "grant all", which does nothing
        // at all once a permission has been permanently denied. Each row is now tappable and goes
        // to the system dialog, or to the app's settings page when the dialog will no longer show.
        binding.setGrantPostNotificationsClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestOrOpenSettings(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppSettings()
            }
        }
        binding.setGrantReadContactsClickListener {
            requestOrOpenSettings(Manifest.permission.READ_CONTACTS)
        }
        binding.setGrantRecordAudioClickListener {
            requestOrOpenSettings(Manifest.permission.RECORD_AUDIO)
        }
        binding.setGrantAccessCameraClickListener {
            requestOrOpenSettings(Manifest.permission.CAMERA)
        }
        // Upstream added this row (Android 17 only, optional) without a listener of its own —
        // keep it tappable like the four above it. The row hides itself below Android 17, where
        // Compatibility reports the permission as granted.
        binding.setGrantAccessLocalNetworkClickListener {
            if (Version.sdkAboveOrEqual(Version.API37_ANDROID_17_CINNAMON_BUN)) {
                requestOrOpenSettings(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                openAppSettings()
            }
        }

        // shiroikuma fork: leave the assistant entirely. The assistant force-navigates here before
        // the landing screen on a clean install, so without this a cleared install cannot reach the
        // main screen — and therefore cannot reach Export / Import to restore its accounts.
        binding.setSkSkipSetupClickListener {
            Log.i("$TAG Skipping setup entirely, leaving assistant")
            SkStartup.setAssistantSkipped(requireContext(), true)
            leaving = true
            requireActivity().finish()
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.MANAGE_OWN_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("$TAG Request MANAGE_OWN_CALLS permission")
            telecomManagerPermissionLauncher.launch(Manifest.permission.MANAGE_OWN_CALLS)
        }

        if (!Compatibility.hasFullScreenIntentPermission(requireContext())) {
            Log.w(
                "$TAG Android 14 or newer detected & full screen intent permission hasn't been granted!"
            )
            Compatibility.requestFullScreenIntentPermission(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()

        if (!leaving && areAllPermissionsGranted()) {
            Log.i("$TAG All permissions have been granted, skipping")
            leave()
        }
    }

    private fun leave() {
        if (leaving) return
        leaving = true

        if (requireActivity().intent.getBooleanExtra(AssistantActivity.SKIP_LANDING_EXTRA, false)) {
            Log.w(
                "$TAG We were asked to leave assistant if at least an account is already configured"
            )
            coreContext.postOnCoreThread { core ->
                if (core.accountList.isNotEmpty()) {
                    coreContext.postOnMainThread {
                        Log.w("$TAG At least one account was found, leaving assistant")
                        try {
                            requireActivity().finish()
                        } catch (ise: IllegalStateException) {
                            Log.e("$TAG Failed to finish activity: $ise")
                        }
                    }
                } else {
                    coreContext.postOnMainThread {
                        Log.w("$TAG No account was found, going to landing fragment")
                        try {
                            goToLoginFragment()
                        } catch (ise: IllegalStateException) {
                            Log.e("$TAG Failed to navigate to login fragment: $ise")
                        }
                    }
                }
            }
        } else {
            goToLoginFragment()
        }
    }

    private fun goToLoginFragment() {
        if (findNavController().currentDestination?.id == R.id.permissionsFragment) {
            val action =
                PermissionsFragmentDirections.actionPermissionsFragmentToLandingFragment()
            leaving = false
            findNavController().navigate(action)
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        for (permission in Compatibility.getAllRequiredPermissionsArray()) {
            val granted = ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
            viewModel.setPermissionGranted(permission, granted)
            if (!granted) {
                Log.w("$TAG Permission [$permission] hasn't been granted yet!")
                return false
            }

        }
        return Compatibility.hasFullScreenIntentPermission(requireContext())
    }
}
