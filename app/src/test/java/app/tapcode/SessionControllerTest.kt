package app.tapcode

import app.tapcode.config.TapcodeConfig
import app.tapcode.engine.Dialer
import app.tapcode.engine.ScreenKind
import app.tapcode.engine.SessionController
import app.tapcode.engine.SessionRequest
import app.tapcode.engine.SessionState
import app.tapcode.engine.UssdParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SE-07 invariant: the controller never acts outside an active session,
 * and every session reaches a terminal state.
 */
class SessionControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class FakeDialer : Dialer {
        val dials = mutableListOf<String>()
        val replies = mutableListOf<String>()
        var cancelled = false

        override fun dial(code: String, subscriptionId: Int?) {
            dials.add(code)
        }

        override fun sendReply(reply: String) {
            replies.add(reply)
        }

        override fun cancelSession() {
            cancelled = true
        }

        override suspend fun awaitDialogAdvance(): Boolean = true
    }

    private fun controller(dialer: FakeDialer): SessionController =
        SessionController(
            parser = UssdParser(TapcodeConfig(carrier = "mtn-ng", version = 1)),
            dialer = dialer,
            scope = scope,
            stepTimeoutMs = 1000
        )

    @Test
    fun `final screen ends session with result`() = runTest {
        val dialer = FakeDialer()
        val c = controller(dialer)
        c.start(SessionRequest("*310#"))
        c.onDialog("Your balance is N500.00. Thank you.")
        assertEquals(SessionState.RESULT, c.state.value)
        assertFalse(c.isActive)
    }

    @Test
    fun `menu screen transitions to MENU and reply requires active session`() = runTest {
        val dialer = FakeDialer()
        val c = controller(dialer)
        c.start(SessionRequest("*123#"))
        c.onDialog("1. Balance\n2. Buy Data")
        assertEquals(SessionState.MENU, c.state.value)
        c.send("1")
        assertEquals(listOf("1"), dialer.replies)
    }

    @Test
    fun `SE-07 ignores dialogs when no session is active`() = runTest {
        val dialer = FakeDialer()
        val c = controller(dialer)
        val screen = c.onDialog("1. Balance\n2. Buy Data")
        assertEquals(null, screen)
        assertEquals(SessionState.IDLE, c.state.value)
    }

    @Test
    fun `SE-05 cancel terminates session and dismisses dialog`() = runTest {
        val dialer = FakeDialer()
        val c = controller(dialer)
        c.start(SessionRequest("*123#"))
        c.cancel()
        assertEquals(SessionState.CANCELLED, c.state.value)
        assertTrue(dialer.cancelled)
        assertFalse(c.isActive)
    }

    @Test
    fun `SE-06 retries dial up to max on error`() = runTest {
        val dialer = FakeDialer()
        val c = SessionController(
            parser = UssdParser(TapcodeConfig(carrier = "mtn-ng", version = 1)),
            dialer = dialer,
            scope = scope,
            stepTimeoutMs = 50,
            maxRetries = 2
        )
        c.start(SessionRequest("*123#"))
        // Simulate timeout errors directly via watchdog path
        repeat(3) { c.onDialog("") }
        assertEquals(SessionState.ERROR, c.state.value)
        assertTrue(dialer.dials.size >= 3)
    }

    @Test
    fun `charge screen routes through CONFIRMING before sending (UX-03, SE-04)`() = runTest {
        val cfg = TapcodeConfig(carrier = "mtn-ng", version = 1, chargeKeywords = listOf("N500"))
        val dialer = FakeDialer()
        val c = SessionController(
            parser = UssdParser(cfg), dialer = dialer, scope = scope, stepTimeoutMs = 1000
        )
        c.start(SessionRequest("*312#"))
        c.onDialog("1. Buy 1GB for N500\n2. Cancel")
        c.send("1")
        assertEquals(SessionState.CONFIRMING, c.state.value)
        assertTrue(dialer.replies.isEmpty())
        c.send("1", confirmed = true)
        assertEquals(listOf("1"), dialer.replies)
    }

    @Test
    fun `saved path replay validates each step and stops on mismatch (UX-05)`() = runTest {
        val dialer = FakeDialer()
        val c = controller(dialer)
        c.start(
            SessionRequest(
                "*123#",
                path = listOf(SessionRequest.Step("(?i)balance", "1"))
            )
        )
        val screen = c.onDialog("1. Balance\n2. Buy Data")
        assertEquals(ScreenKind.MENU, screen?.kind)
        assertEquals(listOf("1"), dialer.replies)
        // Mismatch: next screen does not expect "data" pattern
        c.onDialog("1. Something else entirely")
        assertEquals(SessionState.ERROR, c.state.value)
        assertFalse(c.isActive)
    }
}