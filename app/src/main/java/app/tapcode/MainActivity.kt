package app.tapcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.tapcode.config.ConfigRepository
import app.tapcode.engine.DialogDriver
import app.tapcode.engine.SimDetector
import android.content.Intent
import android.net.Uri

/**
 * UX-01: home screen with detected SIMs and quick-answer tiles for MTN lines.
 * UX-08: status checks for call permission and the accessibility service,
 * with a fix action for each — including the Android 13+ restricted-settings
 * path for sideloaded installs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var simList: LinearLayout
    private val simDetector by lazy { SimDetector(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        status = TextView(this).apply { textSize = 16f }
        simList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
        })
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Fix permissions"
            contentDescription = "Grant phone permission and open accessibility settings"
            setOnClickListener { requestFix() }
        })
        root.addView(simList)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val callOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val a11yState = AccessibilityStatus.check(this)
        status.text = buildString {
            appendLine("Call permission: ${if (callOk) "granted" else "missing"} (UX-08)")
            appendLine("Session driver: ${AccessibilityStatus.instructions(a11yState)}")
            appendLine()
            appendLine("For MTN lines. Tapcode is not affiliated with MTN Nigeria.")
        }

        simList.removeAllViews()
        if (!callOk) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        val cfg = ConfigRepository(this).load()
        simDetector.detectSims().forEach { sim ->
            simList.addView(TextView(this).apply {
                text = "${sim.carrierName} (SIM ${sim.subscriptionId})" +
                    if (sim.isMtn) " — quick answers ready" else ""
                textSize = 18f
                setPadding(0, 24, 0, 24)
            })
            if (sim.isMtn) {
                cfg.quickAnswers.forEach { (name, qa) ->
                    simList.addView(Button(this).apply {
                        text = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replaceFirstChar { it.uppercase() }
                        contentDescription = "$name quick answer on ${sim.carrierName}"
                        setOnClickListener {
                            startActivity(
                                Intent(this@MainActivity, QuickAnswerActivity::class.java)
                                    .putExtra("code", qa.code)
                                    .putExtra("subId", sim.subscriptionId)
                            )
                        }
                    })
                }
            }
        }
    }

    private fun requestFix() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
        }
        when (AccessibilityStatus.check(this)) {
            AccessibilityStatus.State.RESTRICTED -> openAppDetails()
            AccessibilityStatus.State.DISABLED ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            AccessibilityStatus.State.ENABLED -> Unit
        }
    }

    /** App info is where the ⋮ → "Allow restricted settings" control lives. */
    private fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        )
    }
}