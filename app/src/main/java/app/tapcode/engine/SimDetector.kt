package app.tapcode.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager

class SimDetector(private val context: Context) {

    data class SimInfo(
        val subscriptionId: Int,
        val carrierName: String,
        val isMtn: Boolean
    )

    fun detectSims(): List<SimInfo> {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val sm = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            ?: return emptyList()
        val subs = sm.activeSubscriptionInfoList ?: return emptyList()
        return subs.map {
            val name = it.carrierName?.toString().orEmpty()
            SimInfo(
                subscriptionId = it.subscriptionId,
                carrierName = name,
                isMtn = name.contains("MTN", ignoreCase = true)
            )
        }
    }

    fun mtnSim(): SimInfo? = detectSims().firstOrNull { it.isMtn }
}