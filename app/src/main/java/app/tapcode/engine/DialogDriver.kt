package app.tapcode.engine

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.tapcode.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DialogDriver : AccessibilityService() {

    companion object {
        @Volatile
        var controller: SessionController? = null

        @Volatile
        var overlayController: OverlayController? = null

        private var instance: DialogDriver? = null
        fun isBound(): Boolean = instance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var config: ConfigRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        config = ConfigRepository(this)
        val cfg = config.load()
        controller = SessionController(
            parser = UssdParser(cfg),
            dialer = AndroidDialer(this),
            scope = scope
        )
        overlayController = OverlayController(this, controller!!)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val c = controller ?: return
        if (!c.isActive) return
        val pkg = event?.packageName?.toString() ?: return
        val cfg = config.load().forManufacturer(Build.MANUFACTURER)
        if (pkg !in cfg.dialogPackages) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        val text = collectText(root)
        if (text.isBlank()) return
        val screen = c.onDialog(text)
        if (screen != null) {
            overlayController?.render(screen)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.appendLine(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                sb.append(collectText(child))
                child.recycle()
            }
        }
        return sb.toString().trim()
    }

    fun tapButton(selector: app.tapcode.config.TapcodeConfig.Selector): Boolean {
        val root = rootInActiveWindow ?: return false
        selector.viewIds.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
        }?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return selector.labels.any { label ->
            root.findAccessibilityNodeInfosByText(label).firstOrNull()?.let {
                it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                true
            } ?: false
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        controller?.shutdown()
        controller = null
        overlayController = null
        scope.cancel()
        super.onDestroy()
    }
}