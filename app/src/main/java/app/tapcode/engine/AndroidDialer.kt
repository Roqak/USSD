package app.tapcode.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

class AndroidDialer(private val context: Context) : Dialer {

    override fun dial(code: String, subscriptionId: Int?) {
        val uri = Uri.parse("tel:" + Uri.encode(code))
        if (Build.VERSION.SDK_INT >= 31 && subscriptionId != null) {
            val tm = context.getSystemService(TelecomManager::class.java)
            val handle = subscriptionIdToHandle(subscriptionId)
            if (handle != null) {
                val extras = android.os.Bundle().apply { putParcelable(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle) }
                tm.placeCall(uri, extras)
                return
            }
        }
        val intent = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun subscriptionIdToHandle(subscriptionId: Int): android.telecom.PhoneAccountHandle? {
        val tm = context.getSystemService(android.telecom.TelecomManager::class.java) ?: return null
        return try {
            // Phone account ids for SIM accounts equal their subscription id
            tm.getCallCapablePhoneAccounts().firstOrNull { it.id == subscriptionId.toString() }
        } catch (_: Exception) {
            null
        }
    }

    override fun sendReply(reply: String) {
        DialogDriver.overlayController?.typeAndSend(reply)
    }

    override fun cancelSession() {
        DialogDriver.overlayController?.dismissDialog()
    }

    override suspend fun awaitDialogAdvance(): Boolean {
        return true
    }
}