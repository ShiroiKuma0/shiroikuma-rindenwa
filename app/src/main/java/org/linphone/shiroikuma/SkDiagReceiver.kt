package org.linphone.shiroikuma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the two buttons on the recording notification ([SkDiag]).
 *
 * Not exported: these come from the app's own notification and nowhere else.
 *  - **Mark** writes a numbered line into every log that is recording, so the moment something
 *    went wrong can be found again in a file of thousands of lines.
 *  - **Turn all off** stops every diagnostic at once, which also dismisses the notification.
 */
class SkDiagReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK -> {
                val number = SkDiag.mark(context)
                Toast.makeText(
                    context,
                    context.getString(R.string.sk_diag_marked, number),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            ACTION_ALL_OFF -> {
                for (kind in SkDiagKind.entries) {
                    SkDiag.setEnabled(context, kind, false)
                }
                Toast.makeText(context, R.string.sk_diag_all_off_done, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_MARK = "shiroikuma.rindenwa.diag.MARK"
        const val ACTION_ALL_OFF = "shiroikuma.rindenwa.diag.ALL_OFF"
    }
}
