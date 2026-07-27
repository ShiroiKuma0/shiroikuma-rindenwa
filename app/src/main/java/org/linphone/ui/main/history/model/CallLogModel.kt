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
package org.linphone.ui.main.history.model

import androidx.annotation.IntegerRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.CallLog
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PhoneNumberUtils
import org.linphone.utils.TimestampUtils

class CallLogModel
    @WorkerThread
    constructor(private val callLog: CallLog) {
    companion object {
        private const val TAG = "[Call Log Model]"

        /**
         * shiroikuma fork: how many trailing digits decide that two numbers are the same line.
         * Nine covers a national subscriber number in every plan 白い熊 uses, while staying long
         * enough that two different numbers of one contact never collide.
         */
        private const val SIGNIFICANT_DIGITS = 9

        /**
         * shiroikuma fork: country code → how its national number is grouped, most specific
         * first. A candidate only applies when the digits after the code match the grouping's
         * total length exactly, which is what keeps "1" from swallowing unrelated numbers.
         */
        private val NUMBER_GROUPINGS = listOf(
            "420" to listOf(3, 3, 3), // Czechia:       +420-601-524-009
            "1" to listOf(3, 3, 4) // North America: +1-808-500-5515
        )
    }

    val id = callLog.callId ?: callLog.refKey

    val timestamp = callLog.startDate

    val address = callLog.remoteAddress

    val sipUri = address.asStringUriOnly()

    val displayedAddress: String

    val avatarModel: ContactAvatarModel

    val wasConference: Boolean

    @IntegerRes
    val iconResId: Int

    val dateTime: String

    /**
     * shiroikuma fork: the number this call actually used, plus the address-book field it is
     * stored under ("Home", "Work mobile", …). With a contact holding several numbers the name
     * alone does not say which one rang, which is the whole point of a call log. Empty when
     * there is nothing useful to add — a conference, or a caller with no number at all.
     */
    val numberWithLabel: String

    val friendRefKey: String?

    var friendExists: Boolean = false

    init {
        val date = if (TimestampUtils.isToday(timestamp)) {
            AppUtils.getString(R.string.today)
        } else if (TimestampUtils.isYesterday(timestamp)) {
            AppUtils.getString(R.string.yesterday)
        } else {
            TimestampUtils.toString(timestamp, onlyDate = true, shortDate = true, hideYear = true)
        }
        val time = TimestampUtils.timeToString(timestamp)
        dateTime = "$date | $time"

        wasConference = callLog.wasConference()
        if (wasConference) {
            val conferenceInfo = callLog.conferenceInfo
            if (conferenceInfo != null) {
                avatarModel = coreContext.contactsManager.getContactAvatarModelForConferenceInfo(
                    conferenceInfo
                )
            } else {
                Log.w("$TAG Failed to retrieve conference info attached to call log!")
                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.address = address
                fakeFriend.name = LinphoneUtils.getDisplayName(address)
                avatarModel = ContactAvatarModel(fakeFriend)
                avatarModel.forceConferenceIcon.postValue(true)
            }

            friendRefKey = null
            friendExists = false
        } else {
            avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val friend = avatarModel.friend
            friendRefKey = friend.refKey
            friendExists = coreContext.contactsManager.isContactAvailable(friend)
        }
        displayedAddress = if (corePreferences.onlyDisplaySipUriUsername) {
            address.username ?: ""
        } else {
            sipUri
        }

        numberWithLabel = computeNumberWithLabel()

        iconResId = LinphoneUtils.getCallIconResId(callLog.status, callLog.dir)
    }

    /**
     * shiroikuma fork: match the call's remote number against the contact's stored numbers and
     * render "<number> · <label>". Falls back to the bare number when the caller is unknown or
     * the matching entry carries no label.
     */
    @WorkerThread
    private fun computeNumberWithLabel(): String {
        if (wasConference) return ""

        val remote = address.username.orEmpty()
        if (remote.isEmpty()) return ""

        val match = if (friendExists) {
            avatarModel.friend.phoneNumbersWithLabel.firstOrNull {
                isSameNumber(it.phoneNumber.orEmpty(), remote)
            }
        } else {
            null
        }

        // The call's own number, not the stored one: it arrives in E.164 from the provider, while
        // an address-book entry may be written any which way. The contact match is only consulted
        // for the label.
        val number = formatNumber(remote)
        val label = if (match != null) {
            PhoneNumberUtils.vcardParamStringToAddressBookLabel(
                coreContext.context.resources,
                match.label.orEmpty()
            )
        } else {
            ""
        }

        return if (label.isEmpty()) number else "$number · $label"
    }

    /**
     * shiroikuma fork: group a number into the house reading — Czech as `+420-601-524-009`,
     * North American as `+1-808-500-5515`. Anything whose country code we do not group (an
     * internal extension, a SIP username, a country not in [NUMBER_GROUPINGS]) is left exactly
     * as it arrived rather than guessed at.
     */
    private fun formatNumber(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return raw

        for ((countryCode, groups) in NUMBER_GROUPINGS) {
            if (!digits.startsWith(countryCode)) continue

            val national = digits.removePrefix(countryCode)
            if (national.length != groups.sum()) continue

            val parts = ArrayList<String>(groups.size + 1)
            parts.add("+$countryCode")
            var offset = 0
            for (group in groups) {
                parts.add(national.substring(offset, offset + group))
                offset += group
            }
            return parts.joinToString("-")
        }

        return raw
    }

    /**
     * shiroikuma fork: address-book numbers and the number a call arrives on rarely match
     * character for character — "+420 601 524 009", "0601524009" and "601524009" are the same
     * line. Compare the trailing significant digits instead, which is enough to tell one of a
     * contact's numbers from another without dragging in a full E.164 parser.
     */
    private fun isSameNumber(a: String, b: String): Boolean {
        val digitsA = a.filter(Char::isDigit)
        val digitsB = b.filter(Char::isDigit)
        if (digitsA.isEmpty() || digitsB.isEmpty()) return false

        val compared = minOf(digitsA.length, digitsB.length, SIGNIFICANT_DIGITS)
        return digitsA.takeLast(compared) == digitsB.takeLast(compared)
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            core.removeCallLog(callLog)
        }
    }
}
