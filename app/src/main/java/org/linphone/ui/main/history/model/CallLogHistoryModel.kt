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
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.Call.Dir
import org.linphone.core.CallLog
import org.linphone.shiroikuma.SkCallLog
import org.linphone.utils.LinphoneUtils

class CallLogHistoryModel
    @WorkerThread
    constructor(val callLog: CallLog) {
    val id = callLog.callId ?: callLog.refKey

    val isOutgoing = MutableLiveData<Boolean>()

    val isSuccessful = MutableLiveData<Boolean>()

    val dateTime = MutableLiveData<String>()

    val duration = MutableLiveData<String>()

    @IntegerRes
    val iconResId = MutableLiveData<Int>()

    init {
        isOutgoing.postValue(callLog.dir == Dir.Outgoing)

        // shiroikuma fork: one call of a contact's history reads in the same formats the list
        // above it uses — the day always spelled out here, since there are no headlines to carry it.
        val context = coreContext.context
        val startDate = callLog.startDate
        dateTime.postValue(
            "${SkCallLog.dayText(context, startDate)} ${SkCallLog.timeText(context, startDate)}"
        )

        duration.postValue(
            SkCallLog.durationText(
                context,
                callLog.duration,
                connected = callLog.status == Call.Status.Success
            )
        )

        isSuccessful.postValue(callLog.status == Call.Status.Success)
        iconResId.postValue(LinphoneUtils.getCallIconResId(callLog.status, callLog.dir))
    }
}
