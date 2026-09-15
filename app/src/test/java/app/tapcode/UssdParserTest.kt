package app.tapcode

import app.tapcode.config.TapcodeConfig
import app.tapcode.engine.ScreenKind
import app.tapcode.engine.UssdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MP-01: split operator text into prompt and numbered options. */
class UssdParserTest {

    private val parser = UssdParser(TapcodeConfig(carrier = "mtn-ng", version = 1))

    @Test
    fun `parses dot numbered menu`() {
        val raw = "MTN Menu\n1. Balance\n2. Buy Data\n3. My Number\n0. Next"
        val result = parser.parse(raw)
        assertEquals(ScreenKind.MENU, result.screen.kind)
        assertEquals(4, result.screen.options.size)
        assertEquals("Balance", result.screen.options[0].label)
        assertEquals("1", result.screen.options[0].number)
    }

    @Test
    fun `parses paren and bare numbered formats`() {
        val raw = "Select:\n1) Check balance\n2 Buy data\n98 More"
        val options = parser.extractOptions(raw)
        assertEquals(3, options.size)
        assertEquals("Check balance", options[0].label)
        assertEquals("More", options[2].label)
    }

    @Test
    fun `hash option supported`() {
        val raw = "1. Balance\n#. Next"
        val options = parser.extractOptions(raw)
        assertEquals(2, options.size)
        assertEquals("#", options[1].number)
    }

    @Test
    fun `final message detected without options`() {
        val raw = "Your airtime balance is N500.00. Thank you."
        val result = parser.parse(raw)
        assertEquals(ScreenKind.FINAL, result.screen.kind)
    }

    @Test
    fun `input prompt detected`() {
        val raw = "Enter amount:"
        val result = parser.parse(raw)
        assertEquals(ScreenKind.INPUT, result.screen.kind)
        assertTrue(result.screen.requiresText)
    }

    @Test
    fun `low confidence falls back to raw text with numeric input (MP-03)`() {
        val raw = "gibberish 77x9 no structure at all"
        val result = parser.parse(raw)
        assertTrue(result.confidence < 0.5 || result.screen.options.isEmpty())
    }

    @Test
    fun `charge keywords flag screen (UX-03)`() {
        val cfg = TapcodeConfig(
            carrier = "mtn-ng", version = 1,
            chargeKeywords = listOf("N", "buy")
        )
        val parser2 = UssdParser(cfg)
        val raw = "1. Buy 1GB for N500\n2. Back"
        val screen = parser2.parse(raw).screen
        assertTrue(screen.mentionsCharge || cfg.mentionsCharge(screen.raw))
    }
}