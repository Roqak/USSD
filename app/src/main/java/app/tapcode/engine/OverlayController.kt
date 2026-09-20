package app.tapcode.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.tapcode.R
import app.tapcode.config.ConfigRepository
import app.tapcode.config.TapcodeConfig
import com.google.android.material.button.MaterialButton

/**
 * UX-02: full-screen overlay (TYPE_ACCESSIBILITY_OVERLAY) shown only while a
 * session started in Tapcode is active. Renders screens, confirmations,
 * errors, results; back and cancel always visible.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val controller: SessionController?
) {
    private var view: LinearLayout? = null
    private var body: TextView? = null
    private var buttons: LinearLayout? = null
    private var input: EditText? = null
    private lateinit var config: ConfigRepository

    fun show() {
        if (view != null) return
        config = ConfigRepository(service)
        val wm = service.getSystemService(WindowManager::class.java)
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(service, R.color.overlay_bg))
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(22), dp(20), dp(22), dp(14))
            elevation = dp(24).toFloat()
        }
        val title = TextView(service).apply {
            text = "Tapcode session"
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            contentDescription = "Tapcode session overlay. USSD dialog is hidden while this is open."
        }
        body = TextView(service).apply {
            textSize = 15f
            setTextColor(ContextCompat.getColor(service, R.color.overlay_on_bg))
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(10), 0, 0)
        }
        input = EditText(service).apply {
            hint = "Reply"
            contentDescription = "USSD reply input"
            visibility = View.GONE
            background = ContextCompat.getDrawable(service, R.drawable.bg_overlay_field)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            textSize = 15f
            setTextColor(0xFF1B1D24.toInt())
            setHintTextColor(ContextCompat.getColor(service, R.color.overlay_muted))
            isSingleLine = true
        }
        buttons = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val cancel = overlayButton(
            "Cancel session",
            "Cancel the current USSD session and dismiss the operator dialog",
            filled = false, ghost = true
        ) { cancel() }
        container.addView(title)
        container.addView(body)
        container.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        container.addView(buttons)
        container.addView(cancel, sectionParams())
        view = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        wm.addView(container, params)
    }

    fun render(screen: UssdScreen) {
        show()
        body?.text = if (screen.kind == ScreenKind.MENU) screen.prompt else screen.raw
        val needsInput = screen.kind == ScreenKind.INPUT || screen.kind == ScreenKind.MENU
        input?.visibility = if (screen.kind == ScreenKind.INPUT) View.VISIBLE else View.GONE

        buttons?.removeAllViews()
        if (screen.kind == ScreenKind.MENU) {
            screen.options.forEach { opt ->
                buttons?.addView(
                    overlayButton("${opt.number}. ${opt.label}", "Option ${opt.number}: ${opt.label}", filled = false) {
                        onReply(opt.number)
                    },
                    sectionParams()
                )
            }
        }
        if (needsInput && screen.mentionsCharge) {
            // UX-03: confirm before any reply on a charge screen
            buttons?.addView(
                overlayButton("Confirm send", "Confirm sending this reply; the screen mentions a charge", filled = true) {
                    val text = input?.text?.toString().orEmpty()
                    controller?.send(text, confirmed = true)
                },
                sectionParams()
            )
        } else if (screen.kind == ScreenKind.INPUT) {
            buttons?.addView(
                overlayButton("Send", "Send reply", filled = true) {
                    val text = input?.text?.toString().orEmpty()
                    onReply(text)
                },
                sectionParams()
            )
        }
    }

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    private fun sectionParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }

    private fun overlayButton(
        text: String,
        description: String,
        filled: Boolean,
        ghost: Boolean = false,
        onClick: () -> Unit
    ): MaterialButton =
        MaterialButton(service).apply {
            this.text = text
            contentDescription = description
            isAllCaps = false
            textSize = 15f
            minHeight = dp(46)
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            when {
                ghost -> {
                    setBackgroundColor(Color.TRANSPARENT)
                    elevation = 0f
                    setTextColor(ContextCompat.getColor(service, R.color.overlay_muted))
                }
                filled -> {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(service, R.color.primary)
                    )
                    setTextColor(Color.WHITE)
                }
                else -> {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(service, R.color.overlay_tint)
                    )
                    strokeColor = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
                    strokeWidth = dp(1)
                    setTextColor(ContextCompat.getColor(service, R.color.overlay_on_bg))
                }
            }
            setOnClickListener { onClick() }
        }

    private fun onReply(text: String) {
        if (text.isBlank()) return
        controller?.send(text)
    }

    fun typeAndSend(reply: String) {
        val root = service.rootInActiveWindow ?: return
        val cfg = config.load().forManufacturer(Build.MANUFACTURER)
        val editable = findEditable(root) ?: return
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
        }
        editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        pressSend(cfg)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { c ->
                val r = findEditable(c)
                if (r != null) return r
            }
        }
        return null
    }

    private fun pressSend(cfg: TapcodeConfig) {
        val root = service.rootInActiveWindow ?: return
        val sel = cfg.buttonSelectors.send
        sel.viewIds.firstNotNullOfOrNull { root.findAccessibilityNodeInfosByViewId(it).firstOrNull() }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: run {
            sel.labels.firstNotNullOfOrNull { root.findAccessibilityNodeInfosByText(it).firstOrNull() }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    /** SE-05: dismiss the operator dialog cleanly and close the overlay. */
    fun dismissDialog() {
        val cfg = config.load().forManufacturer(Build.MANUFACTURER)
        val root = service.rootInActiveWindow ?: return
        val sel = cfg.buttonSelectors.cancel
        val cancelled = sel.viewIds.firstNotNullOfOrNull {
            root.findAccessibilityNodeInfosByViewId(it).firstOrNull()
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (!cancelled) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        hide()
    }

    fun cancel() {
        controller?.cancel()
        hide()
    }

    fun hide() {
        val v = view ?: return
        try {
            service.getSystemService(WindowManager::class.java).removeView(v)
        } catch (_: Exception) {
        }
        view = null
    }
}