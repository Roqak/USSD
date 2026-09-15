package app.tapcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * SE-02: single-response codes go through sendUssdRequest with no
 * accessibility involvement. SE-08: network errors map to clear states
 * with a retry action.
 */
class QuickAnswerActivity : AppCompatActivity() {

    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        result = TextView(this).apply { textSize = 18f; setPadding(0, 32, 0, 32) }
        root.addView(TextView(this).apply { textSize = 22f; text = "Quick answer" })
        root.addView(result)
        setContentView(root)

        val code = intent.getStringExtra("code") ?: return
        val subId = intent.getIntExtra("subId", -1)
        sendUssd(code, if (subId >= 0) subId else null)
    }

    private fun sendUssd(code: String, subscriptionId: Int?) {
        result.text = "Sending $code…"
        val tm = getSystemService(TelephonyManager::class.java)
        tm.sendUssdRequest(
            code,
            object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    runOnUiThread { result.text = response?.toString() ?: "No response" }
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    runOnUiThread {
                        // SE-08: map network errors to clear states with retry
                        result.text = when (failureCode) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL ->
                                "USSD service unavailable. Check signal and try again."
                            else -> "Request failed (code $failureCode)."
                        }
                        result.append("\n\nTap Back and retry.")
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }
}