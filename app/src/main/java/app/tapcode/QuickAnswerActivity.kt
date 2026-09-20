package app.tapcode

import android.content.Intent
import android.telephony.TelephonyManager
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.material.button.MaterialButton

/**
 * SE-02: single-response codes go through sendUssdRequest with no
 * accessibility involvement. SE-08: network errors map to clear states
 * with a retry action.
 */
class QuickAnswerActivity : AppCompatActivity() {

    private lateinit var codeChip: TextView
    private lateinit var resultText: TextView
    private lateinit var retryButton: MaterialButton
    private var code: String? = null
    private var subscriptionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_answer)
        codeChip = findViewById(R.id.codeChip)
        resultText = findViewById(R.id.resultText)
        retryButton = findViewById(R.id.retryButton)
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }

        val code = intent.getStringExtra("code")
        if (code == null) {
            codeChip.visibility = View.GONE
            resultText.text = "No USSD code supplied."
            return
        }
        retryButton.setOnClickListener { sendUssd(code, subscriptionId) }
        val subId = intent.getIntExtra("subId", -1)
        subscriptionId = if (subId >= 0) subId else null
        sendUssd(code, subscriptionId)
    }

    private fun sendUssd(code: String, subscriptionId: Int?) {
        codeChip.text = code
        retryButton.visibility = View.GONE
        resultText.text = "Sending $code…"
        val tm = getSystemService(TelephonyManager::class.java)
        tm.sendUssdRequest(
            code,
            object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    runOnUiThread { resultText.text = response?.toString() ?: "No response" }
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    runOnUiThread {
                        // SE-08: map network errors to clear states with retry
                        resultText.text = when (failureCode) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL ->
                                "USSD service unavailable. Check signal and try again."
                            else -> "Request failed (code $failureCode)."
                        }
                        retryButton.visibility = View.VISIBLE
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }
}