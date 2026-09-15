package app.tapcode

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager

/**
 * UX-08: distinguishes "service disabled" from "blocked by Android 13+
 * restricted settings", which needs the ⋮ → Allow restricted settings flow.
 */
object AccessibilityStatus {

    enum class State { ENABLED, DISABLED, RESTRICTED }

    fun check(context: Context): State {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return State.DISABLED
        val enabled = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        val isBound = app.tapcode.engine.DialogDriver.isBound()
        if (isBound) return State.ENABLED

        // The service is registered in settings but not yet connected: on
        // Android 12+ this is typically the restricted-settings block. On
        // Android 13+ a sideloaded app shows "Controlled by restricted setting"
        // and the toggle is greyed out until the user allows it.
        return if (isSideloaded(context) && Build.VERSION.SDK_INT >= 33) {
            State.RESTRICTED
        } else {
            State.DISABLED
        }
    }

    /** Sideloaded = installed from a source other than Play (unknown installer). */
    private fun isSideloaded(context: Context): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer.isNullOrEmpty()
        } catch (_: Exception) {
            true
        }
    }

    fun instructions(state: State): String = when (state) {
        State.ENABLED -> "Session driver is active."
        State.RESTRICTED ->
            "Android blocks accessibility for sideloaded apps until you allow it:\n" +
                "1. Tap Fix below to open App info\n" +
                "2. Tap the ⋮ menu (top right)\n" +
                "3. Tap \"Allow restricted settings\"\n" +
                "4. Return here and enable the service"
        State.DISABLED ->
            "Tap Fix below and enable \"Tapcode session driver\" in accessibility settings."
    }
}