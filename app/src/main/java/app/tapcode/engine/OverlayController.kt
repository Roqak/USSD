package app.tapcode.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.tapcode.config.ConfigRepository
import app.tapcode.config.TapcodeConfig

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
            setBackgroundColor(0xEE101418.toInt())
            setPadding(48, 48, 48, 48)
            elevation = 24f
        }
        val title = TextView(service).apply {
            text = "Tapcode session"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            contentDescription = "Tapcode session overlay. USSD dialog is hidden while this is open."
        }
        body = TextView(service).apply {
            textSize = 18f
            setTextColor(0xFFDADCE0.toInt())
        }
        input = EditText(service).apply {
            hint = "Reply"
            contentDescription = "USSD reply input"
            visibility = android.view.View.GONE
        }
        buttons = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val cancel = Button(service).apply {
            text = "Cancel session"
            contentDescription = "Cancel the current USSD session and dismiss the operator dialog"
            setOnClickListener { cancel() }
        }
        container.addView(title)
        container.addView(body)
        container.addView(input)
        container.addView(buttons)
        container.addView(cancel)
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
        body?.text = when {
            screen.kind == ScreenKind.MENU -> screen.prompt + "\n" +
                screen.options.joinToString("\n") { "${it.number}. ${it.label}" }
            else -> screen.raw
        }
        val needsInput = screen.kind == ScreenKind.INPUT || screen.kind == ScreenKind.MENU
        input?.visibility = if (screen.kind == ScreenKind.INPUT) android.view.View.VISIBLE else android.view.View.GONE

        buttons?.removeAllViews()
        if (screen.kind == ScreenKind.MENU) {
            screen.options.forEach { opt ->
                buttons?.addView(Button(service).apply {
                    text = "${opt.number}. ${opt.label}"
                    contentDescription = "Option ${opt.number}: ${opt.label}"
                    setOnClickListener { onReply(opt.number) }
                })
            }
        }
        if (needsInput && screen.mentionsCharge) {
            // UX-03: confirm before any reply on a charge screen
            buttons?.addView(Button(service).apply {
                text = "Confirm send"
                contentDescription = "Confirm sending this reply; the screen mentions a charge"
                setOnClickListener {
                    val text = input?.text?.toString().orEmpty()
                    controller?.send(text, confirmed = true)
                }
            })
        } else if (screen.kind == ScreenKind.INPUT) {
            buttons?.addView(Button(service).apply {
                text = "Send"
                contentDescription = "Send reply"
                setOnClickListener {
                    val text = input?.text?.toString().orEmpty()
                    onReply(text)
                }
            })
        }
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