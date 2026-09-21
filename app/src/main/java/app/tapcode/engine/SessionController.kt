package app.tapcode.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SessionController(
    private val parser: UssdParser,
    private val dialer: Dialer,
    private val scope: CoroutineScope,
    private val stepTimeoutMs: Long = 30_000,
    private val maxRetries: Int = 2
) {
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events

    private var request: SessionRequest? = null
    private var stepIndex = 0
    private var retries = 0
    private var active = false
    private var watchdog: Job? = null

    val isActive: Boolean get() = active

    fun start(request: SessionRequest) {
        check(!active) { "SE-07: session already active" }
        this.request = request
        stepIndex = 0
        retries = 0
        active = true
        dial()
    }

    fun onDialog(text: String): UssdScreen? {
        if (!active) return null
        watchdog?.cancel()
        val parsed = parser.parse(text)
        if (parsed.confidence < 0.5) {
            return parsed.copy(
                screen = parsed.screen.copy(kind = ScreenKind.INPUT, requiresText = true)
            ).screen
        }
        when (parsed.screen.kind) {
            ScreenKind.MENU -> {
                transition(SessionState.MENU, parsed.screen)
                if (maybeAutoReplay(parsed.screen)) {
                    // UX: when the saved path can answer the operator's menu,
                    // never surface it — show progress until the next dialog.
                    return UssdScreen(
                        raw = parsed.screen.raw,
                        kind = ScreenKind.PROGRESS,
                        prompt = "",
                        confidence = parsed.confidence
                    )
                }
            }
            ScreenKind.INPUT -> transition(SessionState.MENU, parsed.screen)
            ScreenKind.PROGRESS -> armWatchdog()
            ScreenKind.FINAL -> finish(parsed.screen)
        }
        return parsed.screen
    }

    fun send(reply: String, confirmed: Boolean = false) {
        if (!active) return
        val screen = lastScreen ?: return
        if (screen.mentionsCharge && !confirmed) {
            transition(SessionState.CONFIRMING, screen)
            return
        }
        dialer.sendReply(reply)
        transition(SessionState.WAITING)
        armWatchdog()
    }

    fun cancel() {
        if (!active) return
        dialer.cancelSession()
        active = false
        watchdog?.cancel()
        transition(SessionState.CANCELLED)
    }

    private fun dial() {
        val req = request ?: return
        transition(SessionState.DIALLING)
        dialer.dial(req.code, req.subscriptionId)
        transition(SessionState.WAITING)
        armWatchdog()
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            val advanced = withTimeoutOrNull(stepTimeoutMs) { dialer.awaitDialogAdvance() }
            if (advanced != true) onTimeout()
        }
    }

    /** SE-06: timeouts redial and replay the path, at most maxRetries times. */
    fun onTimeout() {
        if (!active) return
        watchdog?.cancel()
        if (retries < maxRetries) {
            retries++
            dial()
        } else {
            active = false
            transition(SessionState.ERROR, message = "timeout")
        }
    }

    private fun onError(message: String) {
        if (!active) return
        if (retries < maxRetries) {
            retries++
            dial()
        } else {
            active = false
            transition(SessionState.ERROR, message = message)
        }
    }

    private fun finish(screen: UssdScreen) {
        active = false
        dialer.cancelSession()
        transition(SessionState.RESULT, screen)
    }

    private fun maybeAutoReplay(screen: UssdScreen): Boolean {
        val path = request?.path ?: return false
        if (stepIndex >= path.size) return false
        val step = path[stepIndex]
        if (!screen.raw.contains(Regex(step.expect, RegexOption.IGNORE_CASE))) {
            // PRD 6.3: stop on mismatch, show the live menu, flag for update
            active = false
            transition(SessionState.ERROR, screen, message = "path mismatch at step $stepIndex")
            return false
        }
        stepIndex++
        transition(SessionState.REPLAYING, screen)
        dialer.sendReply(step.reply)
        transition(SessionState.WAITING)
        armWatchdog()
        return true
    }

    private var lastScreen: UssdScreen? = null

    private fun transition(to: SessionState, screen: UssdScreen? = null, message: String? = null) {
        if (screen != null) lastScreen = screen
        val from = _state.value
        _state.value = to
        _events.tryEmit(SessionEvent(from, to, screen, message))
    }

    fun shutdown() {
        watchdog?.cancel()
        active = false
    }
}

interface Dialer {
    fun dial(code: String, subscriptionId: Int?)
    fun sendReply(reply: String)
    fun cancelSession()
    suspend fun awaitDialogAdvance(): Boolean
}