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
package org.linphone.ui.main.contacts.model

import android.net.Uri
import android.telephony.PhoneNumberUtils
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AbstractAvatarModel
import org.linphone.contacts.getNativeContactPictureUri
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Friend
import org.linphone.core.FriendListenerStub
import org.linphone.core.SecurityLevel
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.TimestampUtils
import androidx.core.net.toUri
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.shiroikuma.SkContactReadings

class ContactAvatarModel
    @WorkerThread
    constructor(val friend: Friend, val address: Address? = null) : AbstractAvatarModel() {
    companion object {
        private const val TAG = "[Contact Avatar Model]"
    }

    val id = friend.refKey ?: friend.name ?: ""

    val contactName = friend.name

    val isStored = friend.inList()

    val isReadOnly = friend.isReadOnly

    val isNative = !friend.nativeUri.isNullOrEmpty()

    val isFavourite = MutableLiveData<Boolean>()

    val lastPresenceInfo = MutableLiveData<String>()

    val name = MutableLiveData<String>()

    var sortingName: String? = null

    var firstLetter: String? = null

    /**
     * shiroikuma fork: what the letter headings bucket this contact under — its phonetic reading
     * (フリガナ) when the address book stores one, else the name [sortingName] already holds.
     * Without the reading a kanji name belongs to no gojūon row and every Japanese contact falls
     * into ＃, which is exactly what the list did before.
     */
    var skSectionKey: String? = null

    /**
     * shiroikuma fork: the contact's first phone number, written under the name in the list the
     * way the sister address book writes it. Empty when the contact has none.
     */
    var skPhoneNumber: String = ""

    private val friendListener = object : FriendListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(fr: Friend) {
            Log.d(
                "$TAG Presence received for friend [${fr.name}]: [${fr.consolidatedPresence}]"
            )
            computePresence()
        }
    }

    init {
        presenceStatus.postValue(ConsolidatedPresence.Offline)

        if (friend.addresses.isNotEmpty()) {
            friend.addListener(friendListener)
        }

        update(address)
        refreshSortingName()
    }

    @AnyThread
    fun compare(other: ContactAvatarModel?): Boolean {
        if (other == null) return false

        val picture = picturePath.value
        val otherPicture = other.picturePath.value
        if (picture != null && otherPicture != null && picture != otherPicture) {
            return false
        }

        if (contactName != other.contactName) {
            return false
        }

        if (id != other.id) {
            return false
        }

        if (isFavourite.value != other.isFavourite.value) {
            return false
        }

        return true
    }

    @WorkerThread
    fun destroy() {
        if (friend.addresses.isNotEmpty()) {
            friend.removeListener(friendListener)
        }
    }

    @WorkerThread
    fun refreshSortingName() {
        sortingName = getNameToUseForSorting()
        firstLetter = AppUtils.getFirstLetter(getNameToUseForSorting().orEmpty())

        // shiroikuma fork: both computed here, on the worker thread that already owns the Friend —
        // the list adapter must not touch the content provider while it binds rows.
        skSectionKey = SkContactReadings.readingFor(coreContext.context, friend.refKey)
            ?.takeIf { it.isNotEmpty() }
            ?: sortingName
        skPhoneNumber = formatContactNumber(
            friend.phoneNumbersWithLabel.firstOrNull()?.phoneNumber.orEmpty()
        )
    }

    @WorkerThread
    fun update(address: Address?) {
        updateSecurityLevel(address)

        isFavourite.postValue(friend.starred)
        initials.postValue(AppUtils.getInitials(friend.name.orEmpty()))
        showTrust.postValue(true)
        picturePath.postValue(getAvatarUri(friend).toString())

        name.postValue(friend.name)
        computePresence(address)
    }

    @WorkerThread
    fun updateSecurityLevelUsingConversation(chatRoom: ChatRoom) {
        /*
        // Don't do that, as chatRoom securityLevel is taking into account the security level
        // between the device and the other devices that shares the same SIP identity
        val securityLevel = when (chatRoom.securityLevel) {
            ChatRoom.SecurityLevel.Unsafe -> {
                SecurityLevel.Unsafe
            }
            ChatRoom.SecurityLevel.Encrypted -> {
                SecurityLevel.EndToEndEncrypted
            }
            ChatRoom.SecurityLevel.Safe -> {
                SecurityLevel.EndToEndEncryptedAndVerified
            }
            else -> SecurityLevel.None
        }*/

        val participants = chatRoom.participants
        var lowestSecurityLevel = if (participants.isEmpty()) {
            SecurityLevel.None
        } else {
            SecurityLevel.EndToEndEncryptedAndVerified
        }
        for (participant in participants) {
            val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(
                participant.address
            )
            val level = avatar.trust.value ?: SecurityLevel.None
            if (level == SecurityLevel.None) {
                lowestSecurityLevel = SecurityLevel.None
            } else if (level == SecurityLevel.Unsafe) {
                lowestSecurityLevel = SecurityLevel.Unsafe
                break
            } else if (lowestSecurityLevel != SecurityLevel.None && level != SecurityLevel.EndToEndEncryptedAndVerified) {
                lowestSecurityLevel = SecurityLevel.EndToEndEncrypted
            }
        }
        trust.postValue(lowestSecurityLevel)
    }

    @WorkerThread
    fun updateSecurityLevel(address: Address?) {
        if (address == null) {
            trust.postValue(friend.securityLevel)
        } else {
            trust.postValue(friend.getSecurityLevelForAddress(address))
        }
    }

    @WorkerThread
    fun getNameToUseForSorting(): String {
        val sortByFirstName = corePreferences.sortContactsByFirstName
        val firstOrLastName = if (sortByFirstName) friend.firstName else friend.lastName
        return firstOrLastName ?: friend.name ?: friend.organization ?: friend.vcard?.fullName ?: ""
    }

    /**
     * shiroikuma fork: the number grouped the way the sister address book writes it —
     * "+420 737 831 885" rather than the unbroken "+420737831885" the SDK hands back. Android's
     * own formatter does the grouping, per the SIM's country; a number it cannot parse is left
     * exactly as it arrived rather than guessed at.
     */
    @WorkerThread
    private fun formatContactNumber(raw: String): String {
        if (raw.isEmpty()) return raw
        val region = Locale.getDefault().country.takeIf { it.isNotEmpty() } ?: return raw
        return PhoneNumberUtils.formatNumber(raw, region) ?: raw
    }

    @WorkerThread
    private fun getAvatarUri(friend: Friend): Uri? {
        val picturePath = friend.photo
        if (!picturePath.isNullOrEmpty()) {
            return picturePath.toUri()
        }

        val refKey = friend.refKey
        if (refKey != null) {
            try {
                return friend.getNativeContactPictureUri()
            } catch (numberFormatException: NumberFormatException) {
                // Expected for contacts created by Linphone
            }
        }

        return null
    }

    @WorkerThread
    private fun computePresence(address: Address? = null) {
        val presence = if (address == null) {
            friend.consolidatedPresence
        } else {
            friend.getPresenceModelForUriOrTel(address.asStringUriOnly())?.consolidatedPresence ?: friend.consolidatedPresence
        }
        Log.d("$TAG Friend [${friend.name}] presence status is [$presence]")
        presenceStatus.postValue(presence)

        val presenceString = when (presence) {
            ConsolidatedPresence.Online -> {
                AppUtils.getString(R.string.contact_presence_status_online)
            }
            ConsolidatedPresence.Busy -> {
                val timestamp = friend.presenceModel?.latestActivityTimestamp ?: -1L
                if (timestamp != -1L) {
                    when {
                        TimestampUtils.isToday(timestamp) -> {
                            val time = TimestampUtils.timeToString(
                                timestamp,
                                timestampInSecs = true
                            )
                            AppUtils.getFormattedString(
                                R.string.contact_presence_status_was_online_today_at,
                                time
                            )
                        }
                        TimestampUtils.isYesterday(timestamp) -> {
                            val time = TimestampUtils.timeToString(
                                timestamp,
                                timestampInSecs = true
                            )
                            AppUtils.getFormattedString(
                                R.string.contact_presence_status_was_online_yesterday_at,
                                time
                            )
                        }
                        else -> {
                            val date = TimestampUtils.toString(
                                timestamp,
                                onlyDate = true,
                                shortDate = false,
                                hideYear = true
                            )
                            AppUtils.getFormattedString(
                                R.string.contact_presence_status_was_online_on,
                                date
                            )
                        }
                    }
                } else {
                    AppUtils.getString(R.string.contact_presence_status_away)
                }
            }
            ConsolidatedPresence.DoNotDisturb -> {
                AppUtils.getString(R.string.contact_presence_status_do_not_disturb)
            }
            else -> ""
        }
        lastPresenceInfo.postValue(presenceString)
    }
}
