package app.tapcode

import android.content.Intent
import android.telephony.TelephonyManager
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.tapcode.engine.UssdPresenter
import com.google.android.material.button.MaterialButton

/**
 * SE-02: single-response codes go through sendUssdRequest with no
 * accessibility involvement. SE-08: network errors map to clear states
 * with a retry action. The dialed code is never shown; the operator's
 * response is presented as a friendly result card.
 */
class QuickAnswerActivity : AppCompatActivity() {

    private lateinit var resultHeading: TextView
    private lateinit var resultAmount: TextView
    private lateinit var resultText: TextView
    private lateinit var retryButton: MaterialButton
    private var code: String? = null
    private var subscriptionId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_answer)
        resultHeading = findViewById(R.id.resultHeading)
        resultAmount = findViewById(R.id.resultAmount)
        resultText = findViewById(R.id.resultText)
        retryButton = findViewById(R.id.retryButton)
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }

        val code = intent.getStringExtra("code")
        if (code == null) {
            resultHeading.text = "No request available"
            resultText.text = "Open this screen from the Tapcode home screen."
            return
        }
        retryButton.setOnClickListener { sendUssd(code, subscriptionId) }
        val subId = intent.getIntExtra("subId", -1)
        subscriptionId = if (subId >= 0) subId else null
        sendUssd(code, subscriptionId)
    }

    private fun sendUssd(code: String, subscriptionId: Int?) {
        retryButton.visibility = View.GONE
        resultHeading.text = "Sending…"
        resultAmount.visibility = View.GONE
        resultText.text = ""
        val tm = getSystemService(TelephonyManager::class.java)
        tm.sendUssdRequest(
            code,
            object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    runOnUiThread {
                        val text = response?.toString().orEmpty()
                        if (text.isBlank()) {
                            resultHeading.text = "No response"
                            resultText.text = ""
                        } else {
                            val friendly = UssdPresenter.present(text)
                            resultHeading.text = friendly.heading ?: "Response"
                            if (friendly.amount != null) {
                                resultAmount.text = friendly.amount
                                resultAmount.visibility = View.VISIBLE
                            } else {
                                resultAmount.visibility = View.GONE
                            }
                            resultText.text = friendly.body
                        }
                    }
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    runOnUiThread {
                        // SE-08: map network errors to clear states with retry
                        resultHeading.text = "Didn't work"
                        resultAmount.visibility = View.GONE
                        resultText.text = when (failureCode) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL ->
                                "USSD service unavailable. Check your signal and try again."
                            else -> "The request failed. Please try again."
                        }
                        retryButton.visibility = View.VISIBLE
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }
}