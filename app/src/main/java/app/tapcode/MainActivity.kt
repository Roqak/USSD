package app.tapcode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.tapcode.config.ConfigRepository
import app.tapcode.config.TapcodeConfig
import app.tapcode.engine.DialogDriver
import app.tapcode.engine.SimDetector
import com.google.android.material.button.MaterialButton

/**
 * UX-01: home screen with detected SIMs and quick-answer tiles for MTN lines.
 * UX-08: status checks for call permission and the accessibility service,
 * with a fix action for each — including the Android 13+ restricted-settings
 * path for sideloaded installs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusTitle: TextView
    private lateinit var statusRows: LinearLayout
    private lateinit var simList: LinearLayout
    private val simDetector by lazy { SimDetector(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusTitle = findViewById(R.id.statusTitle)
        statusRows = findViewById(R.id.statusRows)
        simList = findViewById(R.id.simList)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val callOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val phoneStateOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val driverState = AccessibilityStatus.check(this)

        statusRows.removeAllViews()
        addStatusRow(getString(R.string.row_calls), callOk) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
        }
        addStatusRow(getString(R.string.row_phone), phoneStateOk) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 1)
        }
        addStatusRow(getString(R.string.row_driver), driverState == AccessibilityStatus.State.ENABLED) {
            when (driverState) {
                AccessibilityStatus.State.RESTRICTED -> openAppDetails()
                else -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        if (driverState != AccessibilityStatus.State.ENABLED) {
            addHint(AccessibilityStatus.instructions(driverState))
        }
        val ready = callOk && phoneStateOk && driverState == AccessibilityStatus.State.ENABLED
        statusTitle.setText(if (ready) R.string.status_ready else R.string.status_setup_needed)

        simList.removeAllViews()
        if (!callOk || !phoneStateOk) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE),
                1
            )
            return
        }
        val cfg = ConfigRepository(this).load()
        val sims = simDetector.detectSims()
        if (sims.isEmpty()) {
            addHint(getString(R.string.no_sims))
            return
        }
        sims.forEach { sim -> simList.addView(simCard(sim, cfg)) }
    }

    private fun addStatusRow(label: String, ok: Boolean, fix: (() -> Unit)? = null) {
        val row = layoutInflater.inflate(R.layout.view_status_row, statusRows, false)
        row.findViewById<View>(R.id.dot).background.setTint(
            ContextCompat.getColor(this, if (ok) R.color.ok else R.color.warn)
        )
        row.findViewById<TextView>(R.id.label).text = label
        val fixButton = row.findViewById<MaterialButton>(R.id.fixButton)
        if (fix == null) {
            fixButton.visibility = View.GONE
        } else {
            fixButton.visibility = View.VISIBLE
            fixButton.setOnClickListener { fix() }
        }
        statusRows.addView(row)
    }

    private fun addHint(text: String) {
        statusRows.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            setPadding(0, 4, 0, 8)
        })
    }

    private fun simCard(sim: SimDetector.SimInfo, cfg: TapcodeConfig): View {
        val card = layoutInflater.inflate(R.layout.view_sim_card, simList, false)
        card.findViewById<TextView>(R.id.avatar).text =
            sim.carrierName.trim().firstOrNull()?.uppercase() ?: "?"
        card.findViewById<TextView>(R.id.carrierName).text = sim.carrierName
        card.findViewById<TextView>(R.id.simMeta).text = "Subscription ${sim.subscriptionId}"
        card.findViewById<TextView>(R.id.qaBadge).visibility =
            if (sim.isMtn) View.VISIBLE else View.GONE

        val qaList = card.findViewById<LinearLayout>(R.id.qaList)
        if (sim.isMtn) {
            cfg.quickAnswers.forEach { (name, qa) ->
                qaList.addView(quickAnswerTile(name, sim.carrierName) {
                    startActivity(
                        Intent(this@MainActivity, QuickAnswerActivity::class.java)
                            .putExtra("code", qa.code)
                            .putExtra("subId", sim.subscriptionId)
                    )
                })
            }
        }
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun quickAnswerTile(rawName: String, carrier: String, onClick: () -> Unit): MaterialButton {
        val name = rawName.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = name
            contentDescription = "$rawName quick answer on $carrier"
            isAllCaps = false
            cornerRadius = dp(12)
            setStrokeColorResource(R.color.outline)
            strokeWidth = dp(1)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = dp(44)
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
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